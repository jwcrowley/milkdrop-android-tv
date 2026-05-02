package com.example.milkdrop.audio

import com.example.milkdrop.ProjectMBridge
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Detects bass and treble transients from the projectM audio analysis pipeline.
 *
 * After each [renderFrame] call, the caller should invoke [poll] to check for
 * beat events. Detected events are emitted on [beatFlow] for consumption by
 * [PresetManager].
 *
 * Bass and treble energy values are read from [ProjectMBridge.getBass] and
 * [ProjectMBridge.getTreble], which are computed as exponential moving averages
 * of the RMS energy in the low and high frequency portions of each PCM buffer.
 *
 * @param bridge         The [ProjectMBridge] instance to read energy values from.
 * @param bassThreshold  Energy level (0.0–1.0) above which a bass beat is detected.
 * @param trebleThreshold Energy level (0.0–1.0) above which a treble beat is detected.
 */
class BeatDetector(
    private val bridge: ProjectMBridge,
    var bassThreshold: Float = 0.8f,
    var trebleThreshold: Float = 0.7f
) {
    private val _beatFlow = MutableSharedFlow<BeatEvent>(extraBufferCapacity = 8)

    /**
     * Emits [BeatEvent] values when bass or treble transients are detected.
     * Consumed by [PresetManager] to trigger beat-driven preset transitions.
     */
    val beatFlow: SharedFlow<BeatEvent> = _beatFlow.asSharedFlow()

    // Track previous energy levels to detect rising edges (transients)
    private var prevBass = 0f
    private var prevTreble = 0f

    // Minimum time between beat events to avoid rapid-fire transitions (ms)
    private val minBeatIntervalMs = 500L
    private var lastBassEventMs = 0L
    private var lastTrebleEventMs = 0L

    /**
     * Poll the current audio energy levels and emit beat events if thresholds are exceeded.
     *
     * Must be called after each [ProjectMBridge.renderFrame] call, on the Render Thread.
     * Beat events are emitted asynchronously via [beatFlow].
     *
     * @return The detected [BeatEvent], or null if no beat was detected this frame.
     */
    fun poll(): BeatEvent? {
        val bass = bridge.getBass()
        val treble = bridge.getTreble()
        val now = System.currentTimeMillis()

        var event: BeatEvent? = null

        // Detect bass transient: energy crosses threshold on a rising edge
        if (bass >= bassThreshold && prevBass < bassThreshold &&
            now - lastBassEventMs >= minBeatIntervalMs) {
            lastBassEventMs = now
            event = BeatEvent.BASS
            _beatFlow.tryEmit(BeatEvent.BASS)
        }

        // Detect treble transient: energy crosses threshold on a rising edge
        if (treble >= trebleThreshold && prevTreble < trebleThreshold &&
            now - lastTrebleEventMs >= minBeatIntervalMs) {
            lastTrebleEventMs = now
            if (event == null) event = BeatEvent.TREBLE
            _beatFlow.tryEmit(BeatEvent.TREBLE)
        }

        prevBass = bass
        prevTreble = treble

        return event
    }

    /** Reset energy history and event timestamps. */
    fun reset() {
        prevBass = 0f
        prevTreble = 0f
        lastBassEventMs = 0L
        lastTrebleEventMs = 0L
    }
}

/** Beat event types emitted by [BeatDetector]. */
enum class BeatEvent {
    /** A bass transient was detected (low-frequency energy spike). */
    BASS,
    /** A treble transient was detected (high-frequency energy spike). */
    TREBLE
}
