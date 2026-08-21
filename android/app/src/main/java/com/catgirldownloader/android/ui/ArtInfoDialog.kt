package com.catgirldownloader.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.catgirldownloader.android.R

/** Builds the "About this art" dialog from the current image metadata. */
object ArtInfoDialog {

    fun show(context: Context, image: DisplayedImage) {
        val sourceName = SourcesDisplayName.displayName(context, image.sourceKey)
        val meta = image.metadata

        val sb = StringBuilder()
        sb.append(context.getString(R.string.source_label)).append(": ").append(sourceName).append("\n")
        sb.append(context.getString(R.string.filename)).append(": ").append(image.filename).append("\n")

        meta["artist"]?.let {
            sb.append(context.getString(R.string.artist)).append(": ").append(it).append("\n")
        }
        meta["uploader"]?.let {
            sb.append(context.getString(R.string.uploader)).append(": ").append(it).append("\n")
        }
        meta["rating"]?.let {
            sb.append(context.getString(R.string.rating)).append(": ").append(it).append("\n")
        }
        meta["score"]?.let {
            sb.append(context.getString(R.string.score)).append(": ").append(it).append("\n")
        }
        meta["likes"]?.let {
            sb.append(context.getString(R.string.likes)).append(": ").append(it).append("\n")
        }
        meta["favorites"]?.let {
            sb.append(context.getString(R.string.favorites_count)).append(": ").append(it).append("\n")
        }
        meta["size"]?.let {
            sb.append(context.getString(R.string.size)).append(": ").append(formatBytes(it)).append("\n")
        }
        if (meta["width"] != null && meta["height"] != null) {
            sb.append("${meta["width"]}×${meta["height"]}\n")
        }
        meta["nsfw"]?.let {
            sb.append(context.getString(R.string.nsfw)).append(": ")
                .append(if (it == true || it.toString() == "true") context.getString(R.string.yes) else context.getString(R.string.no))
                .append("\n")
        }
        meta["created_at"]?.let {
            sb.append(context.getString(R.string.posted)).append(": ").append(it).append("\n")
        }
        meta["tags"]?.let {
            if (it is List<*> && it.isNotEmpty()) {
                sb.append(context.getString(R.string.tags)).append(": ").append(it.joinToString(", ")).append("\n")
            }
        }
        meta["category"]?.let {
            sb.append(context.getString(R.string.category)).append(": ").append(it).append("\n")
        }

        val link = image.link
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.about_art)
            .setMessage(sb.toString().trim())
            .setNegativeButton(android.R.string.cancel, null)
        if (link != null) {
            dialog.setPositiveButton(R.string.open_page) { _, _ ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                } catch (e: Exception) {
                    // no browser available
                }
            }
        }
        dialog.show()
    }

    private fun formatBytes(value: Any?): String {
        val bytes = (value as? Number)?.toLong() ?: return value?.toString() ?: ""
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${(bytes / 1024.0).toInt()} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

/** Resolves a source key to its display name. */
object SourcesDisplayName {
    private var cache: Map<String, String> = emptyMap()

    fun setSources(sources: List<SourceMeta>) {
        cache = sources.associate { it.key to it.name }
    }

    fun displayName(context: Context, key: String): String = cache[key] ?: key
}
