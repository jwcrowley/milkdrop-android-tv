package com.example.milkdrop.audio

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates beat-like PCM for TV devices with no usable audio input.
 * This drives projectM's audio-reactive paths without microphone or casting UI.
 */
class AutoPulseAudioSource : AudioSource {

    override val sampleRate: Int = 44100
    override val channelCount: Int = 1

    private val running = AtomicBoolean(false)
    private var emitThread: Thread? = null
    private val samplesPerFrame = sampleRate / 30
    private var phase = 0.0
    private var frameIndex = 0

    override fun start(callback: (AudioFrame) -> Unit) {
        if (running.getAndSet(true)) return

        emitThread = Thread({
            while (running.get()) {
                callback(
                    AudioFrame(
                        pcmData = nextBuffer(),
                        sampleRate = sampleRate,
                        channelCount = channelCount,
                        timestampNanos = System.nanoTime()
                    )
                )
                try {
                    Thread.sleep(33)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }, "MilkDrop-AutoPulseAudio").also { it.isDaemon = true }

        emitThread?.start()
    }

    override fun stop() {
        running.set(false)
        emitThread?.interrupt()
        emitThread?.join(200)
        emitThread = null
    }

    private fun nextBuffer(): ShortArray {
        val buffer = ShortArray(samplesPerFrame)
        val beatPhase = frameIndex % 24
        val kick = if (beatPhase < 5) 1.0 - beatPhase / 5.0 else 0.0
        val sweep = 0.5 + 0.5 * sin(frameIndex * 0.071)
        val frequency = 55.0 + 165.0 * sweep
        val amplitude = (2200.0 + 23000.0 * kick + 5000.0 * sweep).coerceAtMost(28000.0)
        val phaseStep = 2.0 * PI * frequency / sampleRate

        for (i in buffer.indices) {
            val tone = sin(phase)
            val harmonic = 0.35 * sin(phase * 2.01)
            buffer[i] = ((tone + harmonic) * amplitude).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            phase += phaseStep
            if (phase > 2.0 * PI) phase -= 2.0 * PI
        }
        frameIndex++
        return buffer
    }
}
