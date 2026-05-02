package com.example.milkdrop

import android.content.ComponentCallbacks2
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.example.milkdrop.audio.AudioCaptureManager
import com.example.milkdrop.audio.AudioFrameQueue
import com.example.milkdrop.audio.BeatDetector
import com.example.milkdrop.preset.PresetLibrary
import com.example.milkdrop.preset.PresetManager
import com.example.milkdrop.preset.PresetParser
import com.example.milkdrop.renderer.ProjectMRenderer
import com.example.milkdrop.renderer.RenderResolution
import com.example.milkdrop.renderer.VisualizerSurfaceView
import com.example.milkdrop.settings.SettingsRepository
import com.example.milkdrop.ui.OverlayFragment
import java.io.File

/**
 * Fullscreen visualizer activity.
 *
 * Hosts [VisualizerSurfaceView] with no title bar or status bar.
 * Handles D-pad key events for preset navigation and overlay management.
 *
 * ## D-pad key handling
 * - [KeyEvent.KEYCODE_DPAD_CENTER] → [PresetManager.nextPreset]
 * - [KeyEvent.KEYCODE_DPAD_LEFT]   → [PresetManager.previousPreset]
 * - Any key                        → show [OverlayFragment], reset 3-second auto-hide timer
 *
 * ## Lifecycle
 * - [onResume]: starts [AudioCaptureManager] and [GLSurfaceView.onResume]
 * - [onPause]:  stops [AudioCaptureManager] and [GLSurfaceView.onPause]
 * - [onDestroy]: releases [PresetManager] resources
 *
 * ## Memory pressure
 * On [ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL], the render resolution
 * is reduced to 50% of native via [ProjectMBridge.reinitialize].
 */
class VisualizerActivity : FragmentActivity(), ComponentCallbacks2 {

    companion object {
        private const val OVERLAY_HIDE_DELAY_MS = 3_000L
        private const val OVERLAY_TAG = "overlay"
    }

    // -------------------------------------------------------------------------
    // Component references — obtained from LauncherActivity's companion object
    // when available, otherwise created locally.
    // -------------------------------------------------------------------------

    private lateinit var surfaceView: VisualizerSurfaceView
    private lateinit var bridge: ProjectMBridge
    private lateinit var audioFrameQueue: AudioFrameQueue
    private lateinit var renderer: ProjectMRenderer
    private lateinit var audioCaptureManager: AudioCaptureManager
    private lateinit var beatDetector: BeatDetector
    private lateinit var presetManager: PresetManager
    private lateinit var settingsRepository: SettingsRepository

    /** True when this activity created its own singletons (standalone launch). */
    private var ownsInstances = false

    private val overlayHideHandler = Handler(Looper.getMainLooper())
    private val overlayHideRunnable = Runnable { hideOverlay() }

    // Track whether the overlay is currently visible
    private var overlayVisible = false

    // Track native surface dimensions for memory-pressure resolution reduction
    private var nativeSurfaceWidth = 0
    private var nativeSurfaceHeight = 0

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen — no title bar, no status bar, keep screen on
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

        // Obtain shared singletons from LauncherActivity if available
        if (LauncherActivity.isInitialized) {
            bridge = LauncherActivity.bridge
            audioFrameQueue = LauncherActivity.audioFrameQueue
            renderer = LauncherActivity.renderer
            audioCaptureManager = LauncherActivity.audioCaptureManager
            beatDetector = LauncherActivity.beatDetector
            presetManager = LauncherActivity.presetManager
            settingsRepository = LauncherActivity.settingsRepository
            ownsInstances = false
        } else {
            // Standalone launch (e.g., from IDE) — create local instances
            ownsInstances = true
            settingsRepository = SettingsRepository(applicationContext)
            bridge = ProjectMBridge()
            audioFrameQueue = AudioFrameQueue(capacity = 4)
            audioCaptureManager = AudioCaptureManager(applicationContext, audioFrameQueue)
            renderer = ProjectMRenderer(
                bridge = bridge,
                audioQueue = audioFrameQueue,
                presetDirectory = File(filesDir, "presets").absolutePath
            )
            beatDetector = BeatDetector(bridge)
            presetManager = PresetManager(
                library = PresetLibrary.build(
                    bundledPresetDir = File(filesDir, "presets"),
                    parser = PresetParser(bridge)
                ),
                renderer = renderer,
                beatDetector = beatDetector,
                settingsFlow = settingsRepository.settingsFlow
            )
        }

        // Inflate the root layout (two-layer FrameLayout: surface + overlay)
        setContentView(R.layout.activity_visualizer)

        // Add the GLSurfaceView into the surface container
        val surfaceContainer = findViewById<FrameLayout>(R.id.visualizer_surface_container)
        surfaceView = VisualizerSurfaceView(this)
        surfaceView.attachRenderer(renderer)
        surfaceContainer.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Show overlay on first launch; it auto-hides after 3 seconds
        if (savedInstanceState == null) {
            showOverlay()
        }
    }

    override fun onResume() {
        super.onResume()
        surfaceView.onResume()
        val settings = settingsRepository.get()
        audioCaptureManager.start(settings.audioSource)
        presetManager.start()

        // Apply configured render resolution from settings
        val resolution = when {
            settings.renderWidth == 1280 && settings.renderHeight == 720 -> RenderResolution.HD_720P
            settings.renderWidth == 1920 && settings.renderHeight == 1080 -> RenderResolution.FHD_1080P
            settings.renderWidth == -1 && settings.renderHeight == -1 -> RenderResolution.HALF_NATIVE
            else -> RenderResolution.NATIVE
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
        overlayHideHandler.removeCallbacks(overlayHideRunnable)
        if (ownsInstances) {
            presetManager.release()
            renderer.release()
        }
    }

    // -------------------------------------------------------------------------
    // D-pad key handling
    // -------------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Any key press shows the overlay and resets the hide timer
        showOverlay()

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                presetManager.nextPreset(smooth = true)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                presetManager.previousPreset()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // -------------------------------------------------------------------------
    // Overlay management
    // -------------------------------------------------------------------------

    private fun showOverlay() {
        overlayHideHandler.removeCallbacks(overlayHideRunnable)

        if (!overlayVisible) {
            val fragment = OverlayFragment.newInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.visualizer_overlay_container, fragment, OVERLAY_TAG)
                .commitAllowingStateLoss()
            overlayVisible = true
        } else {
            // Update the overlay's preset name in case it changed
            (supportFragmentManager.findFragmentByTag(OVERLAY_TAG) as? OverlayFragment)
                ?.updatePresetName(presetManager.getCurrentPreset()?.name ?: "")
        }

        // Schedule auto-hide
        overlayHideHandler.postDelayed(overlayHideRunnable, OVERLAY_HIDE_DELAY_MS)
    }

    private fun hideOverlay() {
        val fragment = supportFragmentManager.findFragmentByTag(OVERLAY_TAG)
        if (fragment != null) {
            supportFragmentManager.beginTransaction()
                .remove(fragment)
                .commitAllowingStateLoss()
        }
        overlayVisible = false
    }

    // -------------------------------------------------------------------------
    // ComponentCallbacks2 — memory pressure handling
    // -------------------------------------------------------------------------

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            renderer.setRenderResolution(RenderResolution.HALF_NATIVE)
        }
    }

    /**
     * Called when the surface size is known, so we can store native dimensions
     * for memory-pressure handling.
     */
    fun onSurfaceSizeKnown(width: Int, height: Int) {
        nativeSurfaceWidth = width
        nativeSurfaceHeight = height
    }
}
