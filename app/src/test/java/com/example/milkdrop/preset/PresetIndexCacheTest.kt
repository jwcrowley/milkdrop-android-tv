package com.example.milkdrop.preset

import com.example.milkdrop.model.Preset
import com.example.milkdrop.model.PresetFormat
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class PresetIndexCacheTest : StringSpec({
    "write and read preserve a preset index" {
        val directory = Files.createTempDirectory("preset-index-test").toFile()
        val cache = directory.resolve("index")
        val preset = Preset(
            id = "/presets/example.milk",
            name = "Example\tPreset",
            filePath = "/presets/example.milk",
            format = PresetFormat.MILK,
            sizeBytes = 42
        )

        try {
            PresetIndexCache.write(cache, "v1", listOf(preset))

            PresetIndexCache.read(cache, "v1") shouldBe listOf(
                preset.copy(name = "Example Preset")
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    "read rejects another index version" {
        val directory = Files.createTempDirectory("preset-index-test").toFile()
        val cache = directory.resolve("index")

        try {
            PresetIndexCache.write(cache, "v1", emptyList())

            PresetIndexCache.read(cache, "v2") shouldBe null
        } finally {
            directory.deleteRecursively()
        }
    }

    "read rejects a truncated entry" {
        val cache = Files.createTempFile("preset-index-test", ".txt").toFile()

        try {
            cache.writeText("milkdrop-preset-index\tv1\nmissing\tfields\n")

            PresetIndexCache.read(cache, "v1") shouldBe null
        } finally {
            cache.delete()
        }
    }
})
