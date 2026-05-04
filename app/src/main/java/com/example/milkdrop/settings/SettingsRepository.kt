package com.example.milkdrop.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.milkdrop.audio.AudioSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists and exposes [AppSettings] via [SharedPreferences].
 *
 * No Google Play Services dependency — uses the standard Android
 * [SharedPreferences] API directly.
 *
 * Exposes a [StateFlow] so UI components can reactively observe settings changes.
 */
class SettingsRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "milkdrop_settings"

        // SharedPreferences keys
        private const val KEY_CYCLE_INTERVAL = "cycle_interval_seconds"
        private const val KEY_TRANSITION_DURATION = "transition_duration_seconds"
        private const val KEY_BEAT_DRIVEN = "beat_driven_transitions"
        private const val KEY_BASS_THRESHOLD = "bass_threshold"
        private const val KEY_AUDIO_SOURCE = "audio_source"
        private const val KEY_AUDIO_SOURCE_EXPLICIT = "audio_source_explicit"
        private const val KEY_RENDER_WIDTH = "render_width"
        private const val KEY_RENDER_HEIGHT = "render_height"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(load())

    /**
     * Reactive stream of the current [AppSettings].
     * Emits a new value whenever [update] is called.
     */
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    /** Returns the current settings snapshot. */
    fun get(): AppSettings = _settingsFlow.value

    /**
     * Persist [settings] to [SharedPreferences] and emit the new value on [settingsFlow].
     */
    fun update(settings: AppSettings) {
        save(settings, audioSourceExplicit = true)
        _settingsFlow.value = settings
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun load(): AppSettings {
        val defaults = AppSettings()
        val storedAudioSource = prefs.getString(KEY_AUDIO_SOURCE, defaults.audioSource.name)
            ?: defaults.audioSource.name
        val audioSource = runCatching { AudioSourceType.valueOf(storedAudioSource) }
            .getOrDefault(defaults.audioSource)
        val migratedAudioSource = if (
            audioSource == AudioSourceType.SYSTEM_AUDIO &&
            !prefs.getBoolean(KEY_AUDIO_SOURCE_EXPLICIT, false)
        ) {
            AudioSourceType.SILENT
        } else {
            audioSource
        }

        return AppSettings(
            cycleIntervalSeconds = prefs.getInt(KEY_CYCLE_INTERVAL, defaults.cycleIntervalSeconds)
                .coerceIn(10, 300),
            transitionDurationSeconds = prefs.getFloat(KEY_TRANSITION_DURATION, defaults.transitionDurationSeconds)
                .coerceIn(1f, 10f),
            beatDrivenTransitions = prefs.getBoolean(KEY_BEAT_DRIVEN, defaults.beatDrivenTransitions),
            bassThreshold = prefs.getFloat(KEY_BASS_THRESHOLD, defaults.bassThreshold)
                .coerceIn(0f, 1f),
            audioSource = migratedAudioSource,
            renderWidth = prefs.getInt(KEY_RENDER_WIDTH, defaults.renderWidth),
            renderHeight = prefs.getInt(KEY_RENDER_HEIGHT, defaults.renderHeight)
        )
    }

    private fun save(settings: AppSettings, audioSourceExplicit: Boolean) {
        prefs.edit().apply {
            putInt(KEY_CYCLE_INTERVAL, settings.cycleIntervalSeconds)
            putFloat(KEY_TRANSITION_DURATION, settings.transitionDurationSeconds)
            putBoolean(KEY_BEAT_DRIVEN, settings.beatDrivenTransitions)
            putFloat(KEY_BASS_THRESHOLD, settings.bassThreshold)
            putString(KEY_AUDIO_SOURCE, settings.audioSource.name)
            putBoolean(KEY_AUDIO_SOURCE_EXPLICIT, audioSourceExplicit)
            putInt(KEY_RENDER_WIDTH, settings.renderWidth)
            putInt(KEY_RENDER_HEIGHT, settings.renderHeight)
            apply()
        }
    }
}
