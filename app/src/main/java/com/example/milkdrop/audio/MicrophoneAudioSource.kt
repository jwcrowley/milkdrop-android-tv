package com.example.milkdrop.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures PCM audio from the device microphone using [AudioRecord].
 *
 * Runs a dedicated capture thread that reads PCM buffers and posts them
 * to the provided callback. The callback is invoked on the capture thread —
 * callers should enqueue frames into an [AudioFrameQueue] rather than doing
 * heavy work in the callback.
 */
class MicrophoneAudioSource : AudioSource {

    private val TAG = "MicrophoneAudioSource"

    override val channelCount: Int = 1  // Mono capture
    override val sampleRate: Int

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private val running = AtomicBoolean(false)
    private val bufferSize: Int

    init {
        // Prefer 44100 Hz; fall back to 48000 Hz if unsupported
        val preferredRate = 44100
        val fallbackRate = 48000
        val minBuf44 = AudioRecord.getMinBufferSize(
            preferredRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        sampleRate = if (minBuf44 > 0) preferredRate else fallbackRate
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // Some TV devices report an error for every queried rate. Keep the
        // constructor valid so start() can fail cleanly instead of crashing.
        bufferSize = if (minBuf > 0) minBuf * 2 else 4096
    }

    override fun start(callback: (AudioFrame) -> Unit) {
        if (running.getAndSet(true)) return  // Already running

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission was revoked", e)
            running.set(false)
            return
        } catch (e: RuntimeException) {
            Log.e(TAG, "AudioRecord creation failed", e)
            running.set(false)
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            running.set(false)
            return
        }

        audioRecord = record
        try {
            record.startRecording()
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission was revoked", e)
            audioRecord = null
            record.release()
            running.set(false)
            return
        } catch (e: RuntimeException) {
            Log.e(TAG, "AudioRecord failed to start", e)
            audioRecord = null
            record.release()
            running.set(false)
            return
        }

        captureThread = Thread({
            val buffer = ShortArray(bufferSize / 2)  // bufferSize is in bytes; ShortArray is 2 bytes/element
            while (running.get()) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val frame = AudioFrame(
                        pcmData = buffer.copyOf(read),
                        sampleRate = sampleRate,
                        channelCount = channelCount,
                        timestampNanos = System.nanoTime()
                    )
                    callback(frame)
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord.read() error: $read")
                }
            }
        }, "MilkDrop-AudioCapture").also { it.isDaemon = true }

        captureThread?.start()
        Log.i(TAG, "Started microphone capture at ${sampleRate}Hz, bufferSize=$bufferSize")
    }

    override fun stop() {
        running.set(false)
        // Stop first to unblock a pending read. Joining before stop can leave the
        // capture thread using AudioRecord while another thread releases it.
        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "AudioRecord stop failed", e)
            }
            Unit
        }
        captureThread?.join(500)
        captureThread = null
        audioRecord?.apply {
            release()
        }
        audioRecord = null
        Log.i(TAG, "Stopped microphone capture")
    }

    fun isRunning(): Boolean = running.get()
}
