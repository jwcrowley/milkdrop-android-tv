package com.example.milkdrop.preset

import android.util.Log
import com.example.milkdrop.model.Preset
import com.example.milkdrop.model.PresetFormat
import java.io.File

/**
 * Indexes all available MilkDrop preset files from bundled and user-supplied sources.
 *
 * Indexing is fast — it only walks the filesystem and collects file paths.
 * No file content is read, no hashing, no JNI calls at index time.
 * This means 9,795 presets index in milliseconds instead of minutes.
 */
class PresetLibrary private constructor(
    private val allPresets: List<Preset>,
    val bundledCount: Int,
    val userCount: Int
) {
    val presets: List<Preset> = allPresets

    fun size(): Int = allPresets.size

    fun getById(id: String): Preset? = allPresets.firstOrNull { it.id == id }

    fun getByIndex(index: Int): Preset = allPresets[index]

    companion object {
        private const val TAG = "PresetLibrary"
        private const val INDEX_VERSION = "cream-9795-v1"
        private const val INDEX_FILE = ".preset_index"
        private const val EXPECTED_BUNDLED_PRESETS = 9_795

        fun build(
            bundledPresetDir: File,
            includeUserPresets: Boolean = true,
            appUserPresetDir: File? = null
        ): PresetLibrary {
            val bundled = loadBundledIndex(bundledPresetDir)
            Log.i(TAG, "Indexed ${bundled.size} bundled presets from ${bundledPresetDir.path}")

            val user = if (includeUserPresets) {
                appUserPresetDir?.let { userDir ->
                    val userPresets = scanDirectory(userDir)
                    Log.i(
                        TAG,
                        "Indexed ${userPresets.size} user presets from ${userDir.path}"
                    )
                    userPresets
                }.orEmpty()
            } else emptyList()

            return PresetLibrary(
                allPresets = bundled + user,
                bundledCount = bundled.size,
                userCount = user.size
            )
        }

        private fun scanDirectory(dir: File): List<Preset> {
            if (!dir.exists() || !dir.isDirectory) return emptyList()

            return dir.walkTopDown()
                .filter { file ->
                    file.isFile && (
                        file.name.endsWith(".milk", ignoreCase = true) ||
                        file.name.endsWith(".milk2", ignoreCase = true)
                    )
                }
                .map { file ->
                    val format = if (file.name.endsWith(".milk2", ignoreCase = true))
                        PresetFormat.MILK2 else PresetFormat.MILK
                    Preset(
                        // Use the absolute path as a stable ID — no SHA-256 needed
                        id = file.absolutePath,
                        name = file.nameWithoutExtension
                            .replace('_', ' ')
                            .replace('-', ' ')
                            .trim(),
                        filePath = file.absolutePath,
                        format = format,
                        sizeBytes = file.length(),
                        isValid = true
                    )
                }
                .sortedBy { it.name.lowercase() }
                .toList()
        }

        private fun loadBundledIndex(bundledPresetDir: File): List<Preset> {
            val cacheFile = File(bundledPresetDir.parentFile, INDEX_FILE)
            PresetIndexCache.read(cacheFile, INDEX_VERSION)?.let { cached ->
                if (cached.size == EXPECTED_BUNDLED_PRESETS) {
                    Log.i(TAG, "Loaded ${cached.size} bundled presets from index cache")
                    return cached
                }
                // Cache exists but has too few entries — stale/corrupt, rebuild it
                Log.w(TAG, "Index cache has only ${cached.size} entries, rebuilding")
                cacheFile.delete()
            }

            val scanned = scanDirectory(bundledPresetDir)
            if (scanned.isNotEmpty()) {
                PresetIndexCache.write(cacheFile, INDEX_VERSION, scanned)
            }
            return scanned
        }
    }
}
