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

        view.findViewById<Button>(R.id.btn_crash_log).setOnClickListener {
            val log = com.example.milkdrop.CrashLogger.getLog(requireContext())
            // Show in a scrollable dialog
            val tv = android.widget.TextView(requireContext()).apply {
                text = if (log.length > 3000) log.takeLast(3000) else log
                textSize = 14f
                setPadding(32, 32, 32, 32)
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF111111.toInt())
            }
            val scroll = android.widget.ScrollView(requireContext()).apply {
                addView(tv)
            }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Crash Log")
                .setView(scroll)
                .setPositiveButton("Clear") { _, _ ->
                    com.example.milkdrop.CrashLogger.clear(requireContext())
                }
                .setNegativeButton("Close", null)
                .show()
        }

        // Focus the first button automatically so D-pad works immediately
        view.findViewById<Button>(R.id.btn_start_visualizer).requestFocus()
    }
}
