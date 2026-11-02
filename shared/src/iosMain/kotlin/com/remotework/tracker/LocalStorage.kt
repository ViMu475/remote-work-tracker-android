package com.remotework.tracker

import platform.Foundation.NSUserDefaults

actual class LocalStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun save(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    actual fun load(key: String): String? {
        return defaults.stringForKey(key)
    }

    actual fun clear(key: String) {
        defaults.removeObjectForKey(key)
    }
}
