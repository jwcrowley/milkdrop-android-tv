package com.example.milkdrop.renderer

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

/**
 * GLSurfaceView configured for OpenGL ES 3.0 continuous rendering.
 *
 * Attach a [ProjectMRenderer] via [setRenderer] before the view is attached
 * to a window. Call [onResume] / [onPause] from the hosting Activity lifecycle.
 */
class VisualizerSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    init {
        // Request OpenGL ES 3.0 context
        setEGLContextClientVersion(3)
        // Render continuously (not on-demand) for smooth animation
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /**
     * Attach the projectM renderer. Must be called before the view is attached
     * to a window (i.e., before [Activity.setContentView] returns).
     */
    fun attachRenderer(renderer: ProjectMRenderer) {
        setRenderer(renderer)
    }
}
