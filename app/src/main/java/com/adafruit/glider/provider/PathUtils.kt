package com.adafruit.glider.provider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

private const val providerPeripheralDelimiter = "//"
private const val gliderPeripheralSeparator = "$"

fun getDocumentAddress(documentId: String): String =
    documentId.substringBefore(gliderPeripheralSeparator)

fun getDocumentPath(documentId: String): String =
    "/" + documentId.substringAfter(gliderPeripheralSeparator, "")
//.removePrefix(providerPeripheralDelimiter) + "/"

fun gliderRouteFromDocument(documentId: String): String {
    val address = getDocumentAddress(documentId)
    val path = getDocumentPath(documentId)
    /*if (path == "/") {      // If root, don't add extra slash
        path = ""
    }*/

    return "$address$gliderPeripheralSeparator$path"
}

fun gliderRouteFromDocument(documentId: String, filename: String): String =
    "${gliderRouteFromDocument(documentId)}$filename"