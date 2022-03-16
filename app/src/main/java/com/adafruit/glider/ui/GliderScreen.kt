package com.adafruit.glider.ui

/**
 * Created by Antonio García (antonio@openroad.es)
 */

enum class GliderScreen {
    Startup,
    BluetoothStatus,
    Scan,
    ConnectedTab;

    companion object {
        fun fromRoute(route: String?): GliderScreen =
            when (route?.substringBefore("/")) {
                Startup.name -> Startup
                BluetoothStatus.name -> BluetoothStatus
                Scan.name -> Scan
                ConnectedTab.name -> ConnectedTab
                null -> Startup
                else -> throw IllegalArgumentException("Route $route is not recognized.")
            }
    }
}