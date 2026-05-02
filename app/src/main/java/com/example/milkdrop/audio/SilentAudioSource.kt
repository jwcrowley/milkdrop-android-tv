package com.example.milkdrop.audio

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Emits zero-amplitude [AudioFrame] objects at approximately 30 Hz.
 *
 * Used as a fallback when:
 * - The RECORD_AUDIO permission is denied
 * - No audio has been detected for 5 consecutive seconds
 * - The user explicitly selects "Silent" as the audio source
 */
class SilentAudioSource : AudioSource {

    override val sampleRate: Int = 44100
    override val channelCount: Int = 1

    private val running = AtomicBoolean(false)
    private var emitThread: Thread? = null

    // Buffer size for ~33ms of audio at 44100Hz mono (one frame at 30fps)
    private val samplesPerFrame = sampleRate / 30
    private val silentBuffer = ShortArray(samplesPerFrame)  // All zeros

    override fun start(callback: (AudioFrame) -> Unit) {
        if (running.getAndSet(true)) return

        emitThread = Thread({
            while (running.get()) {
                callback(
                    AudioFrame(
                        pcmData = silentBuffer.copyOf(),
                        sampleRate = sampleRate,
                        channelCount = channelCount,
                        timestampNanos = System.nanoTime()
                    )
                )
                try {
                    Thread.sleep(33)  // ~30 Hz
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }, "MilkDrop-SilentAudio").also { it.isDaemon = true }

        emitThread?.start()
    }

    override fun stop() {
        running.set(false)
        emitThread?.interrupt()
        emitThread?.join(200)
        emitThread = null
    }
}
