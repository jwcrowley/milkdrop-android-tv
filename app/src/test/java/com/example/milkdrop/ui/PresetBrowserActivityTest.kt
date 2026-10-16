package com.example.milkdrop.ui

import com.example.milkdrop.model.Preset
import com.example.milkdrop.model.PresetFormat
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PresetBrowserActivityTest : StringSpec({
    "bundled presets use the collection category instead of its subcategory" {
        val preset = preset(
            "/data/user/0/com.example.milkdrop/files/presets/" +
                "projectm-cream/Dancer/Spinner/example.milk"
        )

        categoryForPreset(preset) shouldBe "Dancer"
    }

    "external presets use the user category" {
        categoryForPreset(preset("/sdcard/MilkDrop/presets/example.milk")) shouldBe
            "User Presets"
    }

    "unrecognized paths use the fallback category" {
        categoryForPreset(preset("/tmp/example.milk")) shouldBe "Other"
    }
})

private fun preset(path: String) = Preset(
    id = path,
    name = "Example",
    filePath = path,
    format = PresetFormat.MILK,
    sizeBytes = 1
)
