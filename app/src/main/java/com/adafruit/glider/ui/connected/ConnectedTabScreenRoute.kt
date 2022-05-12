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
    object Log : ConnectedTabScreenRoute("log")

    companion object {
        fun fromRoute(route: String?): ConnectedTabScreenRoute =
            when (route?.substringBefore("/")) {
                Info.route -> Info
                FileExplorer.route -> FileExplorer
                Log.route -> Log
                null -> FileExplorer
                else -> throw IllegalArgumentException("Route $route is not recognized.")
            }
    }

    fun title(): String =
        when (this) {
            is Info -> "Info"
            is FileExplorer -> "File Explorer"
            is Log -> "Log"
        }
}
