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
        bufferSize = minBuf * 2  // Double buffer for smoother reads
    }

    override fun start(callback: (AudioFrame) -> Unit) {
        if (running.getAndSet(true)) return  // Already running

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            running.set(false)
            return
        }

        audioRecord = record
        record.startRecording()

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
        captureThread?.join(500)
        captureThread = null
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        Log.i(TAG, "Stopped microphone capture")
    }
}
