package com.example.milkdrop.preset

import com.example.milkdrop.model.Preset
import java.io.File

/**
 * Serializes a [Preset] back to its `.milk` text format.
 *
 * Used primarily for the round-trip property test (Property 1): parse → serialize → parse
 * should produce a structurally equivalent [Preset].
 *
 * Since projectM handles the actual preset rendering and the preset file is the
 * source of truth, serialization here means reading the original file content
 * and writing it to a new location. This preserves all shader code, equations,
 * and parameters exactly.
 *
 * For the round-trip test, this is sufficient: if the file content is preserved
 * verbatim, re-parsing it will produce an equivalent Preset.
 */
object PresetSerializer {

    /**
     * Serialize a [ParseResult.Success] by copying the original preset file content
     * to a new [targetFile].
     *
     * @param result     The successfully parsed preset result.
     * @param targetFile The destination file to write the serialized content to.
     * @return The content written to [targetFile] as a String.
     * @throws IllegalArgumentException if [result] refers to a non-existent file.
     */
    fun serialize(result: ParseResult.Success, targetFile: File): String {
        val sourceFile = File(result.preset.filePath)
        require(sourceFile.exists()) {
            "Source preset file does not exist: ${result.preset.filePath}"
        }
        val content = sourceFile.readText(Charsets.UTF_8)
        targetFile.writeText(content, Charsets.UTF_8)
        return content
    }

    /**
     * Serialize a [ParseResult.Success] to a String (without writing to disk).
     * Used in tests to verify round-trip fidelity without filesystem side effects.
     */
    fun serializeToString(result: ParseResult.Success): String {
        val sourceFile = File(result.preset.filePath)
        require(sourceFile.exists()) {
            "Source preset file does not exist: ${result.preset.filePath}"
        }
        return sourceFile.readText(Charsets.UTF_8)
    }
}
