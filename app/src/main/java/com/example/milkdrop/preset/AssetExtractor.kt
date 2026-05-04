package com.example.milkdrop.preset

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Extracts bundled preset files from the APK assets to the app's internal storage.
 *
 * projectM's C++ API requires filesystem paths, not Android asset streams, so
 * presets must be copied to [Context.getFilesDir] before they can be loaded.
 *
 * Extraction is gated by a version-stamp marker file ([PRESET_VERSION_FILE]).
 * If the app version changes (e.g., after an update), presets are re-extracted.
 *
 * Run on [Dispatchers.IO] — never on the main thread.
 */
class AssetExtractor(private val context: Context) {

    companion object {
        private const val TAG = "AssetExtractor"
        private const val PRESET_ASSET_DIR = "presets"
        private const val PRESET_VERSION_FILE = ".preset_version"
        private const val PRESET_BUNDLE_VERSION = "cream-9795-v1"
        private const val PRESET_COUNT_FILE = ".preset_count"
        private const val MIN_EXPECTED_BUNDLED_PRESETS = 9_000
    }

    /** The directory where extracted presets are stored on the device filesystem. */
    fun getPresetDirectory(): File = File(context.filesDir, PRESET_ASSET_DIR)

    /**
     * Extract all preset files from assets if they haven't been extracted for
     * the current app version.
     *
     * @return The number of preset files successfully extracted (or already present).
     */
    suspend fun extractPresetsIfNeeded(): Int = withContext(Dispatchers.IO) {
        val presetDir = getPresetDirectory()
        val versionFile = File(context.filesDir, PRESET_VERSION_FILE)
        val countFile = File(context.filesDir, PRESET_COUNT_FILE)

        // Extraction is tied to the bundled preset corpus, not the app version.
        // This prevents every app update from recopying thousands of unchanged files.
        if (versionFile.exists()) {
            val storedVersion = versionFile.readText().trim()
            if (storedVersion == PRESET_BUNDLE_VERSION && presetDir.exists()) {
                val count = countFile.readTextOrNull()?.trim()?.toIntOrNull()
                    ?: countPresets(presetDir).also { countFile.writeText(it.toString()) }
                if (count >= MIN_EXPECTED_BUNDLED_PRESETS) {
                    Log.i(TAG, "Presets already extracted (bundle $PRESET_BUNDLE_VERSION, count=$count)")
                    return@withContext count
                }
            }
        }

        // Upgrade path from older app-version stamps: if the extracted corpus is
        // already present, adopt it without doing a full asset copy again.
        if (presetDir.exists()) {
            val existingCount = countFile.readTextOrNull()?.trim()?.toIntOrNull()
                ?: countPresets(presetDir).also { countFile.writeText(it.toString()) }
            if (existingCount >= MIN_EXPECTED_BUNDLED_PRESETS) {
                versionFile.writeText(PRESET_BUNDLE_VERSION)
                Log.i(TAG, "Adopted existing extracted presets (count=$existingCount)")
                return@withContext existingCount
            }
        }

        Log.i(TAG, "Extracting preset bundle $PRESET_BUNDLE_VERSION...")
        presetDir.mkdirs()

        val extractedCount = extractDirectory(PRESET_ASSET_DIR, presetDir)

        try {
            versionFile.writeText(PRESET_BUNDLE_VERSION)
            countFile.writeText(extractedCount.toString())
        } catch (e: IOException) {
            Log.w(TAG, "Failed to write version stamp: ${e.message}")
        }

        Log.i(TAG, "Extracted $extractedCount preset files")
        extractedCount
    }

    private fun extractDirectory(assetPath: String, targetDir: File): Int {
        var count = 0
        val assets = try {
            context.assets.list(assetPath) ?: return 0
        } catch (e: IOException) {
            Log.e(TAG, "Failed to list assets at $assetPath: ${e.message}")
            return 0
        }

        for (asset in assets) {
            val assetFilePath = "$assetPath/$asset"
            val targetFile = File(targetDir, asset)

            // Check if it's a directory by trying to list it
            val subAssets = try { context.assets.list(assetFilePath) } catch (e: IOException) { null }
            if (subAssets != null && subAssets.isNotEmpty()) {
                // It's a directory — recurse
                targetFile.mkdirs()
                count += extractDirectory(assetFilePath, targetFile)
            } else {
                // It's a file — copy it
                if (asset.endsWith(".milk", ignoreCase = true) ||
                    asset.endsWith(".milk2", ignoreCase = true)) {
                    count += copyAsset(assetFilePath, targetFile)
                }
            }
        }
        return count
    }

    private fun copyAsset(assetPath: String, targetFile: File): Int {
        return try {
            context.assets.open(assetPath).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            1
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset $assetPath: ${e.message}")
            0
        }
    }

    private fun countPresets(dir: File): Int {
        return dir.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".milk", true) || it.name.endsWith(".milk2", true)) }
            .count()
    }

    private fun File.readTextOrNull(): String? = try {
        if (exists()) readText() else null
    } catch (_: IOException) {
        null
    }
}
