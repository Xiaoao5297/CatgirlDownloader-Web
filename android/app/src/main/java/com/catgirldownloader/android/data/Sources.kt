package com.catgirldownloader.android.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URLEncoder
import java.util.Random

/**
 * A source of random anime images. Implementations mirror the Python
 * source classes from CatgirlDownloader-Web.
 */
interface ImageSource {
    val key: String
    val name: String
    val hasTags: Boolean
    val tagPicker: Boolean
    val tagsConfigKey: String?
    val tagSingle: Boolean
    val tagDynamic: Boolean
    val needsKey: Boolean
    val tagsLabel: String?

    suspend fun fetchImage(nsfwMode: String, tags: String, apiKey: String?): ImageInfo?

    /** Returns suggested tags for the tag picker, or null when unsupported. */
    suspend fun fetchTags(query: String?): List<Tag>?
}

// ── Catgirl (nekos.moe) ────────────────────────────────────────────
class CatgirlSource : ImageSource {
    override val key = "catgirl"
    override val name = "Catgirl (nekos.moe)"
    override val hasTags = false
    override val tagPicker = true
    override val tagsConfigKey = "catgirl_tags"
    override val tagSingle = false
    override val tagDynamic = false
    override val needsKey = false
    override val tagsLabel = null

    override suspend fun fetchImage(nsfwMode: String, tags: String, apiKey: String?): ImageInfo? {
        val tagList = tags.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val json = if (tagList.isEmpty()) {
            val nsfw = when (nsfwMode) {
                NsfwModes.ONLY -> "true"
                NsfwModes.BLOCK -> "false"
                else -> null
            }
            ApiClient.getJson(
                "https://nekos.moe/api/v1/random/image" + (nsfw?.let { "?nsfw=$it" } ?: "")
            )
        } else {
            val body = JsonObject().apply {
                add("tags", JsonArray().apply { tagList.forEach { add(it) } })
                addProperty("limit", 25)
                addProperty("sort", "relevance")
                when (nsfwMode) {
                    NsfwModes.ONLY -> addProperty("nsfw", true)
                    NsfwModes.BLOCK -> addProperty("nsfw", false)
                }
            }
            ApiClient.postJson("https://nekos.moe/api/v1/images/search", body.toString())
        }
        return json?.let { parse(it) }
    }

    override suspend fun fetchTags(query: String?): List<Tag>? {
        val json = ApiClient.getJson("https://nekos.moe/api/v1/tags") ?: return null
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val arr = root.getAsJsonArray("tags")
            (0 until arr.size()).map { Tag(arr[it].asString, arr[it].asString) }
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(json: String): ImageInfo? {
        return try {
        val root = JsonParser.parseString(json).asJsonObject
        val images = root.getAsJsonArray("images")
        if (images.size() == 0) return null
        val img = images[0].asJsonObject
        val id = img.get("id")?.takeUnless { it.isJsonNull }?.asString ?: return null
        val artist = img.get("artist")?.takeUnless { it.isJsonNull }?.asString
        val nsfw = img.get("nsfw")?.takeUnless { it.isJsonNull }?.asBoolean
        val likes = img.get("likes")?.takeUnless { it.isJsonNull }?.asLong
        val favorites = img.get("favorites")?.takeUnless { it.isJsonNull }?.asLong
        val uploader = img.get("uploader")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject?.get("username")?.takeUnless { it.isJsonNull }?.asString
        val tags = img.get("tags")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.let { arr ->
                (0 until arr.size()).map { arr[it].asString }
            }
        val createdAt = img.get("createdAt")?.takeUnless { it.isJsonNull }?.asString
        ImageInfo(
            imageUrl = "https://nekos.moe/image/$id",
            artist = artist,
            link = "https://nekos.moe/post/$id",
            filename = "nekos.moe_$id",
            sourceKey = key,
            metadata = buildMap {
                artist?.let { put("artist", it) }
                nsfw?.let { put("nsfw", it) }
                likes?.let { put("likes", it) }
                favorites?.let { put("favorites", it) }
                uploader?.let { put("uploader", it) }
                if (!tags.isNullOrEmpty()) put("tags", tags)
                createdAt?.let { put("created_at", it) }
            },
        )
    } catch (e: Exception) {
        null
    }
    }
}

// ── Waifu (waifu.im) ───────────────────────────────────────────────
class WaifuSource : ImageSource {
    override val key = "waifu"
    override val name = "Waifu (waifu.im)"
    override val hasTags = false
    override val tagPicker = true
    override val tagsConfigKey = "waifu_tags"
    override val tagSingle = false
    override val tagDynamic = false
    override val needsKey = false
    override val tagsLabel = null

    override suspend fun fetchImage(nsfwMode: String, tags: String, apiKey: String?): ImageInfo? {
        val tagList = tags.replace(",", "|").split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val params = mutableListOf(
            "IsNsfw=" + when (nsfwMode) {
                NsfwModes.ONLY -> "True"
                NsfwModes.ALL -> "All"
                else -> "False"
            },
        )
        if (tagList.isNotEmpty()) {
            params += "IncludedTags=" + URLEncoder.encode(tagList.joinToString(","), "UTF-8")
        }
        val json = ApiClient.getJson("https://api.waifu.im/images?" + params.joinToString("&"))
        return json?.let { parse(it) }
    }

    override suspend fun fetchTags(query: String?): List<Tag>? {
        val json = ApiClient.getJson("https://api.waifu.im/tags?limit=50") ?: return null
        return try {
            val arr = JsonParser.parseString(json).asJsonObject.getAsJsonArray("items")
            (0 until arr.size()).mapNotNull { i ->
                val o = arr[i].asJsonObject
                val slug = o.get("slug")?.takeUnless { it.isJsonNull }?.asString ?: return@mapNotNull null
                val name = o.get("name")?.takeUnless { it.isJsonNull }?.asString ?: slug
                Tag(name, slug)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(json: String): ImageInfo? {
        return try {
        val root = JsonParser.parseString(json).asJsonObject
        val items = root.getAsJsonArray("items")
        if (items.size() == 0) return null
        val item = items[0].asJsonObject
        val url = item.get("url")?.takeUnless { it.isJsonNull }?.asString ?: return null
        val id = item.get("id")?.takeUnless { it.isJsonNull }?.let { v ->
            if (v.isJsonPrimitive && v.asJsonPrimitive.isNumber) v.asLong else v.asString
        }?.toString() ?: "unknown"
        val artist = item.getAsJsonArray("artists")?.firstOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("name")?.takeUnless { it.isJsonNull }?.asString
        val source = item.get("source")?.takeUnless { it.isJsonNull }?.asString
        val nsfw = item.get("isNsfw")?.takeUnless { it.isJsonNull }?.asBoolean
        val animated = item.get("isAnimated")?.takeUnless { it.isJsonNull }?.asBoolean
        val width = item.get("width")?.takeUnless { it.isJsonNull }?.asLong
        val height = item.get("height")?.takeUnless { it.isJsonNull }?.asLong
        val size = item.get("byteSize")?.takeUnless { it.isJsonNull }?.asLong
        val favorites = item.get("favorites")?.takeUnless { it.isJsonNull }?.asLong
        val uploadedAt = item.get("uploadedAt")?.takeUnless { it.isJsonNull }?.asString
        val tags = item.getAsJsonArray("tags")?.let { arr ->
            (0 until arr.size()).mapNotNull { arr[it].asJsonObject.get("name")?.asString }
        }
        ImageInfo(
            imageUrl = url,
            artist = artist,
            link = source,
            filename = "waifu.im_$id",
            sourceKey = key,
            metadata = buildMap {
                artist?.let { put("artist", it) }
                source?.let { put("source", it) }
                nsfw?.let { put("nsfw", it) }
                animated?.let { put("gif", it) }
                width?.let { put("width", it) }
                height?.let { put("height", it) }
                size?.let { put("size", it) }
                favorites?.let { put("favorites", it) }
                if (!tags.isNullOrEmpty()) put("tags", tags)
                uploadedAt?.let { put("created_at", it) }
            },
        )
    } catch (e: Exception) {
        null
    }
    }
}

// ── Danbooru ───────────────────────────────────────────────────────
class DanbooruSource : ImageSource {
    override val key = "danbooru"
    override val name = "Danbooru"
    override val hasTags = true
    override val tagPicker = true
    override val tagsConfigKey = "danbooru_tags"
    override val tagSingle = false
    override val tagDynamic = true
    override val needsKey = false
    override val tagsLabel = "Danbooru Tags"

    private val forbiddenTags = listOf("shota", "loli")

    override suspend fun fetchImage(nsfwMode: String, tags: String, apiKey: String?): ImageInfo? {
        val tagList = tags.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() && it !in forbiddenTags }
        val userTags = tagList.joinToString(" ")
        val ratingTag = when (nsfwMode) {
            NsfwModes.BLOCK -> "rating:general"
            NsfwModes.ONLY -> "rating:explicit"
            else -> null
        }
        val query = listOfNotNull(userTags.ifEmpty { null }, ratingTag).joinToString(" ")
        repeat(5) {
            val url = "https://danbooru.donmai.us/posts.json?limit=1&random=true" +
                (if (query.isNotBlank()) "&tags=" + URLEncoder.encode(query, "UTF-8") else "")
            val json = ApiClient.getJson(url) ?: return null
            val info = parse(json) ?: return null
            if (info.metadata["tags"]?.toString()?.lowercase()?.let { t ->
                    forbiddenTags.any { t.contains(it) }
                } == true
            ) {
                return@repeat
            }
            return info
        }
        return null
    }

    override suspend fun fetchTags(query: String?): List<Tag>? {
        val prefix = query?.trim()?.ifEmpty { null } ?: "a"
        val url = "https://danbooru.donmai.us/tags.json?search[name_matches]=" +
            URLEncoder.encode("$prefix*", "UTF-8") +
            "&search[order]=count&limit=20"
        val json = ApiClient.getJson(url) ?: return null
        return try {
            val arr = JsonParser.parseString(json).asJsonArray
            (0 until arr.size()).mapNotNull { i ->
                val name = arr[i].asJsonObject.get("name")?.takeUnless { it.isJsonNull }?.asString
                    ?: return@mapNotNull null
                Tag(name, name)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(json: String): ImageInfo? {
        return try {
        val root = JsonParser.parseString(json).asJsonArray
        if (root.size() == 0) return null
        val post = root[0].asJsonObject
        val fileUrl = post.get("file_url")?.takeUnless { it.isJsonNull }?.asString ?: return null
        val id = post.get("id")?.takeUnless { it.isJsonNull }?.asLong?.toString() ?: "unknown"
        val artistTag = post.get("tag_string_artist")?.takeUnless { it.isJsonNull }?.asString
        val artist = artistTag?.split(" ")?.firstOrNull()
        val rating = post.get("rating")?.takeUnless { it.isJsonNull }?.asString
        val score = post.get("score")?.takeUnless { it.isJsonNull }?.asLong
        val favCount = post.get("fav_count")?.takeUnless { it.isJsonNull }?.asLong
        val width = post.get("image_width")?.takeUnless { it.isJsonNull }?.asLong
        val height = post.get("image_height")?.takeUnless { it.isJsonNull }?.asLong
        val size = post.get("file_size")?.takeUnless { it.isJsonNull }?.asLong
        val source = post.get("source")?.takeUnless { it.isJsonNull }?.asString
        val tagString = post.get("tag_string")?.takeUnless { it.isJsonNull }?.asString
        val tags = tagString?.split(" ")?.filter { it.isNotBlank() }
        val createdAt = post.get("created_at")?.takeUnless { it.isJsonNull }?.asString
        ImageInfo(
            imageUrl = fileUrl,
            artist = artist,
            link = "https://danbooru.donmai.us/posts/$id",
            filename = "danbooru_$id",
            sourceKey = key,
            metadata = buildMap {
                artist?.let { put("artist", it) }
                rating?.let { put("rating", it) }
                score?.let { put("score", it) }
                favCount?.let { put("favorites", it) }
                width?.let { put("width", it) }
                height?.let { put("height", it) }
                size?.let { put("size", it) }
                source?.let { put("source", it) }
                if (!tags.isNullOrEmpty()) put("tags", tags)
                createdAt?.let { put("created_at", it) }
            },
        )
    } catch (e: Exception) {
        null
    }
    }
}

// ── Nekos API (nekosapi.com) ───────────────────────────────────────
class NekosSource : ImageSource {
    override val key = "nekos"
    override val name = "Nekos API"
    override val hasTags = true
    override val tagPicker = false
    override val tagsConfigKey = "category"
    override val tagSingle = false
    override val tagDynamic = false
    override val needsKey = false
    override val tagsLabel = "Category"

    override suspend fun fetchImage(nsfwMode: String, tags: String, apiKey: String?): ImageInfo? {
        val params = mutableListOf("limit=1")
        when (nsfwMode) {
            NsfwModes.BLOCK -> params += "rating=safe"
            NsfwModes.ONLY -> params += "rating=explicit"
        }
        if (tags.trim().isNotEmpty()) {
            params += "tags=" + URLEncoder.encode(tags.trim(), "UTF-8")
        }
        val json = ApiClient.getJson("https://api.nekosapi.com/v4/images/random?" + params.joinToString("&"))
        return json?.let { parse(it) }
    }

    override suspend fun fetchTags(query: String?): List<Tag>? = null

    private fun parse(json: String): ImageInfo? {
        return try {
        val root = JsonParser.parseString(json).asJsonArray
        if (root.size() == 0) return null
        val item = root[0].asJsonObject
        val url = item.get("url")?.takeUnless { it.isJsonNull }?.asString ?: return null
        val id = item.get("id")?.takeUnless { it.isJsonNull }?.asLong?.toString() ?: "unknown"
        val artist = item.get("artist_name")?.takeUnless { it.isJsonNull }?.asString
        val source = item.get("source_url")?.takeUnless { it.isJsonNull }?.asString
        val rating = item.get("rating")?.takeUnless { it.isJsonNull }?.asString
        val tags = item.getAsJsonArray("tags")?.let { arr ->
            (0 until arr.size()).mapNotNull { arr[it].takeUnless { e -> e.isJsonNull }?.asString }
        }
        ImageInfo(
            imageUrl = url,
            artist = artist,
            link = source ?: "https://api.nekosapi.com/v4/images/$id",
            filename = "nekosapi_$id",
            sourceKey = key,
            metadata = buildMap {
                artist?.let { put("artist", it) }
                rating?.let { put("rating", it); put("nsfw", rating in listOf("borderline", "explicit")) }
                if (!tags.isNullOrEmpty()) put("tags", tags)
                source?.let { put("source", it) }
            },
        )
    } catch (e: Exception) {
        null
    }
    }
}

// ── PurrBot ────────────────────────────────────────────────────────
class PurrbotSource : ImageSource {
    override val key = "purrbot"
    override val name = "PurrBot"
    override val hasTags = true
    override val tagPicker = true
    override val tagsConfigKey = "category"
    override val tagSingle = true
    override val tagDynamic = false
    override val needsKey = false
    override val tagsLabel = "Category"

    override suspend fun fetchImage(nsfwMode: String, tags: String, apiKey: String?): ImageInfo? {
        val path = when (nsfwMode) {
            NsfwModes.ONLY -> "nsfw"
            NsfwModes.ALL -> if (Random().nextBoolean()) "sfw" else "nsfw"
            else -> "sfw"
        }
        val category = tags.trim().lowercase().ifEmpty { "neko" }
        val formats = if (path == "nsfw" && category != "neko") listOf("gif") else listOf("img", "gif")
        for (fmt in formats) {
            val json = ApiClient.getJson("https://api.purrbot.site/v2/img/$path/$category/$fmt") ?: continue
            try {
                val root = JsonParser.parseString(json).asJsonObject
                if (root.get("error")?.asBoolean == true) return null
                val link = root.get("link")?.takeUnless { it.isJsonNull }?.asString ?: continue
                val imageId = link.substringAfterLast("/").substringBeforeLast(".").ifEmpty { "unknown" }
                return ImageInfo(
                    imageUrl = link,
                    artist = null,
                    link = link,
                    filename = "purrbot_$imageId",
                    sourceKey = key,
                    metadata = mapOf("category" to category),
                )
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    override suspend fun fetchTags(query: String?): List<Tag>? =
        PURRBOT_CATEGORIES.map { Tag(it, it) }

    companion object {
        val PURRBOT_CATEGORIES = listOf(
            "angry", "background", "bite", "blush", "comfy", "cry", "cuddle",
            "dance", "eevee", "fluff", "holo", "hug", "icon", "kiss", "kitsune",
            "lay", "lick", "neko", "okami", "pat", "poke", "pout", "senko",
            "shiro", "slap", "smile", "tail", "tickle",
            "anal", "blowjob", "cum", "fuck", "pussylick", "solo", "solo_male",
            "threesome_fff", "threesome_ffm", "threesome_mmf", "yaoi", "yuri",
        )
    }
}

// ── Fluxpoint ──────────────────────────────────────────────────────
class FluxpointSource : ImageSource {
    override val key = "fluxpoint"
    override val name = "Fluxpoint"
    override val hasTags = true
    override val tagPicker = true
    override val tagsConfigKey = "category"
    override val tagSingle = true
    override val tagDynamic = false
    override val needsKey = true
    override val tagsLabel = "Category"

    override suspend fun fetchImage(nsfwMode: String, tags: String, apiKey: String?): ImageInfo? {
        if (apiKey.isNullOrBlank()) return null
        val path = when (nsfwMode) {
            NsfwModes.ONLY -> "nsfw"
            NsfwModes.ALL -> if (Random().nextBoolean()) "sfw" else "nsfw"
            else -> "sfw"
        }
        val category = tags.trim().lowercase().ifEmpty { "neko" }
        for (fmt in listOf("img", "gif")) {
            val json = ApiClient.getJson(
                "https://api.fluxpoint.dev/$path/$fmt/$category",
                mapOf("Authorization" to apiKey),
            ) ?: continue
            try {
                val root = JsonParser.parseString(json).asJsonObject
                if (root.get("success")?.asBoolean == false) return null
                val file = root.get("file")?.takeUnless { it.isJsonNull }?.asString ?: continue
                val id = root.get("id")?.takeUnless { it.isJsonNull }?.asString ?: "unknown"
                return ImageInfo(
                    imageUrl = file,
                    artist = null,
                    link = file,
                    filename = "fluxpoint_$id",
                    sourceKey = key,
                    metadata = mapOf("category" to category),
                )
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    override suspend fun fetchTags(query: String?): List<Tag>? =
        FLUXPOINT_CATEGORIES.map { Tag(it, it) }

    companion object {
        val FLUXPOINT_CATEGORIES = listOf(
            "waifu", "neko", "hug", "cuddle", "kiss", "blush", "cry", "pat",
            "smug", "wave", "dance", "poke", "wink", "smile",
            "baka", "bite", "feed", "fluff", "grab", "handhold", "highfive",
            "laugh", "lick", "punch", "shrug", "slap", "stare", "tickle", "wag",
            "wasted",
            "cum", "feet", "femdom", "futa", "gasm", "holo", "kitsune",
            "pantyhose", "peeing", "petplay", "pussy", "slime", "solo", "girl",
            "anal", "ass", "bdsm", "blowjob", "boobjob", "boobs", "handjob",
            "hentai", "kuni", "wank", "spank", "tentacle", "toys", "yuri",
        )
    }
}

// ── Registry ───────────────────────────────────────────────────────
object Sources {
    val all: List<ImageSource> = listOf(
        CatgirlSource(),
        WaifuSource(),
        DanbooruSource(),
        NekosSource(),
        PurrbotSource(),
        FluxpointSource(),
    )

    fun byKey(key: String): ImageSource =
        all.firstOrNull { it.key == key } ?: all.first()
}
