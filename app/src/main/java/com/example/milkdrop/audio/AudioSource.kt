package com.example.milkdrop.audio

/**
 * Common interface for all audio capture sources.
 *
 * Implementations:
 * - [MicrophoneAudioSource] — captures from the device microphone via AudioRecord
 * - [SystemAudioSource] — captures system audio playback (API 29+)
 * - [AutoPulseAudioSource] — generated beat-like PCM for mic-less TV hardware
 * - [SilentAudioSource] — emits zero-amplitude frames (fallback when permission denied)
 */
interface AudioSource {
    /** Start capturing audio. [callback] is invoked on the capture thread for each [AudioFrame]. */
    fun start(callback: (AudioFrame) -> Unit)
    /** Stop capturing and release resources. */
    fun stop()
    /** Sample rate in Hz (44100 or 48000). */
    val sampleRate: Int
    /** Number of channels: 1 (mono) or 2 (stereo). */
    val channelCount: Int
}

/** Identifies which audio source type is active. Used in Settings and UI. */
enum class AudioSourceType {
    AUTO_PULSE,
    MICROPHONE,
    SYSTEM_AUDIO,
    SILENT
}
