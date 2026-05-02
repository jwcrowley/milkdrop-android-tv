package com.example.milkdrop

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.example.milkdrop.audio.AudioCaptureManager
import com.example.milkdrop.audio.AudioFrameQueue
import com.example.milkdrop.audio.BeatDetector
import com.example.milkdrop.preset.AssetExtractor
import com.example.milkdrop.preset.PresetLibrary
import com.example.milkdrop.preset.PresetManager
import com.example.milkdrop.preset.PresetParser
import com.example.milkdrop.renderer.ProjectMRenderer
import com.example.milkdrop.settings.SettingsRepository
import com.example.milkdrop.ui.MainFragment
import com.example.milkdrop.ui.SplashFragment

/**
 * Entry point for the Android TV launcher.
 *
 * On first launch (or after an app update), shows [SplashFragment] while
 * [AssetExtractor] copies bundled presets to internal storage. Once extraction
 * completes (or on subsequent launches where presets are already present),
 * replaces the splash with [MainFragment].
 *
 * All shared application-level objects are created here and exposed via the
 * [Companion] object so that child fragments and activities can access them
 * without re-creating them.
 */
class LauncherActivity : FragmentActivity(), SplashFragment.SplashCompleteListener {

    companion object {
        /**
         * Application-level singletons shared across activities and fragments.
         * Initialized in [LauncherActivity.onCreate] and valid for the lifetime
         * of the process.
         */
        lateinit var settingsRepository: SettingsRepository
            private set

        lateinit var assetExtractor: AssetExtractor
            private set

        lateinit var presetParser: PresetParser
            private set

        lateinit var bridge: ProjectMBridge
            private set

        lateinit var audioFrameQueue: AudioFrameQueue
            private set

        lateinit var audioCaptureManager: AudioCaptureManager
            private set

        lateinit var renderer: ProjectMRenderer
            private set

        lateinit var beatDetector: BeatDetector
            private set

        lateinit var presetLibrary: PresetLibrary
            private set

        lateinit var presetManager: PresetManager
            private set

        /** True once all singletons have been initialized. */
        var isInitialized: Boolean = false
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        // Initialize shared singletons (idempotent — only runs once per process)
        if (!isInitialized) {
            initializeSingletons()
        }

        if (savedInstanceState != null) {
            // Fragment manager already restores the correct fragment on rotation
            return
        }

        // Decide which fragment to show first
        val extractor = assetExtractor
        val presetDir = extractor.getPresetDirectory()
        val presetsAlreadyExtracted = presetDir.exists() &&
            presetDir.list()?.isNotEmpty() == true

        if (presetsAlreadyExtracted) {
            // Subsequent launch — go straight to the main menu
            showMainFragment()
        } else {
            // First launch — show splash while presets are extracted
            showSplashFragment()
        }
    }

    // -------------------------------------------------------------------------
    // SplashFragment.SplashCompleteListener
    // -------------------------------------------------------------------------

    override fun onSplashComplete(presetCount: Int) {
        showMainFragment()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun initializeSingletons() {
        settingsRepository = SettingsRepository(applicationContext)
        assetExtractor = AssetExtractor(applicationContext)
        presetParser = PresetParser(ProjectMBridge())
        bridge = ProjectMBridge()
        audioFrameQueue = AudioFrameQueue(capacity = 4)
        audioCaptureManager = AudioCaptureManager(applicationContext, audioFrameQueue)
        renderer = ProjectMRenderer(
            bridge = bridge,
            audioQueue = audioFrameQueue,
            presetDirectory = assetExtractor.getPresetDirectory().absolutePath
        )
        beatDetector = BeatDetector(bridge)

        // PresetLibrary is built lazily after extraction; use an empty library
        // as a placeholder until SplashFragment signals completion.
        presetLibrary = PresetLibrary.build(
            bundledPresetDir = assetExtractor.getPresetDirectory(),
            parser = presetParser
        )

        presetManager = PresetManager(
            library = presetLibrary,
            renderer = renderer,
            beatDetector = beatDetector,
            settingsFlow = settingsRepository.settingsFlow
        )

        isInitialized = true
    }

    private fun showSplashFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.launcher_container, SplashFragment())
            .commitNow()
    }

    private fun showMainFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.launcher_container, MainFragment())
            .commitNow()
    }
}
