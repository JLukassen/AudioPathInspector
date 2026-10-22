package com.yooshastan.audiopath.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.yooshastan.audiopath.IAudioDiagnostics
import rikka.shizuku.Shizuku
import java.lang.Exception
import kotlin.concurrent.thread

sealed class AccessState(val label: String) {
    data object Offline : AccessState("Shizuku unavailable")
    data object PermissionRequired : AccessState("Shizuku permission required")
    data class Ready(val uid: Int) : AccessState(if (uid == 0) "Shizuku root" else "Shizuku ADB shell")
}

class ShizukuBridge(
    private val context: Context,
    private val onAccessChange: (AccessState) -> Unit,
    private val onDump: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val permissionRequestCode = 4102
    private val receivedListener = Shizuku.OnBinderReceivedListener { emitAccess() }
    private val deadListener = Shizuku.OnBinderDeadListener {
        onAccessChange(AccessState.Offline)
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == permissionRequestCode) emitAccess()
    }
    private var connection: ServiceConnection? = null

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(receivedListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        emitAccess()
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(receivedListener)
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        connection = null
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) { onError("Start Shizuku before requesting access."); return }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) emitAccess()
            else if (Shizuku.shouldShowRequestPermissionRationale()) {
                onError("Permission denied. Allow Audio Path Inspector in the Shizuku app.")
            } else Shizuku.requestPermission(permissionRequestCode)
        } catch (e: Exception) { onError("Shizuku request failed: ${e.message}") }
    }

    fun collect() {
        if (!Shizuku.pingBinder()) { onError("Shizuku is not running."); return }
        if (runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED)
            != PackageManager.PERMISSION_GRANTED) { onError("Grant Shizuku permission first."); return }
        if (connection != null) { onError("Collection already started."); return }
        val component = ComponentName(context.packageName, PrivilegedAudioService::class.java.name)
        val args = Shizuku.UserServiceArgs(component)
            .daemon(false)
            .processNameSuffix("audio_diag")
            .tag("read_only_audio_diag")
            .version(2)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service == null) {
                    connection = null
                    onError("Shizuku returned no service binder.")
                    return
                }
                thread(name = "shizuku-audio-collect", isDaemon = true) {
                    try {
                        val dump = IAudioDiagnostics.Stub.asInterface(service).collectSnapshot()
                        android.os.Handler(android.os.Looper.getMainLooper()).post { onDump(dump) }
                    } catch (e: Exception) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onError("Audio diagnostics failed: ${e.message}")
                        }
                    } finally {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            connection = null
                            runCatching { Shizuku.unbindUserService(args, this, true) }
                        }
                    }
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                connection = null
                onError("Shizuku service disconnected.")
            }
        }
        connection = conn
        try { Shizuku.bindUserService(args, conn) }
        catch (e: Exception) {
            connection = null
            onError("Couldn't bind Shizuku UserService: ${e.message}")
        }
    }

    private fun emitAccess() {
        if (!Shizuku.pingBinder()) { onAccessChange(AccessState.Offline); return }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                onAccessChange(AccessState.PermissionRequired)
            } else {
                onAccessChange(AccessState.Ready(Shizuku.getUid()))
            }
        } catch (_: Exception) { onAccessChange(AccessState.Offline) }
    }
}
