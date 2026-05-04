package com.example.milkdrop.preset

import android.util.Log
import com.example.milkdrop.audio.BeatDetector
import com.example.milkdrop.audio.BeatEvent
import com.example.milkdrop.model.Preset
import com.example.milkdrop.renderer.ProjectMRenderer
import com.example.milkdrop.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/**
 * Manages the preset lifecycle: indexing, shuffling, cycling, history, and transitions.
 *
 * ## Shuffle algorithm
 * Fisher-Yates shuffle over the full preset index. When the shuffled list is
 * exhausted, it is reshuffled before repeating — guaranteeing no preset repeats
 * until all presets in the library have been shown.
 *
 * ## History
 * A [ArrayDeque] capped at [MAX_HISTORY] entries. [nextPreset] pushes to the front;
 * [previousPreset] pops from the front and pushes the current preset back.
 *
 * ## Beat-driven transitions
 * When [AppSettings.beatDrivenTransitions] is true, [BeatDetector.beatFlow] is
 * collected and [nextPreset] is triggered on each [BeatEvent.BASS].
 */
class PresetManager(
    private var library: PresetLibrary,
    private val renderer: ProjectMRenderer,
    private val beatDetector: BeatDetector,
    private val favoritesRepository: PresetFavoritesRepository,
    private val settingsFlow: StateFlow<AppSettings>
) {
    companion object {
        private const val TAG = "PresetManager"
        private const val MAX_HISTORY = 10
        private const val BEAT_TRANSITION_COOLDOWN_MS = 8_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Shuffled index list — rebuilt when exhausted
    private val shuffledIndices = mutableListOf<Int>()
    private var shufflePosition = 0
    private var favoritesOnlyMode = false

    // History deque: most recent preset at the front
    private val history = ArrayDeque<Preset>(MAX_HISTORY + 1)

    // Currently displayed preset
    private var currentPreset: Preset? = null
    private var presetLocked = false
    private var lastBeatTransitionAtMs = 0L

    // Coroutine jobs
    private var cycleJob: Job? = null
    private var beatJob: Job? = null

    init {
        if (library.size() > 0) {
            rebuildShuffle()
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Start automatic preset cycling and beat detection. */
    fun start() {
        if (library.size() == 0) {
            Log.w(TAG, "PresetLibrary is empty — nothing to play")
            return
        }

        // Load the first preset immediately
        if (currentPreset == null) {
            nextPreset(smooth = false)
        }

        startCycleTimer()
        startBeatListener()
    }

    /** Stop automatic cycling and beat detection. */
    fun stop() {
        cycleJob?.cancel()
        cycleJob = null
        beatJob?.cancel()
        beatJob = null
    }

    /** Advance to the next preset. Thread-safe (posts to Main dispatcher). */
    fun nextPreset(smooth: Boolean = true) {
        if (library.size() == 0) return

        // Push current preset to history before advancing
        currentPreset?.let { pushHistory(it) }

        // Advance shuffle position
        if (shufflePosition >= shuffledIndices.size) {
            rebuildShuffle()
        }
        val nextIndex = shuffledIndices[shufflePosition++]
        val next = library.getByIndex(nextIndex)

        currentPreset = next
        renderer.loadPreset(next, smooth)
        Log.d(TAG, "Next preset: ${next.name} (smooth=$smooth)")

        // Restart the cycle timer only when automatic cycling is not pinned.
        if (smooth && !presetLocked) startCycleTimer()
    }

    /** Return to the previously displayed preset. */
    fun previousPreset() {
        if (history.isEmpty()) return

        // Push current back to the front of the shuffle (so it's not lost)
        currentPreset?.let { current ->
            // Insert current at the beginning of the remaining shuffle
            val currentIndex = library.presets.indexOf(current)
            if (currentIndex >= 0) {
                shuffledIndices.add(shufflePosition.coerceAtMost(shuffledIndices.size), currentIndex)
            }
        }

        val previous = history.pollFirst()
        currentPreset = previous
        renderer.loadPreset(previous, smooth = true)
        Log.d(TAG, "Previous preset: ${previous.name}")

        if (!presetLocked) startCycleTimer()
    }

    /** Load a specific preset immediately. */
    fun loadPreset(preset: Preset) {
        currentPreset?.let { pushHistory(it) }
        currentPreset = preset
        renderer.loadPreset(preset, smooth = true)
        if (!presetLocked) startCycleTimer()
        Log.d(TAG, "Loaded specific preset: ${preset.name}")
    }

    /** Returns the currently displayed preset, or null if none has been loaded yet. */
    fun getCurrentPreset(): Preset? = currentPreset

    fun toggleFavorite(preset: Preset): Boolean {
        val isFavorite = favoritesRepository.toggleFavorite(preset)
        if (favoritesOnlyMode && !isFavorite && favoritePresets().isEmpty()) {
            favoritesOnlyMode = false
        }
        rebuildShuffle()
        return isFavorite
    }

    fun isFavoritesOnlyMode(): Boolean = favoritesOnlyMode

    fun isPresetLocked(): Boolean = presetLocked

    fun togglePresetLock(): Boolean {
        presetLocked = !presetLocked
        if (presetLocked) {
            cycleJob?.cancel()
            cycleJob = null
        } else {
            startCycleTimer()
        }
        return presetLocked
    }

    fun toggleFavoritesOnlyMode(): Boolean {
        favoritesOnlyMode = !favoritesOnlyMode && favoritePresets().isNotEmpty()
        rebuildShuffle()
        return favoritesOnlyMode
    }

    /**
     * Returns the history of recently displayed presets, most recent first.
     * Maximum [MAX_HISTORY] entries.
     */
    fun getHistory(): List<Preset> = history.toList()

    /** Release all coroutine resources. Call when the activity is destroyed. */
    fun release() {
        stop()
        scope.cancel()
    }

    /** Rebuild the shuffle from a new library (called after preset extraction completes). */
    fun rebuildLibrary(newLibrary: PresetLibrary) {
        library = newLibrary
        favoritesRepository.pruneToLibrary(library)
        if (library.size() > 0) {
            rebuildShuffle()
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun rebuildShuffle() {
        shuffledIndices.clear()
        shuffledIndices.addAll(eligiblePresetIndices().toMutableList().also { it.shuffle() })
        shufflePosition = 0
        Log.d(TAG, "Rebuilt shuffle for ${shuffledIndices.size} presets")
    }

    private fun eligiblePresetIndices(): List<Int> {
        if (!favoritesOnlyMode) return (0 until library.size()).toList()
        val favoriteIds = favoritesRepository.getFavoriteIds()
        val indices = library.presets.mapIndexedNotNull { index, preset ->
            if (preset.id in favoriteIds) index else null
        }
        if (indices.isEmpty()) {
            favoritesOnlyMode = false
            return (0 until library.size()).toList()
        }
        return indices
    }

    private fun favoritePresets(): List<Preset> {
        val favoriteIds = favoritesRepository.getFavoriteIds()
        return library.presets.filter { it.id in favoriteIds }
    }

    private fun pushHistory(preset: Preset) {
        history.addFirst(preset)
        while (history.size > MAX_HISTORY) {
            history.pollLast()
        }
    }

    private fun startCycleTimer() {
        cycleJob?.cancel()
        if (presetLocked) return
        val settings = settingsFlow.value
        val intervalMs = settings.cycleIntervalSeconds * 1000L
        cycleJob = scope.launch {
            delay(intervalMs)
            nextPreset(smooth = true)
        }
    }

    private fun startBeatListener() {
        beatJob?.cancel()
        beatJob = scope.launch {
            beatDetector.beatFlow.collect { event ->
                val settings = settingsFlow.value
                val now = android.os.SystemClock.elapsedRealtime()
                val cooledDown = now - lastBeatTransitionAtMs >= BEAT_TRANSITION_COOLDOWN_MS
                if (
                    settings.beatDrivenTransitions &&
                    !presetLocked &&
                    event == BeatEvent.BASS &&
                    cooledDown
                ) {
                    lastBeatTransitionAtMs = now
                    Log.d(TAG, "Beat-driven transition triggered")
                    nextPreset(smooth = true)
                }
            }
        }
    }
}
