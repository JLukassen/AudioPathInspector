package com.yooshastan.audiopath.data

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** Describes a value's provenance. No unsupported data is presented as verified. */
enum class Evidence { REPORTED, CONFIGURED, OBSERVED, UNKNOWN }

data class AudioFact(
    val label: String,
    val value: String,
    val evidence: Evidence,
    val source: String
)

data class DeviceFacts(
    val id: Int,
    val name: String,
    val type: String,
    val direction: String,
    val address: String,
    val sampleRates: String,
    val channelCounts: String,
    val encodings: String,
    val audioProfiles: String
)

data class Snapshot(
    val collectedAt: Long,
    val model: String,
    val osVersion: String,
    val devices: List<DeviceFacts>,
    val facts: List<AudioFact>
) {
    fun asJson(): String = JSONObject().apply {
        put("schema", 1)
        put("note", "Read-only diagnostics. Device capabilities do not prove active routing or bit-perfect output.")
        put("collectedAt", DateFormat.getDateTimeInstance().format(Date(collectedAt)))
        put("deviceModel", model)
        put("androidVersion", osVersion)
        put("facts", JSONArray().apply {
            facts.forEach { f -> put(JSONObject().apply {
                put("label", f.label); put("value", f.value)
                put("evidence", f.evidence.name); put("source", f.source)
            }) }
        })
        put("devices", JSONArray().apply {
            devices.forEach { d -> put(JSONObject().apply {
                put("id", d.id); put("name", d.name); put("type", d.type)
                put("direction", d.direction); put("address", d.address)
                put("sampleRates", d.sampleRates); put("channelCounts", d.channelCounts)
                put("encodings", d.encodings); put("audioProfiles", d.audioProfiles)
            }) }
        })
    }.toString(2)
}

class AudioInspector(context: Context) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun snapshot(): Snapshot {
        val devices = manager.getDevices(AudioManager.GET_DEVICES_ALL).map { d ->
            DeviceFacts(
                id = d.id,
                name = d.productName.toString(),
                type = typeName(d.type),
                direction = when {
                    d.isSink && d.isSource -> "Input + output"
                    d.isSink -> "Output"
                    d.isSource -> "Input"
                    else -> "Unknown"
                },
                address = d.address.ifBlank { "Not reported" },
                sampleRates = numbers(d.sampleRates, "Hz"),
                channelCounts = numbers(d.channelCounts, "channels"),
                encodings = if (d.encodings.isEmpty()) "Unspecified / potentially arbitrary" else
                    d.encodings.joinToString(", ") { encodingName(it) },
                audioProfiles = if (Build.VERSION.SDK_INT >= 31) {
                    if (d.audioProfiles.isEmpty()) "No profiles reported"
                    else d.audioProfiles.joinToString("; ") { profile ->
                        "${encodingName(profile.format)}: ${numbers(profile.sampleRates, "Hz")}"
                    }
                } else "Requires Android 12+"
            )
        }.sortedWith(compareBy<DeviceFacts> { it.direction != "Output" }.thenBy { it.name })

        val communication = if (Build.VERSION.SDK_INT >= 31) {
            manager.communicationDevice?.let { "${it.productName} (#${it.id})" }
                ?: "Not selected / not reported"
        } else "Unavailable on this Android version"

        val facts = listOf(
            AudioFact("Framework output sample rate",
                manager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "Not reported",
                Evidence.CONFIGURED, "AudioManager property (not DAC output measurement)"),
            AudioFact("Framework output buffer size",
                manager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.let { "$it frames" } ?: "Not reported",
                Evidence.CONFIGURED, "AudioManager property"),
            AudioFact("Audio mode", modeName(manager.mode), Evidence.CONFIGURED, "AudioManager.mode"),
            AudioFact("Communication device", communication, Evidence.OBSERVED,
                "AudioManager.getCommunicationDevice (calls only; not media route)"),
            AudioFact("Current third-party media output", "Not observable using public APIs",
                Evidence.UNKNOWN, "Requires a device-specific diagnostics source"),
            AudioFact("End-to-end DAC output sample rate", "Not measured",
                Evidence.UNKNOWN, "Hardware measurement unavailable"),
            AudioFact("Bit-perfect status", "Not verified",
                Evidence.UNKNOWN, "No trustworthy direct public API")
        )
        return Snapshot(System.currentTimeMillis(), Build.MODEL, "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", devices, facts)
    }

    private fun numbers(values: IntArray, suffix: String): String =
        if (values.isEmpty()) "Unspecified / potentially arbitrary"
        else values.sorted().distinct().joinToString(", ") { "$it $suffix" }

    private fun modeName(mode: Int): String = when (mode) {
        AudioManager.MODE_NORMAL -> "Normal"
        AudioManager.MODE_RINGTONE -> "Ringtone"
        AudioManager.MODE_IN_CALL -> "In call"
        AudioManager.MODE_IN_COMMUNICATION -> "Communication"
        else -> "Other ($mode)"
    }

    private fun encodingName(value: Int): String = when(value) {
        AudioFormat.ENCODING_PCM_8BIT -> "PCM 8-bit"
        AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "PCM float"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24-bit packed"
        AudioFormat.ENCODING_PCM_32BIT -> "PCM 32-bit"
        AudioFormat.ENCODING_AC3 -> "AC-3"
        AudioFormat.ENCODING_E_AC3 -> "E-AC-3"
        AudioFormat.ENCODING_DTS -> "DTS"
        AudioFormat.ENCODING_IEC61937 -> "IEC 61937"
        else -> "Encoding #$value"
    }

    private fun typeName(value: Int): String = when (value) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in earpiece"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "3.5mm headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Analog line"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Digital line"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio device"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB audio accessory"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset / DAC"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
        AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in microphone"
        AudioDeviceInfo.TYPE_FM_TUNER -> "FM tuner"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "Remote submix"
        AudioDeviceInfo.TYPE_BUS -> "Audio bus"
        AudioDeviceInfo.TYPE_IP -> "Network audio"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE speaker"
        else -> "Android type #$value"
    }
}
