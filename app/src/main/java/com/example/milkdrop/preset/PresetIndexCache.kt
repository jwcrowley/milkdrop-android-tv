package com.example.milkdrop.preset

import com.example.milkdrop.model.Preset
import com.example.milkdrop.model.PresetFormat
import java.io.File

/** Simple line-based cache for the bundled preset index. */
object PresetIndexCache {
    private const val HEADER_PREFIX = "milkdrop-preset-index\t"

    fun read(cacheFile: File, expectedVersion: String): List<Preset>? {
        if (!cacheFile.exists()) return null
        return try {
            cacheFile.bufferedReader().useLines { lines ->
                val iterator = lines.iterator()
                if (!iterator.hasNext()) return@useLines null
                if (iterator.next() != HEADER_PREFIX + expectedVersion) return@useLines null

                val presets = mutableListOf<Preset>()
                while (iterator.hasNext()) {
                    val parts = iterator.next().split('\t')
                    if (parts.size != 5) return@useLines null
                    val format = runCatching { PresetFormat.valueOf(parts[3]) }.getOrNull()
                        ?: return@useLines null
                    presets.add(
                        Preset(
                            id = parts[0],
                            name = parts[1],
                            filePath = parts[2],
                            format = format,
                            sizeBytes = parts[4].toLongOrNull() ?: return@useLines null,
                            isValid = true
                        )
                    )
                }
                presets
            }
        } catch (_: Exception) {
            null
        }
    }

    fun write(cacheFile: File, version: String, presets: List<Preset>) {
        cacheFile.parentFile?.mkdirs()
        val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
        tmp.bufferedWriter().use { writer ->
            writer.append(HEADER_PREFIX).append(version).append('\n')
            presets.forEach { preset ->
                writer.append(preset.id).append('\t')
                    .append(preset.name.sanitize()).append('\t')
                    .append(preset.filePath).append('\t')
                    .append(preset.format.name).append('\t')
                    .append(preset.sizeBytes.toString()).append('\n')
            }
        }
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        tmp.renameTo(cacheFile)
    }

    private fun String.sanitize(): String = replace('\t', ' ').replace('\n', ' ')
}
