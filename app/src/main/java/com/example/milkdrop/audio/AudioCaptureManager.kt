package com.example.milkdrop.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the active [AudioSource] and routes audio frames to the [AudioFrameQueue].
 *
 * Selects the appropriate source based on the requested [AudioSourceType] and
 * runtime permission state. Switches to [SilentAudioSource] if RECORD_AUDIO
 * is denied.
 *
 * Lifecycle: call [start] from Activity.onResume(), [stop] from Activity.onPause().
 */
class AudioCaptureManager(
    private val context: Context,
    private val audioQueue: AudioFrameQueue
) {
    private val TAG = "AudioCaptureManager"

    private var activeSource: AudioSource? = null

    private val _activeSourceType = MutableStateFlow(AudioSourceType.SILENT)
    /** Observed by the UI to show the current audio source indicator. */
    val activeSourceFlow: StateFlow<AudioSourceType> = _activeSourceType.asStateFlow()

    /**
     * Start audio capture with the requested [sourceType].
     * Falls back to [SilentAudioSource] if the required permission is not granted.
     */
    fun start(sourceType: AudioSourceType = AudioSourceType.MICROPHONE) {
        stop()  // Stop any existing source first

        val source = when {
            sourceType == AudioSourceType.MICROPHONE && hasRecordPermission() -> {
                Log.i(TAG, "Starting microphone capture")
                _activeSourceType.value = AudioSourceType.MICROPHONE
                MicrophoneAudioSource()
            }
            sourceType == AudioSourceType.SYSTEM_AUDIO &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            hasRecordPermission() -> {
                // SystemAudioSource requires a MediaProjection token obtained via
                // the consent flow in the Activity. If not available, fall through to mic.
                Log.i(TAG, "System audio requested but MediaProjection not yet available; using microphone")
                _activeSourceType.value = AudioSourceType.MICROPHONE
                MicrophoneAudioSource()
            }
            else -> {
                if (!hasRecordPermission()) {
                    Log.w(TAG, "RECORD_AUDIO permission denied — using silent source")
                }
                _activeSourceType.value = AudioSourceType.SILENT
                SilentAudioSource()
            }
        }

        activeSource = source
        source.start { frame -> audioQueue.offer(frame) }
    }

    /**
     * Switch to [SystemAudioSource] using the provided [mediaProjection] token.
     * Only available on API 29+.
     */
    fun startSystemAudio(mediaProjection: android.media.projection.MediaProjection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "System audio capture requires API 29+")
            return
        }
        stop()
        val source = SystemAudioSource(mediaProjection) {
            // Fallback: MediaProjection was revoked — switch to microphone
            Log.w(TAG, "MediaProjection revoked — switching to microphone")
            start(AudioSourceType.MICROPHONE)
        }
        activeSource = source
        _activeSourceType.value = AudioSourceType.SYSTEM_AUDIO
        source.start { frame -> audioQueue.offer(frame) }
    }

    /** Stop the active audio source and clear the queue. */
    fun stop() {
        activeSource?.stop()
        activeSource = null
        audioQueue.clear()
    }

    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
