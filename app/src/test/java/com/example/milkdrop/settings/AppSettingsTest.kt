package com.example.milkdrop.settings

import com.example.milkdrop.audio.AudioSourceType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AppSettingsTest : StringSpec({
    "defaults favor playback capture and a transition-safe render size" {
        val settings = AppSettings()

        settings.audioSource shouldBe AudioSourceType.SYSTEM_AUDIO
        settings.renderWidth shouldBe 1280
        settings.renderHeight shouldBe 720
    }
})
