package com.example.milkdrop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
        private const val TAG = "LauncherActivity"
        private const val REQUEST_AUDIO_PERMISSION = 1001

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

        /** Called after extraction completes to rebuild the library with all extracted presets. */
        fun rebuildPresetLibrary() {
            presetLibrary = PresetLibrary.build(
                bundledPresetDir = assetExtractor.getPresetDirectory(),
                parser = presetParser
            )
            presetManager.rebuildLibrary(presetLibrary)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.install(this)
        setContentView(R.layout.activity_launcher)

        if (!isInitialized) {
            initializeSingletons()
        }

        // Request RECORD_AUDIO at runtime — just having it in the manifest isn't enough on API 23+
        requestAudioPermissionIfNeeded()

        if (savedInstanceState != null) return

        // Always go through SplashFragment — it runs extractPresetsIfNeeded() which checks
        // the version stamp and re-extracts if the app was updated. Only skips if already
        // extracted for the current versionCode.
        showSplashFragment()
    }

    private fun requestAudioPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Requesting RECORD_AUDIO permission")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION
            )
        } else {
            Log.i(TAG, "RECORD_AUDIO permission already granted")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "RECORD_AUDIO permission granted")
                // If the visualizer is already running it will pick this up on next onResume.
                // Nothing else needed here — AudioCaptureManager checks permission on each start().
            } else {
                Log.w(TAG, "RECORD_AUDIO permission denied — visualizer will run in silent mode")
            }
        }
    }

    override fun onSplashComplete(presetCount: Int) {
        showMainFragment()
    }

    private fun initializeSingletons() {
        settingsRepository  = SettingsRepository(applicationContext)
        assetExtractor      = AssetExtractor(applicationContext)
        bridge              = ProjectMBridge()
        presetParser        = PresetParser(bridge)
        audioFrameQueue     = AudioFrameQueue(capacity = 4)
        audioCaptureManager = AudioCaptureManager(applicationContext, audioFrameQueue)
        renderer = ProjectMRenderer(
            bridge = bridge,
            audioQueue = audioFrameQueue,
            presetDirectory = assetExtractor.getPresetDirectory().absolutePath
        )
        beatDetector  = BeatDetector(bridge)
        presetLibrary = PresetLibrary.build(
            bundledPresetDir = assetExtractor.getPresetDirectory(),
            parser = presetParser
        )
        presetManager = PresetManager(
            library      = presetLibrary,
            renderer     = renderer,
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
