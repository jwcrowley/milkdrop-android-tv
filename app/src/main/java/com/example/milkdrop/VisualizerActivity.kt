package com.example.milkdrop

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjection
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.milkdrop.audio.AudioCaptureManager
import com.example.milkdrop.audio.AudioFrameQueue
import com.example.milkdrop.audio.AudioSourceType
import com.example.milkdrop.audio.BeatDetector
import com.example.milkdrop.audio.PlaybackCaptureService
import com.example.milkdrop.preset.PresetLibrary
import com.example.milkdrop.preset.PresetFavoritesRepository
import com.example.milkdrop.preset.PresetManager
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
    private var playbackCaptureJob: Job? = null
    private var activeAudioSource = AudioSourceType.SILENT
    private var mediaProjection: MediaProjection? = null
    private var projectionRequestInFlight = false

    private val playbackCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        projectionRequestInFlight = false
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            PlaybackCaptureService.start(this, result.resultCode, data)
        } else {
            settingsRepository.update(
                settingsRepository.get().copy(audioSource = AudioSourceType.AUTO_PULSE)
            )
            audioCaptureManager.start(AudioSourceType.AUTO_PULSE)
        }
    }

    private val overlayHideHandler = Handler(Looper.getMainLooper())
    private val audioLevelHandler = Handler(Looper.getMainLooper())
    private var overlayVisible = false
    private var frameCount = 0
    private var fpsLastTimeMs = 0L
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
    private lateinit var fpsText: TextView

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
                library = PresetLibrary.build(
                    bundledPresetDir = File(filesDir, "presets"),
                    appUserPresetDir = (
                        getExternalFilesDir("presets") ?: File(filesDir, "user_presets")
                    ).also { it.mkdirs() }
                ),
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
        hintText.text  = "▶ = Next  ◀ = Previous  ▲ = Favorite  ▼ = Favorites  Hold OK = Lock"
        root.addView(overlayView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = android.view.Gravity.BOTTOM })

        // FPS counter — top-right, always visible when enabled
        fpsText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xCCBB86FC.toInt())
            setShadowLayer(4f, 1f, 1f, 0xFF000000.toInt())
            visibility = View.GONE
        }
        val fpsPadding = (16 * resources.displayMetrics.density).toInt()
        root.addView(fpsText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.gravity = android.view.Gravity.TOP or android.view.Gravity.END
            it.topMargin = fpsPadding
            it.marginEnd = fpsPadding
        })

        setContentView(root)
        showOverlay()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            playbackCaptureJob = lifecycleScope.launch {
                PlaybackCaptureService.projectionFlow.collectLatest { projection ->
                    mediaProjection = projection
                    if (projection != null &&
                        settingsRepository.get().audioSource == AudioSourceType.SYSTEM_AUDIO
                    ) {
                        audioCaptureManager.startSystemAudio(projection)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val settings = settingsRepository.get()
        applySurfaceResolution(settings.renderWidth, settings.renderHeight)
        surfaceView.onResume()
        startAudio(settings.audioSource)
        // Always restart the manager after onPause(). Preset browser selection can set
        // a current preset before this activity starts, but the timer and beat listener
        // still need to be started.
        presetManager.start()
        val resolution = when {
            settings.renderWidth == 1280  -> RenderResolution.HD_720P
            settings.renderWidth == 1920  -> RenderResolution.FHD_1080P
            else                          -> RenderResolution.NATIVE
        }
        renderer.setRenderResolution(resolution)
        renderer.setTransitionDuration(settings.transitionDurationSeconds)
        beatDetector.bassThreshold = settings.bassThreshold

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

        // FPS counter
        fpsLastTimeMs = android.os.SystemClock.elapsedRealtime()
        frameCount = 0
        if (settings.showFps) {
            fpsText.visibility = View.VISIBLE
            renderer.setFrameCallback { onFrame() }
        } else {
            fpsText.visibility = View.GONE
            renderer.setFrameCallback(null)
        }
    }

    override fun onPause() {
        super.onPause()
        audioLevelHandler.removeCallbacksAndMessages(null)
        renderer.setFrameCallback(null)
        audioIndicatorJob?.cancel()
        audioIndicatorJob = null
        audioCaptureManager.stop()
        presetManager.stop()
        if (ownsInstances && isFinishing) {
            surfaceView.queueEvent { renderer.release() }
        }
        surfaceView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayHideHandler.removeCallbacksAndMessages(null)
        audioLevelHandler.removeCallbacksAndMessages(null)
        playbackCaptureJob?.cancel()
        playbackCaptureJob = null
        if (isFinishing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PlaybackCaptureService.stop(this)
        }
        if (ownsInstances) {
            presetManager.release()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!projectionRequestInFlight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PlaybackCaptureService.stop(this)
            mediaProjection = null
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        showOverlay()
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT  -> { presetManager.nextPreset(smooth = true); true }
            KeyEvent.KEYCODE_DPAD_LEFT   -> { presetManager.previousPreset(); true }
            KeyEvent.KEYCODE_DPAD_UP     -> { toggleFavorite(); true }
            KeyEvent.KEYCODE_DPAD_DOWN   -> { toggleFavoritesOnlyMode(); true }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER       -> {
                event?.startTracking()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                togglePresetLock()
                true
            }
            else -> super.onKeyLongPress(keyCode, event)
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
        val locked = if (presetManager.isPresetLocked()) "  •  Locked" else ""
        return favorite + preset.name + mode + locked
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

    private fun togglePresetLock() {
        val locked = presetManager.togglePresetLock()
        showOverlay()
        presetNameText.text = if (locked) {
            "Preset locked"
        } else {
            "Preset cycling resumed"
        }
    }

    private fun startAudio(sourceType: AudioSourceType) {
        if (sourceType != AudioSourceType.SYSTEM_AUDIO) {
            audioCaptureManager.start(sourceType)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            audioCaptureManager.start(AudioSourceType.AUTO_PULSE)
            return
        }
        mediaProjection?.let {
            audioCaptureManager.startSystemAudio(it)
            return
        }
        audioCaptureManager.start(AudioSourceType.AUTO_PULSE)
        if (!projectionRequestInFlight) {
            projectionRequestInFlight = true
            val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
            playbackCaptureLauncher.launch(manager.createScreenCaptureIntent())
        }
    }

    private fun updateAudioIndicator() {
        val level = (bridge.getBass() * 100f).toInt().coerceIn(0, 100)
        audioIndicatorText.text = when (activeAudioSource) {
            AudioSourceType.AUTO_PULSE -> "⚡ Auto Pulse ${level}%"
            AudioSourceType.MICROPHONE -> "🎤 Mic ${level}%"
            AudioSourceType.SYSTEM_AUDIO -> "♫ Device audio ${level}%"
            AudioSourceType.SILENT -> "🔇 Silent"
        }
    }

    private fun onFrame() {
        frameCount++
        val now = android.os.SystemClock.elapsedRealtime()
        val elapsed = now - fpsLastTimeMs
        if (elapsed >= 1000L) {
            val fps = frameCount * 1000f / elapsed
            frameCount = 0
            fpsLastTimeMs = now
            fpsText.post { fpsText.text = "%.0f fps".format(fps) }
        }
    }

    private fun applySurfaceResolution(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            surfaceView.holder.setFixedSize(width, height)
        } else {
            surfaceView.holder.setSizeFromLayout()
        }
    }
}
