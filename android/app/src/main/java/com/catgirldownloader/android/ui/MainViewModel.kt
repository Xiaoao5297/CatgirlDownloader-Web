package com.catgirldownloader.android.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.request.ImageRequest
import com.catgirldownloader.android.data.ApiClient
import com.catgirldownloader.android.data.Favorite
import com.catgirldownloader.android.data.FavoritesRepository
import com.catgirldownloader.android.data.ImageInfo
import com.catgirldownloader.android.data.ImageSource
import com.catgirldownloader.android.data.NsfwModes
import com.catgirldownloader.android.data.Prefs
import com.catgirldownloader.android.data.Sources
import com.catgirldownloader.android.data.Tag
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque

/** A lightweight description of a source shown in the settings UI. */
data class SourceMeta(
    val key: String,
    val name: String,
    val hasTags: Boolean,
    val tagPicker: Boolean,
    val tagsConfigKey: String?,
    val tagSingle: Boolean,
    val tagDynamic: Boolean,
    val needsKey: Boolean,
    val tagsLabel: String?,
)

/** An image currently on screen (or in history). favId is set for favorites. */
data class DisplayedImage(
    val imageUrl: String,
    val artist: String?,
    val link: String?,
    val filename: String,
    val sourceKey: String,
    val metadata: Map<String, Any?>,
    val favId: String? = null,
)

data class UiState(
    val sourceKey: String = "catgirl",
    val nsfwMode: String = NsfwModes.DEFAULT,
    val autoReload: Boolean = false,
    val reloadInterval: Int = 30,
    val isLoading: Boolean = false,
    val error: Boolean = false,
    val sources: List<SourceMeta> = emptyList(),
    val history: List<DisplayedImage> = emptyList(),
    val historyIndex: Int = -1,
    val favorites: List<Favorite> = emptyList(),
    val pickerTags: List<String> = emptyList(),
) {
    val current: DisplayedImage?
        get() = if (historyIndex in history.indices) history[historyIndex] else null
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private val favoritesRepo = FavoritesRepository(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Auto-reload countdown, 0..1. Kept separate so the main state
     *  flow is not re-emitted 100x per second while the bar animates. */
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private var reloadJob: Job? = null
    private var preloadJob: Job? = null
    private var lastLoadedUrl: String? = null

    /** Pre-downloaded next images (mirrors the web server's preload queue). */
    private val preloadQueue = ArrayDeque<ImageInfo>()

    /** Marker id used while a favorite is being saved in the background. */
    private companion object {
        const val PENDING_FAV = "__pending__"
    }

    init {
        val sourceKey = prefs.source
        _state.update {
            it.copy(
                sourceKey = sourceKey,
                nsfwMode = prefs.nsfwMode,
                autoReload = prefs.autoReload,
                reloadInterval = prefs.reloadInterval,
                sources = Sources.all.map { s ->
                    SourceMeta(
                        s.key, s.name, s.hasTags, s.tagPicker, s.tagsConfigKey,
                        s.tagSingle, s.tagDynamic, s.needsKey, s.tagsLabel,
                    )
                },
                favorites = favoritesRepo.load(),
                pickerTags = parseTags(sourceKey, prefs.tags(tagsConfigKey(sourceKey) ?: "")),
            )
        }
    }

    // ── Image fetching ───────────────────────────────────────────────
    fun fetchImage() {
        if (_state.value.isLoading) return
        reloadJob?.cancel()
        val s = _state.value
        val source = Sources.byKey(s.sourceKey)

        // Instant result from the preload queue if one is ready.
        while (preloadQueue.isNotEmpty()) {
            val preloaded = preloadQueue.removeFirst()
            if (preloaded.imageUrl != lastLoadedUrl) {
                _progress.value = 0f
                lastLoadedUrl = preloaded.imageUrl
                pushImage(
                    DisplayedImage(
                        imageUrl = preloaded.imageUrl,
                        artist = preloaded.artist,
                        link = preloaded.link,
                        filename = preloaded.filename,
                        sourceKey = preloaded.sourceKey,
                        metadata = preloaded.metadata,
                    ),
                )
                _state.update { it.copy(isLoading = false, error = false) }
                fillPreload()
                return
            }
        }

        viewModelScope.launch {
            _progress.value = 0f
            _state.update { it.copy(isLoading = true, error = false) }
            var info = fetch(source, s.nsfwMode)
            if (info != null && info.imageUrl == lastLoadedUrl) {
                // avoid showing the exact same image twice in a row
                info = fetch(source, s.nsfwMode)
            }
            if (info != null) {
                lastLoadedUrl = info.imageUrl
                pushImage(
                    DisplayedImage(
                        imageUrl = info.imageUrl,
                        artist = info.artist,
                        link = info.link,
                        filename = info.filename,
                        sourceKey = info.sourceKey,
                        metadata = info.metadata,
                    ),
                )
                _state.update { it.copy(isLoading = false) }
                fillPreload()
                // Countdown is started from the UI once the image is really
                // displayed (onImageDisplayed), not here.
            } else {
                _state.update { it.copy(isLoading = false, error = true) }
                // Nothing to display: restart the countdown so it retries.
                scheduleReload()
            }
        }
    }

    /** Called by the UI when the current image has finished loading. */
    fun onImageDisplayed() {
        scheduleReload()
    }

    private suspend fun fetch(source: ImageSource, nsfwMode: String) =
        source.fetchImage(
            nsfwMode,
            prefs.tags(tagsConfigKey(source.key) ?: ""),
            if (source.needsKey) prefs.fluxpointKey else null,
        )

    // ── Preload queue ────────────────────────────────────────────────
    /** Number of images to pre-download (0 = disabled), mirrors web's _MAX_PRELOAD. */
    fun preloadCount(): Int = prefs.preloadCount

    fun setPreloadCount(n: Int) {
        val v = n.coerceIn(0, 20)
        prefs.preloadCount = v
        if (v == 0) {
            preloadQueue.clear()
        } else {
            fillPreload()
        }
    }

    private fun fillPreload() {
        preloadJob?.cancel()
        val s = _state.value
        val count = prefs.preloadCount
        if (count <= 0) return
        val source = Sources.byKey(s.sourceKey)
        preloadJob = viewModelScope.launch {
            while (preloadQueue.size < count) {
                val info = fetch(source, s.nsfwMode) ?: break
                preloadQueue.addLast(info)
                prewarm(info.imageUrl)
            }
        }
    }

    /** Warm Coil's memory cache so the next "New Image" is instant. */
    private fun prewarm(url: String) {
        try {
            val ctx = getApplication<Application>()
            Coil.imageLoader(ctx).enqueue(ImageRequest.Builder(ctx).data(url).build())
        } catch (e: Exception) {
            // ignore preload failures
        }
    }

    private fun clearPreload() {
        preloadJob?.cancel()
        preloadQueue.clear()
    }

    // ── History navigation ───────────────────────────────────────────
    fun goPrev() {
        val s = _state.value
        if (s.historyIndex <= 0 || s.isLoading) return
        _state.update { it.copy(historyIndex = s.historyIndex - 1, error = false) }
    }

    fun goNext() {
        val s = _state.value
        if (s.isLoading) return
        if (s.historyIndex < s.history.size - 1) {
            _state.update { it.copy(historyIndex = s.historyIndex + 1, error = false) }
        } else {
            fetchImage()
        }
    }

    private fun pushImage(image: DisplayedImage) {
        val s = _state.value
        val history = if (s.historyIndex < s.history.size - 1) {
            s.history.subList(0, s.historyIndex + 1).toMutableList()
        } else {
            s.history.toMutableList()
        }
        history.add(image)
        _state.update { it.copy(history = history, historyIndex = history.size - 1) }
    }

    fun showFavorite(fav: Favorite) {
        pushImage(
            DisplayedImage(
                imageUrl = fav.imageUrl,
                artist = fav.artist,
                link = fav.link,
                filename = fav.filename,
                sourceKey = fav.source,
                metadata = fav.metadata,
                favId = fav.id,
            ),
        )
    }

    // ── Settings ─────────────────────────────────────────────────────
    fun setSource(key: String) {
        if (_state.value.sourceKey == key) return
        prefs.source = key
        clearPreload()
        _state.update {
            it.copy(
                sourceKey = key,
                pickerTags = parseTags(key, prefs.tags(tagsConfigKey(key) ?: "")),
            )
        }
        fetchImage()
    }

    fun setNsfwMode(mode: String) {
        prefs.nsfwMode = mode
        clearPreload()
        _state.update { it.copy(nsfwMode = mode) }
        fetchImage()
    }

    fun setAutoReload(enabled: Boolean) {
        prefs.autoReload = enabled
        _state.update { it.copy(autoReload = enabled) }
        if (enabled) scheduleReload() else { reloadJob?.cancel(); _progress.value = 0f }
    }

    fun setReloadInterval(seconds: Int) {
        val v = seconds.coerceIn(1, 3600)
        prefs.reloadInterval = v
        _state.update { it.copy(reloadInterval = v) }
        if (_state.value.autoReload) scheduleReload()
    }

    fun setLang(lang: String) {
        prefs.lang = lang
    }

    fun prefsLang(): String = prefs.lang

    fun categoryValue(): String = prefs.tags("category")

    fun fluxpointKey(): String = prefs.fluxpointKey

    fun setPickerTags(tags: List<String>) {
        val key = tagsConfigKey(_state.value.sourceKey) ?: return
        val delim = if (_state.value.sourceKey == "danbooru") " " else "|"
        prefs.setTags(key, tags.joinToString(delim))
        clearPreload()
        _state.update { it.copy(pickerTags = tags) }
    }

    fun setCategory(value: String) {
        prefs.setTags("category", value.trim())
        clearPreload()
    }

    fun setFluxpointKey(value: String) {
        prefs.fluxpointKey = value.trim()
        clearPreload()
    }

    // ── Favorites ────────────────────────────────────────────────────
    fun toggleFavorite() {
        val cur = _state.value.current ?: return
        val existing = _state.value.favorites.firstOrNull { it.imageUrl == cur.imageUrl }
        if (cur.favId != null || existing != null) {
            val id = cur.favId ?: existing!!.id
            favoritesRepo.remove(id)
            _state.update { it.copy(favorites = favoritesRepo.load()) }
            updateFavId(cur.imageUrl, null)
            return
        }

        // Optimistic: mark the heart solid immediately, persist in background.
        updateFavId(cur.imageUrl, PENDING_FAV)
        viewModelScope.launch {
            val bytes = ApiClient.download(cur.imageUrl)
            if (bytes != null) {
                val fav = favoritesRepo.add(cur, bytes)
                _state.update { it.copy(favorites = favoritesRepo.load()) }
                updateFavId(cur.imageUrl, fav.id)
            } else {
                updateFavId(cur.imageUrl, null)
            }
        }
    }

    fun deleteFavorite(id: String) {
        favoritesRepo.remove(id)
        _state.update { it.copy(favorites = favoritesRepo.load()) }
        val s = _state.value
        if (s.current?.favId == id) updateFavId(s.current!!.imageUrl, null)
    }

    /** Update the favId of the history entry that matches [imageUrl]. */
    private fun updateFavId(imageUrl: String, id: String?) {
        val s = _state.value
        val updated = s.history.map {
            if (it.imageUrl == imageUrl) it.copy(favId = id) else it
        }
        if (updated != s.history) _state.update { it.copy(history = updated) }
    }

    // ── Download ─────────────────────────────────────────────────────
    fun downloadCurrent(context: Context, onResult: (Boolean) -> Unit) {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            val bytes = if (cur.favId != null) {
                val fav = _state.value.favorites.firstOrNull { it.id == cur.favId }
                if (fav != null) {
                    favoritesRepo.file(fav).readBytes()
                } else {
                    ApiClient.download(cur.imageUrl)
                }
            } else {
                ApiClient.download(cur.imageUrl)
            }
            if (bytes == null) {
                onResult(false)
            } else {
                onResult(saveToGallery(context, cur.filename, bytes))
            }
        }
    }

    private fun saveToGallery(context: Context, baseName: String, bytes: ByteArray): Boolean {
        val ext = resolveExtFromName(baseName)
        val displayName = baseName + "." + ext
        val mime = when (ext) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CatgirlDownloader")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "CatgirlDownloader")
                dir.mkdirs()
                File(dir, displayName).writeBytes(bytes)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun resolveExtFromName(name: String): String {
        val base = name.substringBefore('?').lowercase()
        return when {
            base.endsWith(".png") -> "png"
            base.endsWith(".gif") -> "gif"
            base.endsWith(".webp") -> "webp"
            base.endsWith(".jpeg") || base.endsWith(".jpg") -> "jpg"
            else -> "jpg"
        }
    }

    // ── Tag suggestions (for settings sheet) ─────────────────────────
    suspend fun searchTags(sourceKey: String, query: String?): List<Tag>? =
        Sources.byKey(sourceKey).fetchTags(query)

    // ── Auto reload countdown ────────────────────────────────────────
    private fun scheduleReload() {
        reloadJob?.cancel()
        val s = _state.value
        if (!s.autoReload) return
        _progress.value = 0f
        reloadJob = viewModelScope.launch {
            val totalMs = s.reloadInterval * 1000L
            val steps = 100L
            val stepMs = totalMs / steps
            for (i in 1..steps) {
                delay(stepMs)
                if (!_state.value.autoReload) return@launch
                _progress.value = i.toFloat() / steps
            }
            fetchImage()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────
    private fun tagsConfigKey(sourceKey: String): String? =
        Sources.byKey(sourceKey).tagsConfigKey

    private fun parseTags(sourceKey: String, raw: String): List<String> {
        val delim = if (sourceKey == "danbooru") " " else "|"
        return raw.split(delim).map { it.trim() }.filter { it.isNotEmpty() }
    }
}
