package com.catgirldownloader.android.ui

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import coil.load
import com.catgirldownloader.android.R
import com.catgirldownloader.android.data.Favorite
import com.catgirldownloader.android.databinding.ItemFavoriteBinding

/**
 * A dialog showing the saved favorites as a masonry (waterfall) grid:
 * fixed column width, full uncropped images with their real aspect ratio.
 * Tap to view, trash button to delete.
 */
class FavoritesDialog(private val context: Context, private val viewModel: MainViewModel) {

    private var dialog: AlertDialog? = null

    fun show() {
        val items = viewModel.state.value.favorites.toMutableList()
        val adapter = FavoriteAdapter(context, items) { fav ->
            viewModel.showFavorite(fav)
            dialog?.dismiss()
        }

        val emptyView = TextView(context).apply {
            text = context.getString(R.string.favorites_empty)
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
            visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            setPadding(dp(24), dp(24), dp(24), dp(24))
            gravity = Gravity.CENTER
        }

        // Waterfall layout: fixed column width, full image, variable height.
        val screenWidth = context.resources.displayMetrics.widthPixels
        val columnWidth = dp(150)
        val columns = (screenWidth / columnWidth).coerceAtLeast(2)

        val recycler = RecyclerView(context).apply {
            layoutManager = StaggeredGridLayoutManager(columns, StaggeredGridLayoutManager.VERTICAL)
            this.adapter = adapter
            setPadding(dp(8), dp(8), dp(8), dp(8))
            // Cap the dialog height so it scrolls instead of growing off-screen.
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.72f).toInt(),
            )
            visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(emptyView)
            addView(recycler)
        }

        val d = AlertDialog.Builder(context)
            .setTitle(R.string.favorites)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog = d

        adapter.onDelete = { fav ->
            viewModel.deleteFavorite(fav.id)
            items.removeAll { it.id == fav.id }
            adapter.notifyDataSetChanged()
            emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            recycler.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        }

        d.show()
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}

private class FavoriteAdapter(
    private val context: Context,
    private val items: List<Favorite>,
    private val onOpen: (Favorite) -> Unit,
) : RecyclerView.Adapter<FavoriteViewHolder>() {

    var onDelete: ((Favorite) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ItemFavoriteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        val fav = items[position]
        holder.binding.favImage.load(fav.imageUrl) { crossfade(true) }
        holder.binding.favArtist.text = fav.artist ?: context.getString(R.string.artist_unknown)
        holder.binding.root.setOnClickListener { onOpen(fav) }
        holder.binding.favDelete.setOnClickListener { onDelete?.invoke(fav) }
    }

    override fun getItemCount(): Int = items.size
}

private class FavoriteViewHolder(val binding: ItemFavoriteBinding) :
    RecyclerView.ViewHolder(binding.root)
