package com.example.milkdrop.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import com.example.milkdrop.R
import com.example.milkdrop.VisualizerActivity

class MainFragment : BrowseSupportFragment() {

    private data class MenuCard(val title: String, val description: String, val action: String)

    companion object {
        const val ACTION_VISUALIZER = "visualizer"
        const val ACTION_PRESETS = "presets"
        const val ACTION_SETTINGS = "settings"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        title = getString(R.string.app_name)
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false

        setupRows()
        setupClickListener()
    }

    private fun setupRows() {
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val cardPresenter = MenuCardPresenter()

        val items = listOf(
            MenuCard(getString(R.string.start_visualizer), getString(R.string.start_visualizer_desc), ACTION_VISUALIZER),
            MenuCard(getString(R.string.browse_presets),   getString(R.string.browse_presets_desc),   ACTION_PRESETS),
            MenuCard(getString(R.string.settings),          getString(R.string.settings_desc),          ACTION_SETTINGS)
        )

        items.forEachIndexed { index, card ->
            val rowAdapter = ArrayObjectAdapter(cardPresenter)
            rowAdapter.add(card)
            rowsAdapter.add(ListRow(HeaderItem(index.toLong(), card.title), rowAdapter))
        }

        adapter = rowsAdapter
    }

    private fun setupClickListener() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val card = item as? MenuCard ?: return@OnItemViewClickedListener
            when (card.action) {
                ACTION_VISUALIZER -> startActivity(Intent(requireContext(), VisualizerActivity::class.java))
                ACTION_PRESETS    -> startActivity(Intent(requireContext(), PresetBrowserActivity::class.java))
                ACTION_SETTINGS   -> startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
        }
    }

    private inner class MenuCardPresenter : Presenter() {

        inner class MenuCardViewHolder(val cardView: ImageCardView) : ViewHolder(cardView)

        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val cardView = ImageCardView(parent.context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                setMainImageDimensions(320, 180)
            }
            return MenuCardViewHolder(cardView)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            val card = item as? MenuCard ?: return
            val holder = viewHolder as MenuCardViewHolder
            holder.cardView.titleText = card.title
            holder.cardView.contentText = card.description
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
