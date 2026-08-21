package com.catgirldownloader.android.data

import android.content.Context

/** Thin SharedPreferences wrapper for app settings. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("catgirl_prefs", Context.MODE_PRIVATE)

    var source: String
        get() = sp.getString("source", "catgirl") ?: "catgirl"
        set(value) = sp.edit().putString("source", value).apply()

    var nsfwMode: String
        get() = sp.getString("nsfw_mode", NsfwModes.DEFAULT) ?: NsfwModes.DEFAULT
        set(value) = sp.edit().putString("nsfw_mode", value).apply()

    var autoReload: Boolean
        get() = sp.getBoolean("auto_reload", false)
        set(value) = sp.edit().putBoolean("auto_reload", value).apply()

    var reloadInterval: Int
        get() = sp.getInt("reload_interval", 30).coerceIn(1, 3600)
        set(value) = sp.edit().putInt("reload_interval", value.coerceIn(1, 3600)).apply()

    var lang: String
        get() = sp.getString("lang", "auto") ?: "auto"
        set(value) = sp.edit().putString("lang", value).apply()

    var preloadCount: Int
        get() = sp.getInt("preload_count", 5).coerceIn(0, 20)
        set(value) = sp.edit().putInt("preload_count", value.coerceIn(0, 20)).apply()

    var fluxpointKey: String
        get() = sp.getString("fluxpoint_key", "") ?: ""
        set(value) = sp.edit().putString("fluxpoint_key", value).apply()

    fun tags(key: String): String = sp.getString("tags_$key", "") ?: ""

    fun setTags(key: String, value: String) {
        sp.edit().putString("tags_$key", value).apply()
    }
}
