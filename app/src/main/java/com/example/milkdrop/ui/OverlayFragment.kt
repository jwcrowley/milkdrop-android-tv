package com.example.milkdrop.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.milkdrop.LauncherActivity
import com.example.milkdrop.R
import com.example.milkdrop.audio.AudioSourceType

/**
 * Semi-transparent overlay displayed over the fullscreen visualizer.
 *
 * Shows:
 * - Current preset name (36sp bold)
 * - Audio source indicator (24sp)
 * - Action hints: "⏎ Next  ◀ Back  ⚙ Settings" (24sp)
 *
 * Auto-hiding is managed by the hosting [VisualizerActivity] via a 3-second
 * [Handler] timer. This fragment is purely presentational.
 *
 * All text is ≥ 24sp as required by the TV UI specification.
 */
class OverlayFragment : Fragment() {

    private lateinit var presetNameView: TextView
    private lateinit var audioSourceView: TextView

    companion object {
        fun newInstance(): OverlayFragment = OverlayFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_overlay, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presetNameView = view.findViewById(R.id.overlay_preset_name)
        audioSourceView = view.findViewById(R.id.overlay_audio_source)

        // Populate with current state from the shared singletons
        if (LauncherActivity.isInitialized) {
            val currentPreset = LauncherActivity.presetManager.getCurrentPreset()
            presetNameView.text = currentPreset?.name ?: ""

            val sourceType = LauncherActivity.audioCaptureManager.activeSourceFlow.value
            audioSourceView.text = formatAudioSource(sourceType)
        }
    }

    /**
     * Update the displayed preset name. Called by [VisualizerActivity] when the
     * overlay is already visible and the preset changes.
     */
    fun updatePresetName(name: String) {
        if (::presetNameView.isInitialized) {
            presetNameView.text = name
        }
    }

    /**
     * Update the displayed audio source indicator. Called by [VisualizerActivity]
     * when the audio source changes.
     */
    fun updateAudioSource(sourceType: AudioSourceType) {
        if (::audioSourceView.isInitialized) {
            audioSourceView.text = formatAudioSource(sourceType)
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun formatAudioSource(sourceType: AudioSourceType): String {
        val sourceName = when (sourceType) {
            AudioSourceType.MICROPHONE   -> getString(R.string.audio_source_microphone)
            AudioSourceType.SYSTEM_AUDIO -> getString(R.string.audio_source_system)
            AudioSourceType.SILENT       -> getString(R.string.audio_source_silent)
        }
        return getString(R.string.audio_source_label, sourceName)
    }
}
