package com.example.milkdrop.preset

import android.content.Context
import com.example.milkdrop.model.Preset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists the user's favorite preset IDs. */
class PresetFavoritesRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "milkdrop_favorites"
        private const val KEY_FAVORITE_IDS = "favorite_preset_ids"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _favoriteIdsFlow = MutableStateFlow(loadFavoriteIds())

    val favoriteIdsFlow: StateFlow<Set<String>> = _favoriteIdsFlow.asStateFlow()

    fun getFavoriteIds(): Set<String> = _favoriteIdsFlow.value

    fun isFavorite(preset: Preset): Boolean = preset.id in _favoriteIdsFlow.value

    fun toggleFavorite(preset: Preset): Boolean {
        val updated = _favoriteIdsFlow.value.toMutableSet()
        val isFavorite = if (preset.id in updated) {
            updated.remove(preset.id)
            false
        } else {
            updated.add(preset.id)
            true
        }
        saveFavoriteIds(updated)
        _favoriteIdsFlow.value = updated
        return isFavorite
    }

    fun pruneToLibrary(library: PresetLibrary) {
        val validIds = library.presets.mapTo(mutableSetOf()) { it.id }
        val pruned = _favoriteIdsFlow.value.filterTo(mutableSetOf()) { it in validIds }
        if (pruned.size != _favoriteIdsFlow.value.size) {
            saveFavoriteIds(pruned)
            _favoriteIdsFlow.value = pruned
        }
    }

    private fun loadFavoriteIds(): Set<String> =
        prefs.getStringSet(KEY_FAVORITE_IDS, emptySet()).orEmpty().toSet()

    private fun saveFavoriteIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITE_IDS, ids).apply()
    }
}
