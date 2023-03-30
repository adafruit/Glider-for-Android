package com.adafruit.glider.provider

import android.util.Log

/**
 * Created by Antonio García (antonio@openroad.es)
 */

//private const val providerPeripheralDelimiter = "//"
private const val gliderPeripheralSeparator = "$"

fun getDocumentAddress(documentId: String): String =
    documentId.substringBefore(gliderPeripheralSeparator)

fun getDocumentPath(documentId: String): String {
    var result = documentId.substringAfter(gliderPeripheralSeparator, "")
    if (!result.startsWith("/")) {
        result = "/$result"
    }
    return result
//.removePrefix(providerPeripheralDelimiter) + "/"
}

fun getDocumentName(documentId: String): String {
    return documentId.substringAfterLast("/", "")
}

private fun gliderRouteFromDocument(documentId: String): String {
    val address = getDocumentAddress(documentId)
    var path = getDocumentPath(documentId)
    if (path == "/") {      // If root, don't add extra slash
        path = ""
    }

    return "$address$gliderPeripheralSeparator$path"
}

fun gliderRouteFromDocument(documentId: String, filename: String): String  = "${gliderRouteFromDocument(documentId)}/$filename"
