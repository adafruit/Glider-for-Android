package com.adafruit.glider.ui

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Created by Antonio García (antonio@openroad.es)
 */

sealed class ScreenRoute(val route: String) {
    object Startup : ScreenRoute("startup")
    object BluetoothStatus : ScreenRoute("bluetoothStatus")
    object Scan : ScreenRoute("scan")
    object ConnectedTab : ScreenRoute("connectedTab")
    object FileEdit : ScreenRoute("fileEdit/{path}") {
        fun createRoute(path: String): String {
            // Encode path param because it contains slashes that will clash with the arg resolution
            val encodedParam =
                URLEncoder.encode(path, StandardCharsets.UTF_8.toString())

            return "fileEdit/$encodedParam"
        }

        fun decodePathArg(encodedPath: String): String {
            return URLDecoder.decode(encodedPath,StandardCharsets.UTF_8.toString())
        }
    }


    companion object {
        fun fromRoute(route: String?): ScreenRoute =
            when (route?.substringBefore("/")) {
                Startup.route -> Startup
                BluetoothStatus.route -> BluetoothStatus
                Scan.route -> Scan
                ConnectedTab.route -> ConnectedTab
                FileEdit.route.substringBefore("/") -> FileEdit
                null -> Startup
                else -> throw IllegalArgumentException("Route $route is not recognized.")
            }
    }
}