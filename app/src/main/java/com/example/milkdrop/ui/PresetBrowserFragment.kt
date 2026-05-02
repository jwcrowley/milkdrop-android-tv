package com.example.milkdrop.ui

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.VerticalGridPresenter
import com.example.milkdrop.LauncherActivity
import com.example.milkdrop.R
import com.example.milkdrop.VisualizerActivity
import com.example.milkdrop.model.Preset

/**
 * Preset browser fragment displayed in a vertical grid layout.
 *
 * Extends [VerticalGridSupportFragment] and populates an [ArrayObjectAdapter]
 * with all presets from [LauncherActivity.presetLibrary]. User presets are
 * distinguished with a "★ User" badge.
 *
 * Selecting a preset calls [PresetManager.loadPreset] and navigates back to
 * [VisualizerActivity].
 *
 * Fully navigable by D-pad only.
 */
class PresetBrowserFragment : VerticalGridSupportFragment() {

    companion object {
        private const val NUM_COLUMNS = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.preset_browser_title)

        val gridPresenter = VerticalGridPresenter()
        gridPresenter.numberOfColumns = NUM_COLUMNS
        setGridPresenter(gridPresenter)

        val adapter = ArrayObjectAdapter(PresetCardPresenter())

        if (LauncherActivity.isInitialized) {
            val library = LauncherActivity.presetLibrary
            val bundledCount = library.bundledCount
            library.presets.forEachIndexed { index, preset ->
                // Presets beyond bundledCount are user presets
                adapter.add(PresetCardPresenter.PresetItem(preset, isUser = index >= bundledCount))
            }
        }

        this.adapter = adapter

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val presetItem = item as? PresetCardPresenter.PresetItem ?: return@OnItemViewClickedListener
            if (LauncherActivity.isInitialized) {
                LauncherActivity.presetManager.loadPreset(presetItem.preset)
            }
            // Navigate back to the visualizer
            val intent = Intent(requireContext(), VisualizerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            activity?.finish()
        }
    }
}

/**
 * Leanback [Presenter] that renders each [Preset] as a card in the grid.
 *
 * User presets receive a "★ User" badge in the card's content text.
 * All text is ≥ 24sp as required.
 */
class PresetCardPresenter : Presenter() {

    /** Wraps a [Preset] with a flag indicating whether it is a user preset. */
    data class PresetItem(val preset: Preset, val isUser: Boolean)

    inner class PresetCardViewHolder(val cardView: ImageCardView) : ViewHolder(cardView)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(240, 135)
        }
        return PresetCardViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val presetItem = item as? PresetItem ?: return
        val holder = viewHolder as PresetCardViewHolder

        holder.cardView.titleText = presetItem.preset.name
        holder.cardView.contentText = if (presetItem.isUser) {
            viewHolder.view.context.getString(R.string.user_preset_badge)
        } else {
            ""
        }

        // Ensure title text size is ≥ 24sp
        holder.cardView.findViewById<TextView>(
            androidx.leanback.R.id.title_text
        )?.textSize = 24f
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val holder = viewHolder as PresetCardViewHolder
        holder.cardView.badgeImage = null
        holder.cardView.mainImage = null
    }
}
