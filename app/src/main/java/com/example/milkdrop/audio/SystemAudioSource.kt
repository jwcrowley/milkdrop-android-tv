package com.example.milkdrop.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures system audio playback using [AudioPlaybackCaptureConfiguration] (API 29+).
 *
 * Requires a valid [MediaProjection] token obtained via the MediaProjection consent flow.
 * @param mediaProjection A valid [MediaProjection] instance from the consent flow.
 * @param onFallback      Called when the MediaProjection is stopped; the caller should
 *                        select a safe fallback source and update the UI.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class SystemAudioSource(
    private val mediaProjection: MediaProjection,
    private val onFallback: () -> Unit
) : AudioSource {

    private val TAG = "SystemAudioSource"

    override val sampleRate: Int = 44100
    override val channelCount: Int = 2  // Stereo system audio

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private val running = AtomicBoolean(false)

    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_STEREO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096) * 2

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.w(TAG, "MediaProjection stopped — falling back to microphone")
            stop()
            onFallback()
        }
    }

    @SuppressLint("MissingPermission")
    override fun start(callback: (AudioFrame) -> Unit) {
        if (running.getAndSet(true)) return

        mediaProjection.registerCallback(projectionCallback, null)

        val record = try {
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()

            AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(captureConfig)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .build()
        } catch (e: RuntimeException) {
            Log.e(TAG, "System audio capture creation failed", e)
            running.set(false)
            mediaProjection.unregisterCallback(projectionCallback)
            onFallback()
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord (system audio) failed to initialize")
            running.set(false)
            mediaProjection.unregisterCallback(projectionCallback)
            onFallback()
            return
        }

        audioRecord = record
        try {
            record.startRecording()
        } catch (e: RuntimeException) {
            Log.e(TAG, "System audio capture failed to start", e)
            audioRecord = null
            record.release()
            running.set(false)
            mediaProjection.unregisterCallback(projectionCallback)
            onFallback()
            return
        }

        captureThread = Thread({
            val buffer = ShortArray(bufferSize / 2)
            while (running.get()) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    callback(
                        AudioFrame(
                            pcmData = buffer.copyOf(read),
                            sampleRate = sampleRate,
                            channelCount = channelCount,
                            timestampNanos = System.nanoTime()
                        )
                    )
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord.read() error: $read")
                }
            }
        }, "MilkDrop-SystemAudio").also { it.isDaemon = true }

        captureThread?.start()
        Log.i(TAG, "Started system audio capture at ${sampleRate}Hz stereo")
    }

    override fun stop() {
        running.set(false)
        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "System AudioRecord stop failed", e)
            }
            Unit
        }
        captureThread?.join(500)
        captureThread = null
        audioRecord?.apply {
            release()
        }
        audioRecord = null
        try {
            mediaProjection.unregisterCallback(projectionCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister MediaProjection callback: ${e.message}")
        }
        Log.i(TAG, "Stopped system audio capture")
    }
}
