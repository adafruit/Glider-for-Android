package com.adafruit.glider.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns


class DocumentProviderUtils {
    companion object {
        // based on: https://stackoverflow.com/questions/5568874/how-to-extract-the-file-name-from-uri-returned-from-intent-action-get-content
        fun getFileName(context: Context, uri: Uri): String? {
            var result: String? = null
            if (uri.scheme == "content") {
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
                cursor.use {
                    it?.let {
                        if (it.moveToFirst()) {
                            val column = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (column >= 0) {
                                result = it.getString(column)
                            }
                        }
                    }
                }
            }

            if (result == null) {
                result = uri.path
                val cut = result?.lastIndexOf('/')
                if (cut != null && cut != -1) {
                    result = result?.substring(cut + 1)
                }
            }
            return result
        }

    }
}