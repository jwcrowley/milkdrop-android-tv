package com.example.milkdrop.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.milkdrop.R
import com.example.milkdrop.VisualizerActivity

/**
 * Main menu fragment with a cinematic TV-optimized layout.
 * D-pad navigable — no touchscreen required.
 */
class MainFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_main_menu, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_start_visualizer).apply {
            setOnClickListener {
                startActivity(Intent(requireContext(), VisualizerActivity::class.java))
            }
            requestFocus()
        }

        view.findViewById<View>(R.id.btn_browse_presets).setOnClickListener {
            startActivity(Intent(requireContext(), PresetBrowserActivity::class.java))
        }

        view.findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }
}
