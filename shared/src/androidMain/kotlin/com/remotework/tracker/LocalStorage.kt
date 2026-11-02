package com.remotework.tracker

import android.content.Context
import android.content.SharedPreferences

actual class LocalStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("remote_work_tracker", Context.MODE_PRIVATE)

    actual fun save(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun load(key: String): String? {
        return prefs.getString(key, null)
    }

    actual fun clear(key: String) {
        prefs.edit().remove(key).apply()
    }
}
