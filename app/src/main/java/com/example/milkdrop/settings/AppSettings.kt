package com.example.milkdrop.settings

import com.example.milkdrop.audio.AudioSourceType

/**
 * Persistent user preferences for the MilkDrop TV app.
 *
 * All fields have sensible defaults. Persisted via [SettingsRepository].
 */
data class AppSettings(
    /** How long each preset is displayed before an automatic transition (seconds). Range: 10–300. */
    val cycleIntervalSeconds: Int = 30,

    /** Duration of the blend transition between presets (seconds). Range: 1–10. */
    val transitionDurationSeconds: Float = 3f,

    /** Whether to trigger preset changes on bass beats detected by [BeatDetector]. */
    val beatDrivenTransitions: Boolean = false,

    /** Bass energy threshold (0.0–1.0) for beat-driven transitions. */
    val bassThreshold: Float = 0.8f,

    /** Which audio source to use for reactivity. */
    val audioSource: AudioSourceType = AudioSourceType.MICROPHONE,

    /**
     * Render width in pixels. 0 = native display resolution.
     * Set to a specific value to override (e.g., 1280 for 720p).
     */
    val renderWidth: Int = 0,

    /**
     * Render height in pixels. 0 = native display resolution.
     * Set to a specific value to override (e.g., 720 for 720p).
     */
    val renderHeight: Int = 0
)
