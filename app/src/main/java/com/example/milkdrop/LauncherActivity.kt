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

class LauncherActivity : FragmentActivity(), SplashFragment.SplashCompleteListener {

    companion object {
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
        var isInitialized: Boolean = false
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.install(this)
        setContentView(R.layout.activity_launcher)

        if (!isInitialized) {
            initializeSingletons()
        }

        if (savedInstanceState != null) return

        val presetDir = assetExtractor.getPresetDirectory()
        val presetsReady = presetDir.exists() && presetDir.list()?.isNotEmpty() == true

        if (presetsReady) {
            showMainFragment()
        } else {
            showSplashFragment()
        }
    }

    override fun onSplashComplete(presetCount: Int) {
        showMainFragment()
    }

    private fun initializeSingletons() {
        settingsRepository = SettingsRepository(applicationContext)
        assetExtractor     = AssetExtractor(applicationContext)
        bridge             = ProjectMBridge()
        presetParser       = PresetParser(bridge)
        audioFrameQueue    = AudioFrameQueue(capacity = 4)
        audioCaptureManager = AudioCaptureManager(applicationContext, audioFrameQueue)
        renderer = ProjectMRenderer(
            bridge = bridge,
            audioQueue = audioFrameQueue,
            presetDirectory = assetExtractor.getPresetDirectory().absolutePath
        )
        beatDetector = BeatDetector(bridge)
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
