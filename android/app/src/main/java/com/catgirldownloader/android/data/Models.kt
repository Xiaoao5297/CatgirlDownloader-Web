package com.catgirldownloader.android.data

/** Metadata describing a single fetched image from any source. */
data class ImageInfo(
    val imageUrl: String,
    val artist: String?,
    val link: String?,
    val filename: String,
    val sourceKey: String,
    val metadata: Map<String, Any?> = emptyMap(),
)

/** A tag / category suggestion returned by a source. */
data class Tag(
    val name: String,
    val slug: String,
)

/** NSFW filter modes, mirroring the web app's NSFW_MODES. */
object NsfwModes {
    const val BLOCK = "BLOCK_NSFW"
    const val ONLY = "ONLY_NSFW"
    const val ALL = "SHOW_EVERYTHING"

    const val DEFAULT = BLOCK
}

/** A locally saved favorite image. */
data class Favorite(
    val id: String,
    val fileName: String,
    val imageUrl: String,
    val filename: String,
    val artist: String?,
    val link: String?,
    val source: String,
    val metadata: Map<String, Any?> = emptyMap(),
    val savedAt: Long,
)
