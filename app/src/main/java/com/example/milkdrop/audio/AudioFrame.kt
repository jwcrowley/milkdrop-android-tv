package com.example.milkdrop.audio

/**
 * A single captured audio buffer.
 *
 * @param pcmData       Interleaved 16-bit PCM samples.
 * @param sampleRate    Capture sample rate in Hz (44100 or 48000).
 * @param channelCount  Number of channels: 1 (mono) or 2 (stereo).
 * @param timestampNanos System.nanoTime() at the time of capture.
 */
data class AudioFrame(
    val pcmData: ShortArray,
    val sampleRate: Int,
    val channelCount: Int,
    val timestampNanos: Long = System.nanoTime()
) {
    // ShortArray doesn't implement structural equality by default
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return sampleRate == other.sampleRate &&
               channelCount == other.channelCount &&
               timestampNanos == other.timestampNanos &&
               pcmData.contentEquals(other.pcmData)
    }

    override fun hashCode(): Int {
        var result = pcmData.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channelCount
        result = 31 * result + timestampNanos.hashCode()
        return result
    }
}
