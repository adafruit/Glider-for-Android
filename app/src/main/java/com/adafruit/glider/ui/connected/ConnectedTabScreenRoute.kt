package com.adafruit.glider.ui.connected

import io.openroad.ble.utils.filenameFromPath
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Created by Antonio García (antonio@openroad.es)
 */

sealed class ConnectedTabScreenRoute(val route: String) {
    object Info : ConnectedTabScreenRoute("info")
    object FileExplorer : ConnectedTabScreenRoute("fileExplorer")
    object FileEdit : ConnectedTabScreenRoute("fileEdit/{path}") {
        fun createRoute(path: String): String {
            // Encode path param because it contains slashes that will clash with the arg resolution
            val encodedParam =
                URLEncoder.encode(path, StandardCharsets.UTF_8.toString())

            return "fileEdit/$encodedParam"
        }

        fun decodePathArg(encodedPath: String): String {
            return URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.toString())
        }
    }

    companion object {
        fun fromRoute(route: String?): ConnectedTabScreenRoute =
            when (route?.substringBefore("/")) {
                Info.route -> Info
                FileExplorer.route -> FileExplorer
                FileEdit.route.substringBefore("/") -> FileEdit
                null -> FileExplorer
                else -> throw IllegalArgumentException("Route $route is not recognized.")
            }
    }

    fun title(): String =
        when (this) {
            is Info -> "Info"
            is FileExplorer -> "File Explorer"
            is FileEdit -> {
                // Return name of the file
                val path = route.substringAfter('/')
                filenameFromPath(path)
            }
        }
}
