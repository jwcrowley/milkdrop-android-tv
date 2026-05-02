package com.example.milkdrop.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.milkdrop.R
import com.example.milkdrop.preset.AssetExtractor
import kotlinx.coroutines.launch

/**
 * Shown on first launch (or after an app update) while preset files are being
 * extracted from the APK assets to internal storage.
 *
 * Automatically navigates to [LauncherActivity]'s main content once extraction
 * completes. On subsequent launches, extraction is skipped and this fragment
 * is not shown.
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
        statusText = view.findViewById(R.id.splash_status)

        statusText.text = getString(R.string.splash_loading)
        progressBar.isIndeterminate = true

        viewLifecycleOwner.lifecycleScope.launch {
            val extractor = AssetExtractor(requireContext())
            val count = extractor.extractPresetsIfNeeded()
            statusText.text = getString(R.string.splash_loaded, count)
            // Notify the hosting activity that extraction is complete
            (activity as? SplashCompleteListener)?.onSplashComplete(count)
        }
    }

    /** Callback interface for the hosting Activity. */
    interface SplashCompleteListener {
        fun onSplashComplete(presetCount: Int)
    }
}
