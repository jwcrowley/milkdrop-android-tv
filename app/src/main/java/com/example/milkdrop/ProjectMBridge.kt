package com.example.milkdrop

/**
 * Kotlin wrapper around the native milkdrop_bridge shared library.
 *
 * Each method maps 1-to-1 to a JNI export in projectm_jni.cpp.
 * The [handle] value is an opaque key (jlong) returned by [create] and
 * passed back to every subsequent call so the native side can look up the
 * projectM instance in its internal handle map.
 */
class ProjectMBridge {

    /** Opaque handle to the native projectM instance. 0 means uninitialised. */
    @Volatile
    private var handle: Long = 0L

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Create a new projectM instance sized to [width] × [height] pixels.
     * [presetPath] is the filesystem path to the initial preset directory.
     * Stores the returned opaque handle internally.
     */
    fun create(width: Int, height: Int, presetPath: String) {
        handle = create(width, height, presetPath as Any)
    }

    private external fun create(width: Int, height: Int, presetPath: Any): Long

    /**
     * Reinitialise the OpenGL context after a surface size change or context
     * loss. Calls [projectm_set_window_size] on the stored handle.
     */
    fun reinitialize(width: Int, height: Int) = reinitialize(handle, width, height)

    private external fun reinitialize(handle: Long, width: Int, height: Int)

    /** Release all native resources. Must be called before the GL surface is destroyed. */
    fun destroy() {
        if (handle != 0L) {
            destroyNative(handle)
            handle = 0L
        }
    }

    private external fun destroyNative(handle: Long)

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /** Render one frame. Must be called on the GL thread. */
    fun renderFrame() = renderFrameNative(handle)

    private external fun renderFrameNative(handle: Long)

    // -------------------------------------------------------------------------
    // Audio
    // -------------------------------------------------------------------------

    /**
     * Feed raw PCM audio to projectM.
     * [pcmData] is interleaved 16-bit samples; [channels] is 1 (mono) or 2 (stereo).
     * Also updates the internal bass/treble energy metrics used by [getBass] and [getTreble].
     */
    fun feedAudio(pcmData: ShortArray, channels: Int, sampleRate: Int) =
        feedAudioNative(handle, pcmData, channels, sampleRate)

    private external fun feedAudioNative(
        handle: Long,
        pcmData: ShortArray,
        channels: Int,
        sampleRate: Int
    )

    // -------------------------------------------------------------------------
    // Preset management
    // -------------------------------------------------------------------------

    /**
     * Load a preset from [presetPath].
     * If [smooth] is true, a blend transition is applied.
     */
    fun loadPreset(presetPath: String, smooth: Boolean) =
        loadPresetNative(handle, presetPath, smooth)

    private external fun loadPresetNative(handle: Long, presetPath: String, smooth: Boolean)

    // -------------------------------------------------------------------------
    // Preset timing
    // -------------------------------------------------------------------------

    /**
     * Set the soft-cut (blend) transition duration in seconds.
     * Delegates to [projectm_set_soft_cut_duration].
     */
    fun setSoftCutDuration(seconds: Double) = setSoftCutDuration(handle, seconds)

    external fun setSoftCutDuration(handle: Long, seconds: Double)

    // -------------------------------------------------------------------------
    // Beat detection
    // -------------------------------------------------------------------------

    /**
     * Returns the current bass energy in the range [0.0, 1.0].
     * Computed as an exponential moving average of the RMS of the low-frequency
     * portion of each PCM buffer fed via [feedAudio].
     */
    fun getBass(): Float = getBassNative(handle)

    private external fun getBassNative(handle: Long): Float

    /**
     * Returns the current treble energy in the range [0.0, 1.0].
     * Computed as an exponential moving average of the RMS of the high-frequency
     * portion of each PCM buffer fed via [feedAudio].
     */
    fun getTreble(): Float = getTrebleNative(handle)

    private external fun getTrebleNative(handle: Long): Float

    // -------------------------------------------------------------------------
    // Companion — library loading
    // -------------------------------------------------------------------------

    companion object {
        init {
            System.loadLibrary("milkdrop_bridge")
        }
    }
}
