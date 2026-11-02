package com.remotework.tracker.android

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/** Exports live in cache/shared (the only folder the FileProvider exposes). */
internal fun exportDir(context: Context): File = File(context.cacheDir, "shared").apply { mkdirs() }

/** Returns a fresh export file and removes older exports, which contain personal working-time data. */
internal fun newExportFile(context: Context, name: String): File {
    val dir = exportDir(context)
    dir.listFiles()?.forEach { it.delete() }
    return File(dir, safeFileName(name))
}

/** Reads UTF-8 text but gives up (null) when the stream is larger than [maxBytes]. */
internal fun readTextLimited(input: InputStream, maxBytes: Int): String? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        if (total > maxBytes) return null
        out.write(buffer, 0, n)
    }
    return String(out.toByteArray(), Charsets.UTF_8)
}
