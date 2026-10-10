package com.gufont.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object SafUtils {
    fun persist(context: Context, uri: Uri, write: Boolean = true) {
        try {
            val flags = if (write) {
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            } else Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) { }
    }
}
