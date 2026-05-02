package com.example.milkdrop.preset

import com.example.milkdrop.model.Preset

/**
 * Result of parsing a MilkDrop preset file.
 *
 * Used by [PresetParser] to communicate success or failure without throwing exceptions.
 */
sealed class ParseResult {
    /**
     * The preset file was successfully parsed.
     * @param preset The parsed [Preset] object.
     */
    data class Success(val preset: Preset) : ParseResult()

    /**
     * The preset file could not be parsed.
     * @param filePath    The path of the file that failed to parse.
     * @param errorMessage A human-readable description of the failure.
     * @param lineNumber  The line number where the error occurred, if known.
     */
    data class Failure(
        val filePath: String,
        val errorMessage: String,
        val lineNumber: Int? = null
    ) : ParseResult()
}
