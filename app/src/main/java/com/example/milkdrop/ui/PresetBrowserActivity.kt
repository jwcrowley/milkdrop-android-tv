package com.example.milkdrop.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.addCallback
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.milkdrop.LauncherActivity
import com.example.milkdrop.R
import com.example.milkdrop.VisualizerActivity
import com.example.milkdrop.model.Preset

internal fun categoryForPreset(preset: Preset): String {
    val parts = preset.filePath.split("/")
    val presetsIdx = parts.indexOfLast { it == "presets" }
    return if (
        presetsIdx >= 0 &&
        parts.getOrNull(presetsIdx + 1) == "projectm-cream"
    ) {
        parts.getOrNull(presetsIdx + 2) ?: "Other"
    } else if (presetsIdx >= 0) {
        "User Presets"
    } else {
        "Other"
    }
}

/**
 * Group presets by top-level category directory, sorted for display.
 * Called off the main thread when the library is built (see
 * [LauncherActivity.rebuildPresetLibrary]).
 */
internal fun groupPresetsByCategory(
    presets: List<Preset>
): List<Pair<String, List<Preset>>> =
    presets.groupBy(::categoryForPreset)
        .entries
        .sortedBy { it.key.trimStart { c -> !c.isLetter() }.lowercase() }
        .map { it.key to it.value.sortedBy { p -> p.name.trimStart { c -> !c.isLetter() }.lowercase() } }

/**
 * Two-level preset browser: Category list → Preset list within category.
 * Built with plain RecyclerView — no Leanback grid (which can't handle 9,795 items).
 * Fully D-pad navigable.
 */
class PresetBrowserActivity : FragmentActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var titleView: TextView
    private lateinit var countView: TextView

    // All presets grouped by top-level category (first path component under presets/)
    private var categories: List<Pair<String, List<Preset>>> = emptyList()
    private var currentCategory: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!LauncherActivity.isInitialized) {
            startActivity(Intent(this, LauncherActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_preset_browser)

        recyclerView = findViewById(R.id.preset_browser_list)
        titleView    = findViewById(R.id.preset_browser_title)
        countView    = findViewById(R.id.preset_browser_count)

        recyclerView.layoutManager = LinearLayoutManager(this)

        if (LauncherActivity.isInitialized) {
            val favoriteIds = LauncherActivity.presetFavoritesRepository.getFavoriteIds()
            // Categories are pre-grouped off the main thread when the library is
            // built; fall back to computing them here only if that cache is empty.
            val grouped = LauncherActivity.presetCategories.ifEmpty {
                groupPresetsByCategory(LauncherActivity.presetLibrary.presets)
            }
            val favorites = LauncherActivity.presetLibrary.presets
                .filter { it.id in favoriteIds }
                .sortedBy { it.name.lowercase() }
            categories = if (favorites.isNotEmpty()) {
                listOf(getString(R.string.favorites_category) to favorites) + grouped
            } else {
                grouped
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (currentCategory != null) showCategoryList() else finish()
        }

        showCategoryList()
    }

    private fun showCategoryList() {
        currentCategory = null
        titleView.text = getString(R.string.browse_presets)
        countView.text = getString(
            R.string.browser_category_count,
            categories.size,
            categories.sumOf { it.second.size }
        )

        recyclerView.adapter = CategoryAdapter(categories) { category, presets ->
            showPresetList(category, presets)
        }
    }

    private fun showPresetList(category: String, presets: List<Preset>) {
        currentCategory = category
        titleView.text = category
        countView.text = getString(R.string.preset_count, presets.size)

        recyclerView.adapter = PresetAdapter(
            presets = presets,
            favoriteIds = if (LauncherActivity.isInitialized) {
                LauncherActivity.presetFavoritesRepository.getFavoriteIds()
            } else {
                emptySet()
            }
        ) { preset ->
            LauncherActivity.presetManager.loadPreset(preset)
            val intent = Intent(this, VisualizerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
        recyclerView.scrollToPosition(0)
    }
}

// ── Category list adapter ─────────────────────────────────────────────────────

class CategoryAdapter(
    private val categories: List<Pair<String, List<Preset>>>,
    private val onClick: (String, List<Preset>) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView  = view.findViewById(R.id.browser_item_name)
        val count: TextView = view.findViewById(R.id.browser_item_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_browser_category, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (name, presets) = categories[position]
        holder.name.text = name
        holder.count.text =
            holder.itemView.context.getString(R.string.browser_item_count, presets.size)
        holder.itemView.setOnClickListener { onClick(name, presets) }
        holder.itemView.isFocusable = true
    }

    override fun getItemCount() = categories.size
}

// ── Preset list adapter ───────────────────────────────────────────────────────

class PresetAdapter(
    private val presets: List<Preset>,
    private val favoriteIds: Set<String>,
    private val onClick: (Preset) -> Unit
) : RecyclerView.Adapter<PresetAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView  = view.findViewById(R.id.browser_item_name)
        val count: TextView = view.findViewById(R.id.browser_item_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_browser_category, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val preset = presets[position]
        holder.name.text = if (preset.id in favoriteIds) {
            holder.itemView.context.getString(R.string.favorite_item, preset.name)
        } else {
            preset.name
        }
        holder.count.text = preset.format.name.lowercase()
        holder.itemView.setOnClickListener { onClick(preset) }
        holder.itemView.isFocusable = true
    }

    override fun getItemCount() = presets.size
}
