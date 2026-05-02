package com.example.milkdrop.audio

import java.util.concurrent.ArrayBlockingQueue

/**
 * Lock-free ring buffer for passing [AudioFrame] objects from the Audio Thread
 * to the Render Thread.
 *
 * Capacity is fixed at 4 frames. When the queue is full, the oldest frame is
 * dropped to make room for the newest (producer never blocks).
 */
class AudioFrameQueue(private val capacity: Int = 4) {

    private val queue = ArrayBlockingQueue<AudioFrame>(capacity)

    /**
     * Enqueue a frame. If the queue is full, the oldest frame is discarded.
     * Called from the Audio Thread.
     */
    fun offer(frame: AudioFrame) {
        if (!queue.offer(frame)) {
            // Queue full — drop oldest frame and retry
            queue.poll()
            queue.offer(frame)
        }
    }

    /**
     * Dequeue the oldest frame, or return null if the queue is empty.
     * Called from the Render Thread.
     */
    fun poll(): AudioFrame? = queue.poll()

    /** Returns the current number of frames in the queue. */
    fun size(): Int = queue.size

    /** Clears all pending frames. */
    fun clear() = queue.clear()
}
