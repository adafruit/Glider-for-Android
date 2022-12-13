package com.adafruit.glider.ui.connected

/**
 * Created by Antonio García (antonio@openroad.es)
 */

/**
 * Destinations used in the connected bottom navigation
 */
sealed class ConnectedNavigationDestinations(val route: String) {
    object Peripherals : ConnectedNavigationDestinations("peripherals")
    object FileExplorer : ConnectedNavigationDestinations("fileExplorer")
    object Log : ConnectedNavigationDestinations("log")

    companion object {
        fun fromRoute(route: String?): ConnectedNavigationDestinations =
            when (route?.substringBefore("/")) {
                Peripherals.route -> Peripherals
                FileExplorer.route -> FileExplorer
                Log.route -> Log
                null -> FileExplorer
                else -> throw IllegalArgumentException("Route $route is not recognized.")
            }
    }

    fun title(): String =
        when (this) {
            is Peripherals -> "Scanning Peripherals..."
            is FileExplorer -> "File Explorer"
            is Log -> "Log"
        }
}
