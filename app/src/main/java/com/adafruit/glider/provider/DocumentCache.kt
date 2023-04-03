package com.adafruit.glider.provider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

data class DocumentCache(
    private val cache: ConcurrentMap<Uri, List<DocumentMetaData>> = ConcurrentHashMap()
) {
    fun add(uri: Uri, documents: List<DocumentMetaData>) {
        cache[uri] = documents
    }

    fun get(uri: Uri) = cache[uri]
}