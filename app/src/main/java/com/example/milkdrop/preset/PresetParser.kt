package com.example.milkdrop.preset

import android.util.Log
import com.example.milkdrop.ProjectMBridge
import com.example.milkdrop.model.Preset
import com.example.milkdrop.model.PresetFormat
import java.io.File
import java.security.MessageDigest

/**
 * Parses MilkDrop preset files into [Preset] objects.
 *
 * Delegates file validation to [ProjectMBridge.parsePreset] (which checks for
 * required MilkDrop header markers). The parser also computes the preset's
 * SHA-256 ID and extracts the display name from the filename.
 *
 * This class is safe to call from any thread. It does not hold state.
 */
class PresetParser(private val bridge: ProjectMBridge) {

    companion object {
        private const val TAG = "PresetParser"
        private const val MAX_FILE_SIZE_BYTES = 512 * 1024L  // 512 KB
    }

    /**
     * Parse a preset file at [filePath].
     *
     * @return [ParseResult.Success] with the parsed [Preset], or
     *         [ParseResult.Failure] with a descriptive error message.
     *         Never throws an uncaught exception.
     */
    fun parse(filePath: String): ParseResult {
        return try {
            parseInternal(filePath)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing $filePath: ${e.message}", e)
            ParseResult.Failure(
                filePath = filePath,
                errorMessage = "Unexpected error: ${e.message ?: "unknown"}"
            )
        }
    }

    private fun parseInternal(filePath: String): ParseResult {
        val file = File(filePath)

        if (!file.exists()) {
            return ParseResult.Failure(filePath, "File not found: $filePath")
        }

        if (!file.isFile) {
            return ParseResult.Failure(filePath, "Path is not a file: $filePath")
        }

        if (file.length() > MAX_FILE_SIZE_BYTES) {
            return ParseResult.Failure(filePath, "File too large (${file.length()} bytes, max 512 KB)")
        }

        // Determine format from extension
        val format = when {
            filePath.endsWith(".milk2", ignoreCase = true) -> PresetFormat.MILK2
            filePath.endsWith(".milk", ignoreCase = true) -> PresetFormat.MILK
            else -> return ParseResult.Failure(filePath, "Unsupported file extension (expected .milk or .milk2)")
        }

        // Delegate validation to the JNI bridge (checks for required header markers)
        val validationResult = bridge.parsePreset(filePath)
        if (validationResult.startsWith("ERROR:")) {
            val errorMessage = validationResult.removePrefix("ERROR:").trim()
            return ParseResult.Failure(filePath, errorMessage)
        }

        // Compute SHA-256 ID from file content
        val id = computeSha256(file)

        // Extract display name from filename (strip extension)
        val name = file.nameWithoutExtension
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()

        val preset = Preset(
            id = id,
            name = name,
            filePath = filePath,
            format = format,
            sizeBytes = file.length(),
            isValid = true,
            errorMessage = null
        )

        Log.d(TAG, "Parsed preset: $name ($format, ${file.length()} bytes)")
        return ParseResult.Success(preset)
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
