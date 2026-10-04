package com.deaquiyalla.nfcinsta

import android.content.Context

object Prefs {
    private const val FILE = "nfc_insta"
    private const val KEY_URL = "url"
    const val DEFAULT_URL = "https://instagram.com/brunotorcoletti"

    fun url(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_URL, DEFAULT_URL)!!

    fun save(ctx: Context, url: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_URL, url).apply()
    }
}
