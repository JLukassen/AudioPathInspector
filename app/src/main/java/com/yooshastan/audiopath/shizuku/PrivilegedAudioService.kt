package com.yooshastan.audiopath.shizuku

import android.os.Process
import com.yooshastan.audiopath.IAudioDiagnostics
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Read-only Shizuku UserService. This collector uses fixed commands and known paths;
 * it never executes user-supplied shell text or writes to an ALSA mixer.
 *
 * SELinux applies even when Shizuku is enabled. UID 2000 is not UID 0.
 */
class PrivilegedAudioService : IAudioDiagnostics.Stub() {
    override fun collectSnapshot(): String = buildString {
        appendLine("Audio Path Inspector — hardware diagnostics 0.3")
        appendLine("Executing UID: ${Process.myUid()} (2000 = ADB shell; 0 = root)")
        appendLine("Snapshot is read-only. A HAL/config file does not prove a live DSP or DAC setting.")
        appendLine("Review raw data before sharing: vendor paths and service logs may identify your device.")
        appendLine()

        addSection("FRAMEWORK: AudioFlinger", command(listOf("/system/bin/dumpsys", "media.audio_flinger"), 100_000))
        addSection("FRAMEWORK: Audio Policy", command(listOf("/system/bin/dumpsys", "media.audio_policy"), 100_000))

        addSection("ALSA: Cards", readFile("/proc/asound/cards", 12_000))
        addSection("ALSA: PCM devices", readFile("/proc/asound/pcm", 12_000))
        addSection("ALSA: Active PCM parameters", alsaStreams())

        addSection("HAL: Audio services", audioServices())
        addSection("HAL: Legacy HIDL services", legacyHalServices())
        addSection("HAL: VINTF audio declarations", vintfAudioDeclarations())
        addSection("HAL: Audio properties", audioProperties())
        addSection("VENDOR: Audio policy and effects", vendorFiles("policy"))
        addSection("VENDOR: Mixer configurations", vendorFiles("mixer"))
        addSection("TINYMIX: Controls", tinymixControls())
    }

    private data class Probe(val status: String, val output: String)

    private fun StringBuilder.addSection(name: String, probe: Probe) {
        appendLine("=== $name ===")
        appendLine("Status: ${probe.status}")
        appendLine(probe.output)
        appendLine()
    }

    /** Drain command output concurrently so a large dumpsys cannot deadlock. */
    private fun command(argv: List<String>, maxBytes: Int = 60_000): Probe = try {
        val process = ProcessBuilder(argv).redirectErrorStream(true).start()
        val buffer = ByteArrayOutputStream()
        var wasTruncated = false
        val pump = thread(name = "audio-probe-drain", isDaemon = true) {
            process.inputStream.use { stream ->
                val chunk = ByteArray(4096)
                while (true) {
                    val size = stream.read(chunk)
                    if (size < 0) break
                    synchronized(buffer) {
                        val capacity = maxBytes - buffer.size()
                        if (capacity > 0) buffer.write(chunk, 0, minOf(size, capacity))
                        if (size > capacity) wasTruncated = true
                    }
                }
            }
        }
        val finished = process.waitFor(7, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        pump.join(1_500)
        val output = synchronized(buffer) { buffer.toString(Charsets.UTF_8.name()) }
        val suffix = if (wasTruncated) "\n[Output truncated to $maxBytes bytes]" else ""
        val exit = if (finished) process.exitValue() else -1
        val status = when {
            !finished -> "TIMEOUT"
            exit != 0 && output.contains("Permission denied", true) -> "PERMISSION_DENIED"
            exit != 0 -> "ERROR (exit $exit)"
            output.isBlank() -> "EMPTY"
            else -> "OK"
        }
        Probe(status, output + suffix)
    } catch (e: Exception) {
        Probe("ERROR", "${e.javaClass.simpleName}: ${e.message}")
    }

    /** Reads only a bounded amount; never writes or modifies the target file. */
    private fun readFile(path: String, maxBytes: Int): Probe {
        val file = File(path)
        if (!file.exists()) return Probe("NOT_FOUND", path)
        if (!file.isFile) return Probe("NOT_FILE", path)
        return try {
            FileInputStream(file).use { stream ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(2048)
                var remaining = maxBytes + 1
                while (remaining > 0) {
                    val n = stream.read(chunk, 0, minOf(chunk.size, remaining))
                    if (n < 0) break
                    buffer.write(chunk, 0, n)
                    remaining -= n
                }
                val contents = buffer.toByteArray()
                val truncated = contents.size > maxBytes
                val limited = String(contents, 0, minOf(maxBytes, contents.size), Charsets.UTF_8)
                Probe(if (limited.isBlank()) "EMPTY" else "OK", limited + if (truncated) "\n[Truncated]" else "")
            }
        } catch (e: SecurityException) {
            Probe("PERMISSION_DENIED", "$path: ${e.message}")
        } catch (e: IOException) {
            Probe(if (e.message?.contains("Permission denied", true) == true) "PERMISSION_DENIED" else "ERROR", "$path: ${e.message}")
        }
    }

    private fun alsaStreams(): Probe {
        val root = File("/proc/asound")
        if (!root.exists()) return Probe("NOT_FOUND", "/proc/asound is not available on this device")
        val cards = try { root.listFiles()?.filter { it.name.matches(Regex("card[0-9]+")) }?.take(8) }
            catch (e: SecurityException) { return Probe("PERMISSION_DENIED", "Cannot list /proc/asound: ${e.message}") }
            ?: return Probe("PERMISSION_DENIED", "Could not enumerate /proc/asound")
        val lines = StringBuilder()
        var inspected = 0
        var readable = 0
        var denied = 0
        for (card in cards) {
            val pcmDirs = card.listFiles()?.filter { it.name.matches(Regex("pcm[0-9]+[pc]")) }?.take(12).orEmpty()
            for (pcm in pcmDirs) {
                val subs = pcm.listFiles()?.filter { it.name.matches(Regex("sub[0-9]+")) }?.take(4).orEmpty()
                for (sub in subs) {
                    if (inspected >= 36) break
                    val probe = readFile("${sub.path}/hw_params", 2_048)
                    inspected++
                    if (probe.status == "OK") readable++
                    if (probe.status == "PERMISSION_DENIED") denied++
                    lines.appendLine("${sub.path}/hw_params: ${probe.status}")
                    if (probe.status == "OK") lines.appendLine(probe.output)
                }
            }
        }
        return when {
            inspected == 0 -> Probe("EMPTY", "No per-stream hw_params nodes enumerated. Kernel/vendor exposure varies.")
            readable == 0 && denied > 0 -> Probe("PERMISSION_DENIED", lines.toString())
            readable == 0 -> Probe("NO_READABLE_STREAMS", lines.toString())
            else -> Probe("OK", "Inspected: $inspected; readable: $readable; denied: $denied\n$lines")
        }
    }

    /** Service registration is metadata, not permission to call the HAL. */
    private fun audioServices(): Probe {
        val result = command(listOf("/system/bin/service", "list"), 55_000)
        if (result.status != "OK") return result
        val lines = result.output.lineSequence().filter { it.contains("audio", ignoreCase = true) }
            .take(45).toList()
        return if (lines.isEmpty()) Probe("EMPTY", "No audio service names visible to shell")
        else Probe("OK", lines.joinToString("\n") + "\n[Service listing does not imply vendor HAL internals are exposed]")
    }

    private fun legacyHalServices(): Probe {
        val executable = File("/system/bin/lshal")
        if (!executable.canExecute()) return Probe("NOT_FOUND", "lshal is not available on this Android build")
        val result = command(listOf(executable.path), 65_000)
        if (result.status != "OK") return result
        val audioLines = result.output.lineSequence().filter { it.contains("audio", ignoreCase = true) }
            .take(45).toList()
        return if (audioLines.isEmpty()) Probe("EMPTY", "No audio HIDL services listed")
        else Probe("OK", audioLines.joinToString("\n") + "\n[Legacy HIDL service presence does not prove active routing]")
    }

    /** VINTF manifests describe implementations, not their live signal processing state. */
    private fun vintfAudioDeclarations(): Probe {
        val roots = listOf("/vendor/etc/vintf", "/odm/etc/vintf", "/system/etc/vintf")
        val paths = mutableListOf<File>()
        for (root in roots) {
            val f = File(root, "manifest.xml")
            if (f.exists()) paths.add(f)
            val fragments = File(root, "manifest").listFiles().orEmpty()
            paths.addAll(fragments.filter { it.isFile && it.name.endsWith(".xml") }.take(30))
        }
        if (paths.isEmpty()) return Probe("NOT_FOUND", "No inspected VINTF manifests were visible")
        var ok = 0
        var denied = 0
        val result = StringBuilder()
        val matcher = Regex("audio|sound_trigger|soundtrigger", RegexOption.IGNORE_CASE)
        for (file in paths.take(36)) {
            val data = readFile(file.path, 60_000)
            if (data.status == "PERMISSION_DENIED") { denied++; result.appendLine("${file.path}: PERMISSION_DENIED"); continue }
            if (data.status != "OK") continue
            ok++
            // Keep adjacent context around audio names. Full manifest may contain unrelated identifiers.
            val lines = data.output.lines()
            val matched = lines.indices.filter { matcher.containsMatchIn(lines[it]) }.take(28)
            if (matched.isEmpty()) continue
            result.appendLine("--- ${file.path} [OK] ---")
            for (index in matched) {
                val from = maxOf(0, index - 2)
                val to = minOf(lines.lastIndex, index + 3)
                for (pos in from..to) result.appendLine(lines[pos].take(260))
                result.appendLine("…")
            }
        }
        val status = when {
            ok > 0 -> "OK"
            denied > 0 -> "PERMISSION_DENIED"
            else -> "EMPTY"
        }
        return Probe(status, result.toString().ifBlank { "No audio HAL declarations found in readable manifest fragments" })
    }

    private fun audioProperties(): Probe {
        val result = command(listOf("/system/bin/getprop"), 80_000)
        if (result.status != "OK") return result
        val names = Regex("audio|alsa|a2dp|codec|offload|mixer", RegexOption.IGNORE_CASE)
        val lines = result.output.lineSequence().filter { line ->
            val key = line.substringBefore(']')
            names.containsMatchIn(key)
        }.take(65).toList()
        return if (lines.isEmpty()) Probe("EMPTY", "No relevant properties were exposed")
        else Probe("OK", lines.joinToString("\n") + "\n[Properties are not direct hardware measurements]")
    }

    private fun vendorFiles(type: String): Probe {
        val baseDirs = listOf("/vendor/etc", "/odm/etc", "/product/etc", "/system/etc")
        val candidateNames = if (type == "policy") listOf(
            "audio_policy_configuration.xml", "audio_effects.xml", "audio_effects.conf",
            "audio_platform_info.xml", "audio_policy.conf", "audio_io_policy.conf"
        ) else listOf("mixer_paths.xml", "mixer_paths_mtp.xml", "mixer_paths_tasha.xml", "mixer_paths_wcd9340.xml")
        val candidates = linkedSetOf<String>()
        for (base in baseDirs) {
            for (name in candidateNames) candidates.add("$base/$name")
            // Include only shallow XML-like audio configs, not vendor-wide filesystem discovery.
            val extraDirs = listOf(File(base), File("$base/audio"))
            for (dir in extraDirs) {
                val files = runCatching { dir.listFiles() }.getOrNull().orEmpty()
                for (file in files.take(150)) {
                    if (file.isFile && file.name.endsWith(".xml", true) &&
                        Regex("audio|mixer|codec|policy|effect", RegexOption.IGNORE_CASE).containsMatchIn(file.name)) {
                        candidates.add(file.path)
                    }
                    if (candidates.size >= 50) break
                }
                if (candidates.size >= 50) break
            }
            if (candidates.size >= 50) break
        }
        val visible = candidates.filter { File(it).exists() }.take(10)
        if (visible.isEmpty()) return Probe("NOT_FOUND", "No matching $type configuration files found at inspected paths. Other vendor paths may exist.")
        val result = StringBuilder()
        var ok = 0
        var denied = 0
        for (path in visible) {
            val probe = readFile(path, if (type == "policy") 12_000 else 8_000)
            if (probe.status == "OK") ok++
            if (probe.status == "PERMISSION_DENIED") denied++
            result.appendLine("--- $path [${probe.status}] ---")
            result.appendLine(probe.output)
        }
        return Probe(if (ok > 0) "OK" else if (denied > 0) "PERMISSION_DENIED" else "NO_READABLE_FILES", result.toString())
    }

    private fun tinymixControls(): Probe {
        if (Process.myUid() != 0) return Probe("ROOT_REQUIRED", "Tinymix control enumeration is root-only. Shizuku ADB shell cannot bypass vendor ALSA restrictions.")
        val candidates = listOf("/system/bin/tinymix", "/vendor/bin/tinymix", "/system/vendor/bin/tinymix")
        val executable = candidates.firstOrNull { File(it).canExecute() }
            ?: return Probe("NOT_FOUND", "No executable tinymix was found in the inspected system/vendor paths")
        val probe = command(listOf(executable), 40_000)
        return if (probe.status == "OK") Probe("OK", "Binary: $executable\n${probe.output}") else probe
    }

    override fun destroy() { exitProcess(0) }
}
