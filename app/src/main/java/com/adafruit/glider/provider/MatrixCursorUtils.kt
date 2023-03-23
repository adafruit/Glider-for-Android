package com.adafruit.glider.provider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.database.MatrixCursor
import android.os.Bundle
import android.provider.DocumentsContract
import com.adafruit.glider.provider.ProviderConfig.DEFAULT_DOCUMENT_PROJECTION

fun createLoadingCursor(projection: Array<out String>?): MatrixCursor {
    return object : MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION) {
        override fun getExtras(): Bundle {
            return Bundle().apply {
                putBoolean(DocumentsContract.EXTRA_LOADING, true)
                putString(DocumentsContract.EXTRA_INFO, "Loading...");
            }
        }
    }
}
