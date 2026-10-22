package com.yooshastan.audiopath.data

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Optional, explicitly user-approved read-only root collector. Works with root managers
 * exposing `su -c` to authorized apps (including supported APatch configurations).
 * Never runs shell text from UI input, never performs writes or changes SELinux settings.
 */
object RootTinymixCollector {
    private const val MAX_OUTPUT_BYTES = 96_000
    private const val TIMEOUT_SECONDS = 25L // allows time for the root manager consent prompt

    // Fixed script; no values, card IDs or paths are interpolated from user/device data.
    private val ROOT_SCRIPT = """
uid=${'$'}(/system/bin/id -u 2>/dev/null)
if [ "${'$'}uid" != "0" ]; then
  echo '__API_STATUS__:ROOT_DENIED'
  exit 41
fi
bin=''
for candidate in /system/bin/tinymix /vendor/bin/tinymix /system/vendor/bin/tinymix /system/xbin/tinymix; do
  if [ -x "${'$'}candidate" ]; then bin="${'$'}candidate"; break; fi
done
if [ -z "${'$'}bin" ]; then
  echo '__API_STATUS__:MISSING_BINARY'
  exit 42
fi
printf 'Executable: %s\n' "${'$'}bin"
count=0
for card in 0 1 2 3; do
  printf '\n--- CARD %s ---\n' "${'$'}card"
  "${'$'}bin" -D "${'$'}card"
  rc=${'$'}?
  if [ "${'$'}rc" -ne 0 ]; then
    echo '[Legacy read-only listing failed; trying contents syntax]'
    "${'$'}bin" -D "${'$'}card" contents
    rc=${'$'}?
  fi
  if [ "${'$'}rc" -eq 0 ]; then
    printf '__API_CARD_OK__:%s\n' "${'$'}card"
    count=${'$'}((count + 1))
  else
    printf '[Card %s unavailable (exit %s)]\n' "${'$'}card" "${'$'}rc"
  fi
done
if [ "${'$'}count" -eq 0 ]; then
  echo '__API_STATUS__:NO_MIXER'
  exit 43
fi
""".trimIndent()

    fun collect(): String {
        val result = try {
            val process = ProcessBuilder("su", "-c", ROOT_SCRIPT).redirectErrorStream(true).start()
            val bytes = ByteArrayOutputStream()
            var truncated = false
            val pump = thread(name = "root-tinymix-output", isDaemon = true) {
                process.inputStream.use { input ->
                    val buffer = ByteArray(2048)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        synchronized(bytes) {
                            val remaining = MAX_OUTPUT_BYTES - bytes.size()
                            if (remaining > 0) bytes.write(buffer, 0, minOf(remaining, count))
                            if (count > remaining) truncated = true
                        }
                    }
                }
            }
            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            pump.join(1_500)
            val output = synchronized(bytes) { bytes.toString(Charsets.UTF_8.name()) }
            val suffix = if (truncated) "\n[Output truncated at $MAX_OUTPUT_BYTES bytes]" else ""
            val status = when {
                !finished -> "TIMEOUT"
                output.contains("__API_STATUS__:ROOT_DENIED") -> "ROOT_DENIED"
                output.contains("__API_STATUS__:MISSING_BINARY") -> "NOT_FOUND"
                output.contains("__API_STATUS__:NO_MIXER") -> "NO_READABLE_MIXERS"
                process.exitValue() != 0 -> "ERROR (exit ${process.exitValue()})"
                output.contains("__API_CARD_OK__:") -> "OK"
                else -> "EMPTY"
            }
            status to (output + suffix)
        } catch (exception: Exception) {
            "ERROR" to "Root invocation failed: ${exception.javaClass.simpleName}: ${exception.message}"
        }
        return buildString {
            appendLine("=== TINYMIX: Root mixer controls ===")
            appendLine("Status: ${result.first}")
            appendLine("Source: local su -c; only read-only Tinymix enumeration (cards 0–3)")
            appendLine("ROOT_DENIED means the root manager refused access or uid was not 0; NOT_FOUND means no system/vendor Tinymix binary.")
            appendLine("A root grant can expose sensitive mixer names. Review diagnostics before sharing.")
            appendLine(result.second)
        }
    }
}
