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
        progressBar.isIndeterminate = true

        viewLifecycleOwner.lifecycleScope.launch {
            val extractor = AssetExtractor(requireContext())
            val count = extractor.extractPresetsIfNeeded()
            statusText.text = getString(R.string.splash_loaded, count)

            // Rebuild the preset library now that extraction is confirmed complete.
            // This ensures PresetManager shuffles from all extracted presets, not
            // whatever was on disk when initializeSingletons() ran at startup.
            if (LauncherActivity.isInitialized) {
                LauncherActivity.rebuildPresetLibrary()
            }

            (activity as? SplashCompleteListener)?.onSplashComplete(count)
        }
    }

    interface SplashCompleteListener {
        fun onSplashComplete(presetCount: Int)
    }
}
