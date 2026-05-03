package com.example.milkdrop.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.milkdrop.R
import com.example.milkdrop.VisualizerActivity

/**
 * Simple main menu fragment — three D-pad-navigable buttons.
 * Uses a plain Fragment + LinearLayout to avoid Leanback theme dependencies.
 */
class MainFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_main_menu, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btn_start_visualizer).setOnClickListener {
            startActivity(Intent(requireContext(), VisualizerActivity::class.java))
        }

        view.findViewById<Button>(R.id.btn_browse_presets).setOnClickListener {
            startActivity(Intent(requireContext(), PresetBrowserActivity::class.java))
        }

        view.findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // Focus the first button automatically so D-pad works immediately
        view.findViewById<Button>(R.id.btn_start_visualizer).requestFocus()
    }
}
