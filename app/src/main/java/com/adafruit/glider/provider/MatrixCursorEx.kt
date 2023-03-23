package com.adafruit.glider.provider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.database.MatrixCursor
import android.provider.DocumentsContract

fun MatrixCursor.isLoading(): Boolean = extras.getBoolean(DocumentsContract.EXTRA_LOADING)
