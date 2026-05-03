package com.example.milkdrop

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.example.milkdrop.audio.AudioCaptureManager
import com.example.milkdrop.audio.AudioFrameQueue
import com.example.milkdrop.audio.BeatDetector
import com.example.milkdrop.preset.PresetLibrary
import com.example.milkdrop.preset.PresetManager
import com.example.milkdrop.preset.PresetParser
import com.example.milkdrop.renderer.ProjectMRenderer
import com.example.milkdrop.renderer.RenderResolution
import com.example.milkdrop.settings.SettingsRepository
import java.io.File

class VisualizerActivity : FragmentActivity() {

    companion object {
        private const val OVERLAY_HIDE_DELAY_MS = 3_000L
    }

    private lateinit var surfaceView: GLSurfaceView
    private lateinit var bridge: ProjectMBridge
    private lateinit var audioFrameQueue: AudioFrameQueue
    private lateinit var renderer: ProjectMRenderer
    private lateinit var audioCaptureManager: AudioCaptureManager
    private lateinit var beatDetector: BeatDetector
    private lateinit var presetManager: PresetManager
    private lateinit var settingsRepository: SettingsRepository
    private var ownsInstances = false

    private val overlayHideHandler = Handler(Looper.getMainLooper())
    private var overlayVisible = false

    // Simple overlay — just a TextView, no fragment transaction needed
    private lateinit var overlayView: View
    private lateinit var presetNameText: TextView
    private lateinit var hintText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        // Wire up singletons
        if (LauncherActivity.isInitialized) {
            bridge              = LauncherActivity.bridge
            audioFrameQueue     = LauncherActivity.audioFrameQueue
            renderer            = LauncherActivity.renderer
            audioCaptureManager = LauncherActivity.audioCaptureManager
            beatDetector        = LauncherActivity.beatDetector
            presetManager       = LauncherActivity.presetManager
            settingsRepository  = LauncherActivity.settingsRepository
            ownsInstances       = false
        } else {
            ownsInstances       = true
            settingsRepository  = SettingsRepository(applicationContext)
            bridge              = ProjectMBridge()
            audioFrameQueue     = AudioFrameQueue(capacity = 4)
            audioCaptureManager = AudioCaptureManager(applicationContext, audioFrameQueue)
            renderer = ProjectMRenderer(
                bridge         = bridge,
                audioQueue     = audioFrameQueue,
                presetDirectory = File(filesDir, "presets").absolutePath
            )
            beatDetector = BeatDetector(bridge)
            presetManager = PresetManager(
                library      = PresetLibrary.build(File(filesDir, "presets"), PresetParser(bridge)),
                renderer     = renderer,
                beatDetector = beatDetector,
                settingsFlow = settingsRepository.settingsFlow
            )
        }

        // Build layout programmatically — no XML dependency that could fail
        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())

        // GL surface — correct init order: EGL version → renderer → render mode
        surfaceView = GLSurfaceView(this).also { gl ->
            gl.setEGLContextClientVersion(3)
            gl.setRenderer(renderer)
            gl.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        root.addView(surfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Overlay — semi-transparent bar at the bottom
        overlayView = layoutInflater.inflate(R.layout.overlay_simple, root, false)
        presetNameText = overlayView.findViewById(R.id.overlay_preset_name)
        hintText       = overlayView.findViewById(R.id.overlay_hints)
        hintText.text  = "OK = Next  ◀ = Back  Back = Menu"
        root.addView(overlayView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = android.view.Gravity.BOTTOM })

        setContentView(root)
        showOverlay()
    }

    override fun onResume() {
        super.onResume()
        surfaceView.onResume()
        val settings = settingsRepository.get()
        audioCaptureManager.start(settings.audioSource)
        if (presetManager.getCurrentPreset() == null) {
            presetManager.start()
        }
        val resolution = when {
            settings.renderWidth == 1280  -> RenderResolution.HD_720P
            settings.renderWidth == 1920  -> RenderResolution.FHD_1080P
            else                          -> RenderResolution.NATIVE
        }
        renderer.setRenderResolution(resolution)
    }

    override fun onPause() {
        super.onPause()
        audioCaptureManager.stop()
        presetManager.stop()
        surfaceView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayHideHandler.removeCallbacksAndMessages(null)
        if (ownsInstances) {
            presetManager.release()
            renderer.release()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        showOverlay()
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> { presetManager.nextPreset(smooth = true); true }
            KeyEvent.KEYCODE_DPAD_LEFT   -> { presetManager.previousPreset(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun showOverlay() {
        overlayHideHandler.removeCallbacksAndMessages(null)
        presetNameText.text = presetManager.getCurrentPreset()?.name ?: "MilkDrop TV"
        overlayView.visibility = View.VISIBLE
        overlayVisible = true
        overlayHideHandler.postDelayed({
            overlayView.visibility = View.GONE
            overlayVisible = false
        }, OVERLAY_HIDE_DELAY_MS)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            renderer.setRenderResolution(RenderResolution.HALF_NATIVE)
        }
    }
}
