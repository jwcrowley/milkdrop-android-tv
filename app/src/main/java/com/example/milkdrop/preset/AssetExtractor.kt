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
        val currentVersion = context.packageManager
            .getPackageInfo(context.packageName, 0).versionCode

        // Check if extraction is needed
        if (versionFile.exists()) {
            val storedVersion = versionFile.readText().trim().toIntOrNull()
            if (storedVersion == currentVersion && presetDir.exists()) {
                val count = countPresets(presetDir)
                if (count > 0) {
                    Log.i(TAG, "Presets already extracted (version $currentVersion, count=$count)")
                    return@withContext count
                }
            }
        }

        Log.i(TAG, "Extracting presets for version $currentVersion...")
        presetDir.mkdirs()

        var extractedCount = 0
        extractedCount = extractDirectory(PRESET_ASSET_DIR, presetDir)

        // Write version stamp
        try {
            versionFile.writeText(currentVersion.toString())
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
}
