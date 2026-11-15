package com.example.milkdrop.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.milkdrop.LauncherActivity
import com.example.milkdrop.R
import com.example.milkdrop.preset.AssetExtractor
import kotlinx.coroutines.launch

/**
 * Shown on every launch while preset extraction is checked/run.
 * extractPresetsIfNeeded() is fast (milliseconds) if already up to date,
 * or shows a progress message while extracting on first launch / after update.
 * After extraction, rebuilds the PresetLibrary so the manager has all presets.
 */
class SplashFragment : Fragment() {

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_splash, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressBar = view.findViewById(R.id.splash_progress)
        statusText  = view.findViewById(R.id.splash_status)

        statusText.text = getString(R.string.splash_loading)
        progressBar.isIndeterminate = false
        progressBar.max = AssetExtractor.EXPECTED_BUNDLED_PRESETS

        viewLifecycleOwner.lifecycleScope.launch {
            val extractor = AssetExtractor(requireContext())
            val count = extractor.extractPresetsIfNeeded { copied ->
                progressBar.post {
                    progressBar.progress = (
                        copied.toLong() * progressBar.max /
                            AssetExtractor.EXPECTED_BUNDLED_PRESETS
                        ).toInt().coerceIn(0, progressBar.max)
                }
            }
            progressBar.progress = progressBar.max
            statusText.text =
                resources.getQuantityString(R.plurals.splash_loaded, count, count)

            // The library starts empty (built off the main thread) so it must be
            // populated now that extraction is complete.
            if (LauncherActivity.isInitialized &&
                LauncherActivity.presetLibrary.bundledCount == 0
            ) {
                LauncherActivity.rebuildPresetLibrary()
            }

            (activity as? SplashCompleteListener)?.onSplashComplete(count)
        }
    }

    interface SplashCompleteListener {
        fun onSplashComplete(presetCount: Int)
    }
}
