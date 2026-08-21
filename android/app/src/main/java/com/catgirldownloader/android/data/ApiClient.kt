package com.catgirldownloader.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Minimal OkHttp wrapper used by all image sources.
 * All network calls run on the IO dispatcher.
 */
object ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "CatgirlDownloaderAndroid/1.0"

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): String? =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
                headers.forEach { (k, v) -> builder.header(k, v) }
                client.newCall(builder.build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String? =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .post(body.toRequestBody("application/json".toMediaType()))
                headers.forEach { (k, v) -> builder.header(k, v) }
                client.newCall(builder.build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun download(url: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
                client.newCall(builder.build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.bytes() else null
                }
            } catch (e: Exception) {
                null
            }
        }
}

/** Resolve an image file extension from a URL, defaulting to jpg. */
fun resolveExt(url: String): String {
    val lower = url.lowercase().substringBefore('?')
    return when {
        lower.endsWith(".png") -> "png"
        lower.endsWith(".gif") -> "gif"
        lower.endsWith(".webp") -> "webp"
        lower.endsWith(".jpeg") -> "jpg"
        lower.endsWith(".jpg") -> "jpg"
        else -> "jpg"
    }
}
