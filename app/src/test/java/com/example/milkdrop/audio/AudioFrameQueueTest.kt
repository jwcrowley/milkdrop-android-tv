package com.example.milkdrop.audio

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AudioFrameQueueTest : StringSpec({
    "a full queue drops the oldest frame" {
        val queue = AudioFrameQueue(capacity = 2)
        val first = frame(1)
        val second = frame(2)
        val third = frame(3)

        queue.offer(first)
        queue.offer(second)
        queue.offer(third)

        queue.size() shouldBe 2
        queue.poll() shouldBe second
        queue.poll() shouldBe third
        queue.poll() shouldBe null
    }

    "clear removes all pending frames" {
        val queue = AudioFrameQueue()
        queue.offer(frame(1))

        queue.clear()

        queue.size() shouldBe 0
        queue.poll() shouldBe null
    }
})

private fun frame(value: Short) = AudioFrame(
    pcmData = shortArrayOf(value),
    sampleRate = 44_100,
    channelCount = 1,
    timestampNanos = value.toLong()
)
