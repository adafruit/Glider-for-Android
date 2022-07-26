package com.adafruit.glider.ui

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.navigation.NavHostController
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Destinations used in the app
 */
sealed class GliderDestinations(val route: String) {
    object Startup : GliderDestinations("startup")
    object Scan : GliderDestinations("scan")
    object ConnectedBottomNavigation : GliderDestinations("connectedTab")
    object FileEdit : GliderDestinations("fileEdit/{path}") {
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

    object MoveFile : GliderDestinations("moveFile/{path}") {
        fun createRoute(path: String): String {
            // Encode path param because it contains slashes that will clash with the arg resolution
            val encodedParam =
                URLEncoder.encode(path, StandardCharsets.UTF_8.toString())

            return "moveFile/$encodedParam"
        }

        fun decodePathArg(encodedPath: String): String {
            return URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.toString())
        }
    }

}


/**
 * Models the navigation actions in the app.
 */
class GliderNavigationActions(navController: NavHostController) {

}