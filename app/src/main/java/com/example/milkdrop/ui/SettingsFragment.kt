package com.example.milkdrop.ui

import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.example.milkdrop.LauncherActivity
import com.example.milkdrop.R
import com.example.milkdrop.audio.AudioSourceType
import com.example.milkdrop.settings.AppSettings

/**
 * Wizard-style settings screen built on [GuidedStepSupportFragment].
 *
 * Presents five guided actions navigable entirely by D-pad:
 *  1. Preset cycle interval (10–300 s, step 10)
 *  2. Transition duration (1–10 s, step 1)
 *  3. Beat-driven transitions (toggle on/off)
 *  4. Audio source (Auto Pulse / Microphone / Silent)
 *  5. Display resolution (Native / 720p / 1080p)
 *
 * On confirm, persists changes via [SettingsRepository.update].
 */
class SettingsFragment : GuidedStepSupportFragment() {

    // -------------------------------------------------------------------------
    // Action IDs
    // -------------------------------------------------------------------------

    companion object {
        private const val ACTION_CYCLE_INTERVAL    = 1L
        private const val ACTION_TRANSITION_DUR    = 2L
        private const val ACTION_BEAT_DRIVEN       = 3L
        private const val ACTION_AUDIO_SOURCE      = 4L
        private const val ACTION_RESOLUTION        = 5L
        private const val ACTION_CRASH_LOG         = 6L
        private const val ACTION_CONFIRM           = 100L
        private const val ACTION_CANCEL            = 101L

        // Sub-action IDs for audio source
        private const val SUB_AUDIO_AUTO_PULSE     = 9L
        private const val SUB_AUDIO_MIC            = 10L
        private const val SUB_AUDIO_SILENT         = 12L

        // Sub-action IDs for resolution
        private const val SUB_RES_NATIVE           = 20L
        private const val SUB_RES_720P             = 21L
        private const val SUB_RES_1080P            = 22L
    }

    // Working copy of settings — mutated as the user navigates actions
    private lateinit var workingSettings: AppSettings

    // -------------------------------------------------------------------------
    // GuidedStepSupportFragment overrides
    // -------------------------------------------------------------------------

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            getString(R.string.settings_title),
            getString(R.string.settings_breadcrumb),
            getString(R.string.app_name),
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        workingSettings = if (LauncherActivity.isInitialized) {
            LauncherActivity.settingsRepository.get()
        } else {
            AppSettings()
        }

        // 1. Preset cycle interval
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_CYCLE_INTERVAL)
                .title(getString(R.string.setting_cycle_interval))
                .description(workingSettings.cycleIntervalSeconds.toString() + " s")
                .descriptionEditable(false)
                .build()
        )

        // 2. Transition duration
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_TRANSITION_DUR)
                .title(getString(R.string.setting_transition_duration))
                .description(workingSettings.transitionDurationSeconds.toInt().toString() + " s")
                .descriptionEditable(false)
                .build()
        )

        // 3. Beat-driven transitions (toggle)
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_BEAT_DRIVEN)
                .title(getString(R.string.setting_beat_driven))
                .description(beatDrivenLabel(workingSettings.beatDrivenTransitions))
                .build()
        )

        // 4. Audio source (sub-actions)
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_AUDIO_SOURCE)
                .title(getString(R.string.setting_audio_source))
                .description(audioSourceLabel(workingSettings.audioSource))
                .subActions(buildAudioSourceSubActions(workingSettings.audioSource))
                .build()
        )

        // 5. Display resolution (sub-actions)
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_RESOLUTION)
                .title(getString(R.string.setting_resolution))
                .description(resolutionLabel(workingSettings.renderWidth, workingSettings.renderHeight))
                .subActions(buildResolutionSubActions(workingSettings.renderWidth, workingSettings.renderHeight))
                .build()
        )

        // 6. Crash log (developer tool)
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_CRASH_LOG)
                .title("View Crash Log")
                .description("Share or clear the debug crash log")
                .build()
        )

        // Confirm / Cancel
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_CONFIRM)
                .title(getString(R.string.settings_confirm))
                .build()
        )
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_CANCEL)
                .title(getString(R.string.settings_cancel))
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_CYCLE_INTERVAL -> {
                // Cycle through values: 10, 20, 30, … 300
                val current = workingSettings.cycleIntervalSeconds
                val next = if (current >= 300) 10 else current + 10
                workingSettings = workingSettings.copy(cycleIntervalSeconds = next)
                action.description = "$next s"
                notifyActionChanged(findActionPositionById(ACTION_CYCLE_INTERVAL))
            }

            ACTION_TRANSITION_DUR -> {
                // Cycle through values: 1, 2, … 10
                val current = workingSettings.transitionDurationSeconds.toInt()
                val next = if (current >= 10) 1 else current + 1
                workingSettings = workingSettings.copy(transitionDurationSeconds = next.toFloat())
                action.description = "$next s"
                notifyActionChanged(findActionPositionById(ACTION_TRANSITION_DUR))
            }

            ACTION_BEAT_DRIVEN -> {
                val toggled = !workingSettings.beatDrivenTransitions
                workingSettings = workingSettings.copy(beatDrivenTransitions = toggled)
                action.description = beatDrivenLabel(toggled)
                notifyActionChanged(findActionPositionById(ACTION_BEAT_DRIVEN))
            }

            ACTION_CRASH_LOG -> {
                val ctx = requireContext()
                val log = com.example.milkdrop.CrashLogger.getLog(ctx)
                val tv = android.widget.TextView(ctx).apply {
                    text = if (log.length > 3000) log.takeLast(3000) else log
                    textSize = 13f
                    setPadding(32, 32, 32, 32)
                    setTextColor(0xFFCCCCCC.toInt())
                    setBackgroundColor(0xFF0D0D0D.toInt())
                }
                val scroll = android.widget.ScrollView(ctx).apply { addView(tv) }
                android.app.AlertDialog.Builder(ctx)
                    .setTitle("Crash Log")
                    .setView(scroll)
                    .setPositiveButton("Share") { _, _ ->
                        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "MilkDrop TV Crash Log")
                            putExtra(android.content.Intent.EXTRA_TEXT, log)
                        }
                        startActivity(android.content.Intent.createChooser(i, "Share crash log"))
                    }
                    .setNeutralButton("Clear") { _, _ -> com.example.milkdrop.CrashLogger.clear(ctx) }
                    .setNegativeButton("Close", null)
                    .show()
            }

            ACTION_CONFIRM -> {
                saveSettings()
                activity?.finish()
            }

            ACTION_CANCEL -> {
                activity?.finish()
            }
        }
    }

    override fun onGuidedActionEditedAndProceed(action: GuidedAction): Long {
        return GuidedAction.ACTION_ID_NEXT
    }

    override fun onSubGuidedActionClicked(action: GuidedAction): Boolean {
        when (action.id) {
            // Audio source sub-actions
            SUB_AUDIO_AUTO_PULSE -> {
                workingSettings = workingSettings.copy(audioSource = AudioSourceType.AUTO_PULSE)
                updateActionDescription(ACTION_AUDIO_SOURCE, audioSourceLabel(AudioSourceType.AUTO_PULSE))
                updateAudioSourceSubActions(AudioSourceType.AUTO_PULSE)
            }
            SUB_AUDIO_MIC -> {
                workingSettings = workingSettings.copy(audioSource = AudioSourceType.MICROPHONE)
                updateActionDescription(ACTION_AUDIO_SOURCE, audioSourceLabel(AudioSourceType.MICROPHONE))
                updateAudioSourceSubActions(AudioSourceType.MICROPHONE)
            }
            SUB_AUDIO_SILENT -> {
                workingSettings = workingSettings.copy(audioSource = AudioSourceType.SILENT)
                updateActionDescription(ACTION_AUDIO_SOURCE, audioSourceLabel(AudioSourceType.SILENT))
                updateAudioSourceSubActions(AudioSourceType.SILENT)
            }

            // Resolution sub-actions
            SUB_RES_NATIVE -> {
                workingSettings = workingSettings.copy(renderWidth = 0, renderHeight = 0)
                updateActionDescription(ACTION_RESOLUTION, resolutionLabel(0, 0))
                updateResolutionSubActions(0, 0)
            }
            SUB_RES_720P -> {
                workingSettings = workingSettings.copy(renderWidth = 1280, renderHeight = 720)
                updateActionDescription(ACTION_RESOLUTION, resolutionLabel(1280, 720))
                updateResolutionSubActions(1280, 720)
            }
            SUB_RES_1080P -> {
                workingSettings = workingSettings.copy(renderWidth = 1920, renderHeight = 1080)
                updateActionDescription(ACTION_RESOLUTION, resolutionLabel(1920, 1080))
                updateResolutionSubActions(1920, 1080)
            }
        }
        return true // collapse sub-actions
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun saveSettings() {
        if (LauncherActivity.isInitialized) {
            LauncherActivity.settingsRepository.update(workingSettings)
        }
    }

    private fun beatDrivenLabel(enabled: Boolean): String =
        if (enabled) getString(R.string.setting_on) else getString(R.string.setting_off)

    private fun audioSourceLabel(source: AudioSourceType): String = when (source) {
        AudioSourceType.AUTO_PULSE  -> getString(R.string.audio_source_auto_pulse)
        AudioSourceType.MICROPHONE   -> getString(R.string.audio_source_microphone)
        AudioSourceType.SYSTEM_AUDIO -> getString(R.string.audio_source_system)
        AudioSourceType.SILENT       -> getString(R.string.audio_source_silent)
    }

    private fun resolutionLabel(width: Int, height: Int): String = when {
        width == 0 && height == 0 -> getString(R.string.setting_resolution_native)
        width == 1280              -> getString(R.string.setting_resolution_720p)
        width == 1920              -> getString(R.string.setting_resolution_1080p)
        else                       -> getString(R.string.setting_resolution_native)
    }

    private fun buildAudioSourceSubActions(current: AudioSourceType): List<GuidedAction> {
        return listOf(
            GuidedAction.Builder(requireContext())
                .id(SUB_AUDIO_AUTO_PULSE)
                .title(getString(R.string.audio_source_auto_pulse))
                .description("Generated reactive signal, no privacy dot")
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(current == AudioSourceType.AUTO_PULSE)
                .build(),
            GuidedAction.Builder(requireContext())
                .id(SUB_AUDIO_MIC)
                .title(getString(R.string.audio_source_microphone))
                .description("Uses a real mic if one exists; shows privacy dot")
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(current == AudioSourceType.MICROPHONE)
                .build(),
            GuidedAction.Builder(requireContext())
                .id(SUB_AUDIO_SILENT)
                .title(getString(R.string.audio_source_silent))
                .description("No audio input")
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(current == AudioSourceType.SILENT)
                .build()
        )
    }

    private fun buildResolutionSubActions(width: Int, height: Int): List<GuidedAction> {
        return listOf(
            GuidedAction.Builder(requireContext())
                .id(SUB_RES_NATIVE)
                .title(getString(R.string.setting_resolution_native))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(width == 0 && height == 0)
                .build(),
            GuidedAction.Builder(requireContext())
                .id(SUB_RES_720P)
                .title(getString(R.string.setting_resolution_720p))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(width == 1280)
                .build(),
            GuidedAction.Builder(requireContext())
                .id(SUB_RES_1080P)
                .title(getString(R.string.setting_resolution_1080p))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(width == 1920)
                .build()
        )
    }

    private fun updateActionDescription(actionId: Long, description: String) {
        val pos = findActionPositionById(actionId)
        if (pos >= 0) {
            actions[pos].description = description
            notifyActionChanged(pos)
        }
    }

    private fun updateAudioSourceSubActions(selected: AudioSourceType) {
        val pos = findActionPositionById(ACTION_AUDIO_SOURCE)
        if (pos >= 0) {
            actions[pos].subActions = buildAudioSourceSubActions(selected)
            notifyActionChanged(pos)
        }
    }

    private fun updateResolutionSubActions(width: Int, height: Int) {
        val pos = findActionPositionById(ACTION_RESOLUTION)
        if (pos >= 0) {
            actions[pos].subActions = buildResolutionSubActions(width, height)
            notifyActionChanged(pos)
        }
    }
}
