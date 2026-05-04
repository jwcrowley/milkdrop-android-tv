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
import androidx.lifecycle.lifecycleScope
import com.example.milkdrop.audio.AudioCaptureManager
import com.example.milkdrop.audio.AudioFrameQueue
import com.example.milkdrop.audio.AudioSourceType
import com.example.milkdrop.audio.BeatDetector
import com.example.milkdrop.preset.PresetLibrary
import com.example.milkdrop.preset.PresetFavoritesRepository
import com.example.milkdrop.preset.PresetManager
import com.example.milkdrop.preset.PresetParser
import com.example.milkdrop.renderer.ProjectMRenderer
import com.example.milkdrop.renderer.RenderResolution
import com.example.milkdrop.settings.SettingsRepository
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VisualizerActivity : FragmentActivity() {

    companion object {
        private const val OVERLAY_HIDE_DELAY_MS = 3_000L
        private const val AUDIO_LEVEL_REFRESH_MS = 250L
    }

    private lateinit var surfaceView: GLSurfaceView
    private lateinit var bridge: ProjectMBridge
    private lateinit var audioFrameQueue: AudioFrameQueue
    private lateinit var renderer: ProjectMRenderer
    private lateinit var audioCaptureManager: AudioCaptureManager
    private lateinit var beatDetector: BeatDetector
    private lateinit var presetManager: PresetManager
    private lateinit var favoritesRepository: PresetFavoritesRepository
    private lateinit var settingsRepository: SettingsRepository
    private var ownsInstances = false
    private var audioIndicatorJob: Job? = null
    private var activeAudioSource = AudioSourceType.SILENT

    private val overlayHideHandler = Handler(Looper.getMainLooper())
    private val audioLevelHandler = Handler(Looper.getMainLooper())
    private var overlayVisible = false
    private val audioLevelRunnable = object : Runnable {
        override fun run() {
            updateAudioIndicator()
            audioLevelHandler.postDelayed(this, AUDIO_LEVEL_REFRESH_MS)
        }
    }

    // Simple overlay — just a TextView, no fragment transaction needed
    private lateinit var overlayView: View
    private lateinit var presetNameText: TextView
    private lateinit var hintText: TextView
    private lateinit var audioIndicatorText: TextView

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
            favoritesRepository = LauncherActivity.presetFavoritesRepository
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
            renderer.setBeatDetector(beatDetector)
            favoritesRepository = PresetFavoritesRepository(applicationContext)
            presetManager = PresetManager(
                library      = PresetLibrary.build(File(filesDir, "presets"), PresetParser(bridge)),
                renderer     = renderer,
                beatDetector = beatDetector,
                favoritesRepository = favoritesRepository,
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
        audioIndicatorText = overlayView.findViewById(R.id.overlay_audio_indicator)
        hintText.text  = "OK = Next  ◀ = Previous  ▲ = Favorite  ▼ = Favorites Mode"
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
        startAudio(settings.audioSource)
        if (presetManager.getCurrentPreset() == null) {
            presetManager.start()
        }
        val resolution = when {
            settings.renderWidth == 1280  -> RenderResolution.HD_720P
            settings.renderWidth == 1920  -> RenderResolution.FHD_1080P
            else                          -> RenderResolution.NATIVE
        }
        renderer.setRenderResolution(resolution)

        // Keep the audio indicator in sync with the actual active source
        audioIndicatorJob?.cancel()
        audioIndicatorJob = lifecycleScope.launch {
            audioCaptureManager.activeSourceFlow.collectLatest { source ->
                activeAudioSource = source
                updateAudioIndicator()
            }
        }
        audioLevelHandler.removeCallbacksAndMessages(null)
        audioLevelHandler.post(audioLevelRunnable)
    }

    override fun onPause() {
        super.onPause()
        audioLevelHandler.removeCallbacksAndMessages(null)
        audioIndicatorJob?.cancel()
        audioIndicatorJob = null
        audioCaptureManager.stop()
        presetManager.stop()
        surfaceView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayHideHandler.removeCallbacksAndMessages(null)
        audioLevelHandler.removeCallbacksAndMessages(null)
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
            KeyEvent.KEYCODE_DPAD_UP     -> { toggleFavorite(); true }
            KeyEvent.KEYCODE_DPAD_DOWN   -> { toggleFavoritesOnlyMode(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun showOverlay() {
        overlayHideHandler.removeCallbacksAndMessages(null)
        presetNameText.text = currentPresetLabel()
        // Audio indicator is kept live by the flow collector in onResume — no snapshot read needed
        overlayView.visibility = View.VISIBLE
        overlayVisible = true
        overlayHideHandler.postDelayed({
            overlayView.visibility = View.GONE
            overlayVisible = false
        }, OVERLAY_HIDE_DELAY_MS)
    }

    private fun currentPresetLabel(): String {
        val preset = presetManager.getCurrentPreset() ?: return "MilkDrop TV"
        val favorite = if (favoritesRepository.isFavorite(preset)) "★ " else ""
        val mode = if (presetManager.isFavoritesOnlyMode()) "  •  Favorites" else ""
        return favorite + preset.name + mode
    }

    private fun toggleFavorite() {
        val preset = presetManager.getCurrentPreset() ?: return
        val isFavorite = presetManager.toggleFavorite(preset)
        showOverlay()
        presetNameText.text = (if (isFavorite) "★ Added favorite: " else "Removed favorite: ") + preset.name
    }

    private fun toggleFavoritesOnlyMode() {
        val enabled = presetManager.toggleFavoritesOnlyMode()
        showOverlay()
        presetNameText.text = if (enabled) {
            "Favorites-only cycling"
        } else {
            "All-presets cycling"
        }
    }

    private fun startAudio(sourceType: AudioSourceType) {
        val safeSource = if (sourceType == AudioSourceType.SYSTEM_AUDIO) {
            AudioSourceType.SILENT
        } else {
            sourceType
        }
        audioCaptureManager.start(safeSource)
    }

    private fun updateAudioIndicator() {
        val level = (bridge.getBass() * 100f).toInt().coerceIn(0, 100)
        audioIndicatorText.text = when (activeAudioSource) {
            AudioSourceType.MICROPHONE -> "🎤 Mic ${level}%"
            AudioSourceType.SYSTEM_AUDIO -> "🔇 System disabled"
            AudioSourceType.SILENT -> "🔇 Silent"
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            renderer.setRenderResolution(RenderResolution.HALF_NATIVE)
        }
    }
}
