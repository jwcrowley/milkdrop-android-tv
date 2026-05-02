package com.example.milkdrop.model

/**
 * Represents a single MilkDrop preset file.
 *
 * @param id           SHA-256 of the file content (stable across renames).
 * @param name         Display name (filename without extension).
 * @param filePath     Absolute path on the device filesystem.
 * @param format       File format: [PresetFormat.MILK] or [PresetFormat.MILK2].
 * @param sizeBytes    File size in bytes.
 * @param isValid      True if the preset passed pre-validation.
 * @param errorMessage Non-null if [isValid] is false; describes the parse error.
 */
data class Preset(
    val id: String,
    val name: String,
    val filePath: String,
    val format: PresetFormat,
    val sizeBytes: Long,
    val isValid: Boolean = true,
    val errorMessage: String? = null
)

enum class PresetFormat {
    MILK,   // MilkDrop 2 .milk format
    MILK2   // MilkDrop3 .milk2 double-preset format
}
