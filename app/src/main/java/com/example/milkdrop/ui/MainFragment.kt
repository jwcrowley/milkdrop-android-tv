package com.example.milkdrop.ui

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import android.view.ViewGroup
import android.widget.TextView
import com.example.milkdrop.R
import com.example.milkdrop.VisualizerActivity

/**
 * Main menu fragment shown on the Android TV home screen.
 *
 * Displays three rows navigable entirely by D-pad:
 *  1. "Start Visualizer" — launches [VisualizerActivity]
 *  2. "Browse Presets"   — navigates to [PresetBrowserFragment]
 *  3. "Settings"         — navigates to [SettingsFragment]
 *
 * All items are reachable within 2 D-pad presses from the launcher.
 */
class MainFragment : BrowseSupportFragment() {

    /** Simple data class representing a menu card. */
    private data class MenuCard(val title: String, val description: String)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        title = getString(R.string.app_name)
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false

        setupRows()
        setupClickListener()
    }

    // -------------------------------------------------------------------------
    // Row setup
    // -------------------------------------------------------------------------

    private fun setupRows() {
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val cardPresenter = MenuCardPresenter()

        // Row 1 — Start Visualizer
        val visualizerAdapter = ArrayObjectAdapter(cardPresenter)
        visualizerAdapter.add(
            MenuCard(
                title = getString(R.string.start_visualizer),
                description = getString(R.string.start_visualizer_desc)
            )
        )
        rowsAdapter.add(ListRow(HeaderItem(0, getString(R.string.start_visualizer)), visualizerAdapter))

        // Row 2 — Browse Presets
        val presetsAdapter = ArrayObjectAdapter(cardPresenter)
        presetsAdapter.add(
            MenuCard(
                title = getString(R.string.browse_presets),
                description = getString(R.string.browse_presets_desc)
            )
        )
        rowsAdapter.add(ListRow(HeaderItem(1, getString(R.string.browse_presets)), presetsAdapter))

        // Row 3 — Settings
        val settingsAdapter = ArrayObjectAdapter(cardPresenter)
        settingsAdapter.add(
            MenuCard(
                title = getString(R.string.settings),
                description = getString(R.string.settings_desc)
            )
        )
        rowsAdapter.add(ListRow(HeaderItem(2, getString(R.string.settings)), settingsAdapter))

        adapter = rowsAdapter
    }

    // -------------------------------------------------------------------------
    // Click / selection handling
    // -------------------------------------------------------------------------

    private fun setupClickListener() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when ((item as? MenuCard)?.title) {
                getString(R.string.start_visualizer) -> launchVisualizer()
                getString(R.string.browse_presets)   -> openPresetBrowser()
                getString(R.string.settings)          -> openSettings()
            }
        }
    }

    private fun launchVisualizer() {
        startActivity(Intent(requireContext(), VisualizerActivity::class.java))
    }

    private fun openPresetBrowser() {
        startActivity(Intent(requireContext(), PresetBrowserActivity::class.java))
    }

    private fun openSettings() {
        startActivity(Intent(requireContext(), SettingsActivity::class.java))
    }

    // -------------------------------------------------------------------------
    // Inner presenter
    // -------------------------------------------------------------------------

    /**
     * Renders each [MenuCard] as a simple text card suitable for a 10-foot UI.
     * Text size is ≥ 24sp as required.
     */
    private inner class MenuCardPresenter : Presenter() {

        inner class MenuCardViewHolder(val cardView: ImageCardView) : ViewHolder(cardView)

        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val cardView = ImageCardView(parent.context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                // Card dimensions suitable for a TV 10-foot UI
                setMainImageDimensions(320, 180)
            }
            return MenuCardViewHolder(cardView)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            val card = item as? MenuCard ?: return
            val holder = viewHolder as MenuCardViewHolder
            holder.cardView.titleText = card.title
            holder.cardView.contentText = card.description
            // Ensure title text size is ≥ 24sp
            holder.cardView.findViewById<TextView>(
                androidx.leanback.R.id.title_text
            )?.textSize = 24f
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {
            val holder = viewHolder as MenuCardViewHolder
            holder.cardView.badgeImage = null
            holder.cardView.mainImage = null
        }
    }
}
