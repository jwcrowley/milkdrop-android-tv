package com.example.milkdrop.preset

import android.os.Environment
import android.util.Log
import com.example.milkdrop.model.Preset
import com.example.milkdrop.model.PresetFormat
import java.io.File

/**
 * Indexes all available MilkDrop preset files from bundled and user-supplied sources.
 *
 * Bundled presets are extracted to [bundledPresetDir] by [AssetExtractor] before
 * this class is constructed. User presets are optionally loaded from
 * `/sdcard/MilkDrop/presets/` if the directory exists and is readable.
 *
 * Construction is synchronous and should be done on a background thread.
 */
class PresetLibrary private constructor(
    private val allPresets: List<Preset>,
    val bundledCount: Int,
    val userCount: Int
) {
    /** All valid presets (bundled + user), in the order they were indexed. */
    val presets: List<Preset> = allPresets

    /** Total number of presets (bundled + user). */
    fun size(): Int = allPresets.size

    /** Look up a preset by its SHA-256 ID. */
    fun getById(id: String): Preset? = allPresets.firstOrNull { it.id == id }

    /** Look up a preset by its index in [presets]. */
    fun getByIndex(index: Int): Preset = allPresets[index]

    companion object {
        private const val TAG = "PresetLibrary"
        private const val USER_PRESET_DIR = "MilkDrop/presets"

        /**
         * Build a [PresetLibrary] by scanning [bundledPresetDir] and optionally
         * the user preset directory on external storage.
         *
         * Invalid or unreadable files are skipped and logged.
         *
         * @param bundledPresetDir  The directory containing extracted bundled presets.
         * @param parser            The [PresetParser] used to validate each file.
         * @param includeUserPresets Whether to scan the external storage user preset directory.
         */
        fun build(
            bundledPresetDir: File,
            parser: PresetParser,
            includeUserPresets: Boolean = true
        ): PresetLibrary {
            val bundled = scanDirectory(bundledPresetDir, parser)
            Log.i(TAG, "Indexed ${bundled.size} bundled presets from ${bundledPresetDir.path}")

            val user = if (includeUserPresets) {
                val userDir = File(
                    Environment.getExternalStorageDirectory(),
                    USER_PRESET_DIR
                )
                if (userDir.exists() && userDir.isDirectory) {
                    val userPresets = scanDirectory(userDir, parser)
                    Log.i(TAG, "Indexed ${userPresets.size} user presets from ${userDir.path}")
                    userPresets
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }

            return PresetLibrary(
                allPresets = bundled + user,
                bundledCount = bundled.size,
                userCount = user.size
            )
        }

        private fun scanDirectory(dir: File, parser: PresetParser): List<Preset> {
            if (!dir.exists() || !dir.isDirectory) return emptyList()

            return dir.walkTopDown()
                .filter { file ->
                    file.isFile && (
                        file.name.endsWith(".milk", ignoreCase = true) ||
                        file.name.endsWith(".milk2", ignoreCase = true)
                    )
                }
                .mapNotNull { file ->
                    when (val result = parser.parse(file.absolutePath)) {
                        is ParseResult.Success -> result.preset
                        is ParseResult.Failure -> {
                            Log.w(TAG, "Skipping invalid preset ${file.name}: ${result.errorMessage}")
                            null
                        }
                    }
                }
                .sortedBy { it.name.lowercase() }
                .toList()
        }
    }
}
