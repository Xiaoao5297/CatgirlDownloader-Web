package com.catgirldownloader.android.data

import android.content.Context
import com.catgirldownloader.android.ui.DisplayedImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

/**
 * Stores favorite images as files in the app's internal storage,
 * plus a JSON index file (mirrors the web app's favorites directory).
 */
class FavoritesRepository(context: Context) {

    private val dir = File(context.filesDir, "favorites").apply { mkdirs() }
    private val indexFile = File(dir, "favorites.json")
    private val gson = Gson()

    fun load(): List<Favorite> = try {
        if (indexFile.exists()) {
            val type = object : TypeToken<List<Favorite>>() {}.type
            val list: List<Favorite> = gson.fromJson(indexFile.readText(), type) ?: emptyList()
            list.filter { File(dir, it.fileName).exists() }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun add(image: DisplayedImage, bytes: ByteArray): Favorite {
        val id = UUID.randomUUID().toString()
        val ext = resolveExt(image.imageUrl)
        val fileName = "$id.$ext"
        File(dir, fileName).writeBytes(bytes)
        val fav = Favorite(
            id = id,
            fileName = fileName,
            imageUrl = image.imageUrl,
            filename = "${image.filename}.$ext",
            artist = image.artist,
            link = image.link,
            source = image.sourceKey,
            metadata = image.metadata,
            savedAt = System.currentTimeMillis(),
        )
        saveAll(listOf(fav) + load())
        return fav
    }

    fun remove(id: String) {
        val list = load()
        list.firstOrNull { it.id == id }?.let { File(dir, it.fileName).delete() }
        saveAll(list.filterNot { it.id == id })
    }

    fun file(fav: Favorite): File = File(dir, fav.fileName)

    private fun saveAll(list: List<Favorite>) {
        try {
            indexFile.writeText(gson.toJson(list))
        } catch (e: Exception) {
            // ignore write failures
        }
    }
}
