package com.yooshastan.audiopath.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** A low-level, user-triggered diagnostic stream. Never starts automatically. */
object TestTone {
    fun playAndInspect(): String {
        val rate = 48_000
        val frames = 19_200 // 400 ms
        val samples = ByteArray(frames * 4) // 16-bit stereo
        for (i in 0 until frames) {
            val value = (sin(2.0 * PI * 750.0 * i / rate) * 520.0).toInt().toShort()
            val offset = i * 4
            for (c in 0..1) {
                samples[offset + 2 * c] = (value.toInt() and 0xff).toByte()
                samples[offset + 2 * c + 1] = ((value.toInt() shr 8) and 0xff).toByte()
            }
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(samples.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            if (track.state != AudioTrack.STATE_INITIALIZED) return "AudioTrack couldn't initialize."
            val written = track.write(samples, 0, samples.size)
            if (written != samples.size) return "AudioTrack wrote $written of ${samples.size} bytes."
            track.play()
            Thread.sleep(140) // allow routing to initialize
            val route = track.routedDevice
            Thread.sleep(310)
            return if (route != null)
                "Own stream: 48,000 Hz • PCM 16-bit stereo. Routed to ${route.productName} (ID ${route.id}). This does not verify the DAC hardware rate."
            else "Own stream: 48,000 Hz • PCM 16-bit stereo. Routing device wasn't reported while playing."
        } finally {
            try { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() } catch (_: Exception) { }
            track.release()
        }
    }
}
