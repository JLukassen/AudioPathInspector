package com.yooshastan.audiopath

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yooshastan.audiopath.data.DiagnosticBaseline
import com.yooshastan.audiopath.data.DiagnosticBaselineStore
import com.yooshastan.audiopath.data.HardwareInventory
import com.yooshastan.audiopath.data.HardwareReport
import com.yooshastan.audiopath.data.RootTinymixCollector
import com.yooshastan.audiopath.data.AudioInspector
import com.yooshastan.audiopath.data.Snapshot
import com.yooshastan.audiopath.data.TestTone
import com.yooshastan.audiopath.shizuku.AccessState
import com.yooshastan.audiopath.shizuku.ShizukuBridge
import com.yooshastan.audiopath.ui.InspectorScreen
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var inspector: AudioInspector
    private lateinit var manager: AudioManager
    private lateinit var shizuku: ShizukuBridge
    private lateinit var baselineStore: DiagnosticBaselineStore
    private var baseline by mutableStateOf<DiagnosticBaseline?>(null)
    private var snapshot by mutableStateOf<Snapshot?>(null)
    private var access by mutableStateOf<AccessState>(AccessState.Offline)
    private var rawDump by mutableStateOf("")
    private var rootDump by mutableStateOf("")
    private var rootScanning by mutableStateOf(false)
    private var toneResult by mutableStateOf("No test tone played")
    private var status by mutableStateOf("")
    private var collecting by mutableStateOf(false)
    private var playing by mutableStateOf(false)

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inspector = AudioInspector(this)
        baselineStore = DiagnosticBaselineStore(this)
        baseline = baselineStore.load()
        manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        snapshot = inspector.snapshot()
        shizuku = ShizukuBridge(
            context = this,
            onAccessChange = { access = it },
            onDump = { dump -> rawDump = dump; collecting = false; status = "Privileged report collected" },
            onError = { message -> collecting = false; status = message }
        )
        shizuku.start()
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        setContent {
            InspectorScreen(
                snapshot = snapshot,
                access = access,
                toneResult = toneResult,
                rawDump = combinedReport(),
                rootReport = rootDump,
                rootScanning = rootScanning,
                baseline = baseline,
                status = status,
                collecting = collecting,
                playing = playing,
                onRefresh = { refresh() },
                onPermission = { shizuku.requestPermission() },
                onCollect = { rootDump = ""; collecting = true; status = "Starting Shizuku collector…"; shizuku.collect() },
                onRootCollect = { scanRootTinymix() },
                onPlayTone = { playTone() },
                onSharePublic = { share("Audio Path Inspector report", snapshot?.asJson() ?: "No data") },
                onShareRaw = { if (combinedReport().isNotBlank()) share("Audio Path Inspector privileged report", combinedReport()) },
                onSaveBaseline = { saveBaseline() },
                onClearBaseline = { clearBaseline() }
            )
        }
    }

    private fun saveBaseline() {
        val entries = HardwareInventory.collect(HardwareReport.parse(combinedReport()))
        if (combinedReport().isBlank() || entries.isEmpty()) { status = "Collect a Shizuku hardware report first"; return }
        runCatching { baselineStore.save(snapshot?.model ?: "Unknown device", entries) }
            .onSuccess { baseline = it; status = "Saved local hardware baseline" }
            .onFailure { status = "Baseline save failed: ${it.message}" }
    }

    private fun clearBaseline() {
        runCatching { baselineStore.clear() }
            .onSuccess { baseline = null; status = "Local baseline cleared" }
            .onFailure { status = "Could not clear baseline: ${it.message}" }
    }

    /** A root grant is never requested until the user confirms the dialog in Hardware. */
    private fun scanRootTinymix() {
        if (rootScanning || collecting) return
        rootScanning = true
        status = "Requesting read-only root access from your root manager…"
        thread(name = "tinymix-root-scan", isDaemon = true) {
            val result = runCatching { RootTinymixCollector.collect() }
                .getOrElse { "=== TINYMIX: Root mixer controls ===\nStatus: ERROR\n${it.message}" }
            runOnUiThread {
                rootDump = result
                rootScanning = false
                val state = HardwareReport.parse(result).firstOrNull()?.status ?: "UNKNOWN"
                status = "Root Tinymix scan: $state"
            }
        }
    }

    private fun combinedReport(): String = listOf(rawDump, rootDump)
        .filter { it.isNotBlank() }.joinToString("\n\n")

    private fun refresh() {
        snapshot = inspector.snapshot()
    }

    private fun playTone() {
        if (playing) return
        playing = true
        toneResult = "Playing a brief, low-volume diagnostic tone…"
        thread(name = "test-tone", isDaemon = true) {
            val result = runCatching { TestTone.playAndInspect() }
                .getOrElse { "Test tone failed: ${it.message}" }
            runOnUiThread { toneResult = result; playing = false; refresh() }
        }
    }

    private fun share(title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share diagnostic report"))
    }

    override fun onDestroy() {
        manager.unregisterAudioDeviceCallback(callback)
        shizuku.stop()
        super.onDestroy()
    }
}
