package com.example.milkdrop.renderer

import android.opengl.GLSurfaceView
import com.example.milkdrop.ProjectMBridge
import com.example.milkdrop.audio.BeatDetector
import com.example.milkdrop.audio.AudioFrameQueue
import com.example.milkdrop.model.Preset
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView.Renderer implementation that drives the projectM render loop.
 *
 * Threading: All GL calls happen on the Render Thread (managed by GLSurfaceView).
 * Audio frames are dequeued from [audioQueue] on the Render Thread.
 * Settings changes are posted via [setTransitionDuration] / [loadPreset] which
 * are thread-safe (they update @Volatile fields read on the Render Thread).
 */
class ProjectMRenderer(
    private val bridge: ProjectMBridge,
    private val audioQueue: AudioFrameQueue,
    private val presetDirectory: String
) : GLSurfaceView.Renderer {

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private val contextLost = AtomicBoolean(false)

    // Pending preset load — set from any thread, consumed on Render Thread
    @Volatile private var pendingPreset: Preset? = null
    @Volatile private var pendingSmooth: Boolean = true
    @Volatile private var transitionDuration: Float = 3f
    @Volatile private var beatDetector: BeatDetector? = null
    @Volatile private var inTransition: Boolean = false
    private var transitionFrameCount: Int = 0

    // Current render resolution setting
    @Volatile var renderResolution: RenderResolution = RenderResolution.NATIVE
        private set

    // -------------------------------------------------------------------------
    // GLSurfaceView.Renderer callbacks (called on Render Thread)
    // -------------------------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val (w, h) = resolveResolution()
        bridge.create(w.coerceAtLeast(1), h.coerceAtLeast(1), presetDirectory)
        bridge.setSoftCutDuration(transitionDuration.toDouble())
        contextLost.set(false)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        val (w, h) = resolveResolution()
        bridge.reinitialize(w, h)
        if (contextLost.get()) {
            contextLost.set(false)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        // Consume the latest audio frame (non-blocking)
        val frame = audioQueue.poll()
        if (frame != null) {
            bridge.feedAudio(frame.pcmData, frame.channelCount)
        }

        // Apply any pending preset load — drop to half-res during transition
        val preset = pendingPreset
        if (preset != null) {
            pendingPreset = null
            bridge.loadPreset(preset.filePath, pendingSmooth)
            if (pendingSmooth) {
                // Drop resolution for the duration of the blend transition
                inTransition = true
                transitionFrameCount = (transitionDuration * 30f).toInt().coerceAtLeast(30)
                val (w, h) = resolveTransitionResolution()
                bridge.reinitialize(w, h)
            }
        }

        // Restore full resolution once transition frames are exhausted
        if (inTransition) {
            transitionFrameCount--
            if (transitionFrameCount <= 0) {
                inTransition = false
                val (w, h) = resolveResolution()
                bridge.reinitialize(w, h)
            }
        }

        bridge.renderFrame()
        beatDetector?.poll()
    }

    // -------------------------------------------------------------------------
    // Thread-safe control API (called from UI/Main thread)
    // -------------------------------------------------------------------------

    /** Load a preset on the next render frame. Thread-safe. */
    fun loadPreset(preset: Preset, smooth: Boolean = true) {
        pendingSmooth = smooth
        pendingPreset = preset
    }

    /** Update the blend transition duration. Thread-safe. */
    fun setTransitionDuration(seconds: Float) {
        transitionDuration = seconds
        bridge.setSoftCutDuration(seconds.toDouble())
    }

    fun setBeatDetector(detector: BeatDetector) {
        beatDetector = detector
    }

    /**
     * Change the render resolution. Thread-safe.
     * The new resolution takes effect on the next [onSurfaceChanged] or
     * immediately via [bridge.reinitialize] if the surface is already known.
     */
    fun setRenderResolution(resolution: RenderResolution) {
        renderResolution = resolution
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            val (w, h) = resolveResolution()
            bridge.reinitialize(w, h)
        }
    }

    /** Called when the EGL surface is destroyed (app backgrounded). */
    fun onContextLost() {
        contextLost.set(true)
        bridge.destroy()
    }

    /** Release all resources. Call from the Render Thread or after it has stopped. */
    fun release() {
        bridge.destroy()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Compute the actual pixel dimensions for the current [renderResolution]
     * and surface size.
     */
    private fun resolveResolution(): Pair<Int, Int> {
        return when (renderResolution) {
            RenderResolution.NATIVE     -> Pair(surfaceWidth, surfaceHeight)
            RenderResolution.HALF_NATIVE -> Pair(
                (surfaceWidth / 2).coerceAtLeast(1),
                (surfaceHeight / 2).coerceAtLeast(1)
            )
            RenderResolution.HD_720P    -> Pair(1280, 720)
            RenderResolution.FHD_1080P  -> Pair(1920, 1080)
        }
    }

    /** Half the normal resolution — used during blend transitions to keep framerate smooth. */
    private fun resolveTransitionResolution(): Pair<Int, Int> {
        val (w, h) = resolveResolution()
        return Pair((w / 2).coerceAtLeast(320), (h / 2).coerceAtLeast(180))
    }
}
