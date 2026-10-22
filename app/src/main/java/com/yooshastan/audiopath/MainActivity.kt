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
    private var snapshot by mutableStateOf<Snapshot?>(null)
    private var access by mutableStateOf<AccessState>(AccessState.Offline)
    private var rawDump by mutableStateOf("")
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
                rawDump = rawDump,
                status = status,
                collecting = collecting,
                playing = playing,
                onRefresh = { refresh() },
                onPermission = { shizuku.requestPermission() },
                onCollect = { collecting = true; status = "Starting Shizuku collector…"; shizuku.collect() },
                onPlayTone = { playTone() },
                onSharePublic = { share("Audio Path Inspector report", snapshot?.asJson() ?: "No data") },
                onShareRaw = { if (rawDump.isNotBlank()) share("Audio Path Inspector privileged report", rawDump) }
            )
        }
    }

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
