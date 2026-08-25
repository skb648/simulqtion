package com.realitycompiler

import android.content.Context

object BackendConfig {
    private const val PREFS = "reality_compiler"
    private const val KEY_URL = "backend_url"

    fun load(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URL, BuildConfig.BACKEND_BASE_URL) ?: BuildConfig.BACKEND_BASE_URL

    fun save(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_URL, url.trim().trimEnd('/')).apply()
    }
}
