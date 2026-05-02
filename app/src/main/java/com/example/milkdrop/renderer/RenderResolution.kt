package com.example.milkdrop.renderer

/**
 * Configurable render resolution options.
 *
 * [NATIVE] matches the GLSurfaceView dimensions (0 = use surface size).
 * [HALF_NATIVE] is 50% of native — used under memory pressure.
 * [HD_720P] and [FHD_1080P] are fixed resolutions.
 */
enum class RenderResolution(val width: Int, val height: Int) {
    NATIVE(0, 0),
    HD_720P(1280, 720),
    FHD_1080P(1920, 1080),
    HALF_NATIVE(-1, -1)  // -1 = 50% of current surface size, computed at runtime
}
