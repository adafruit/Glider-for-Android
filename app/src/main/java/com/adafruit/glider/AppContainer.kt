package com.adafruit.glider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.content.Context
import io.openroad.filetransfer.ConnectionManager
import io.openroad.wifi.scanner.WifiPeripheralScanner
import io.openroad.wifi.scanner.WifiPeripheralScannerImpl
import kotlinx.coroutines.MainScope

/**
 * Dependency Injection container at the application level.
 */
interface AppContainer {
    val wifiPeripheralScanner: WifiPeripheralScanner
    val connectionManager: ConnectionManager
}

/**
 * Implementation for the Dependency Injection container at the application level.
 *
 * Variables are initialized lazily and the same instance is shared across the whole app.
 */
class AppContainerImpl(private val applicationContext: Context) : AppContainer {
    override val wifiPeripheralScanner: WifiPeripheralScanner by lazy {
        WifiPeripheralScannerImpl(
            context = applicationContext,
            serviceType = "_circuitpython._tcp.",
            externalScope = MainScope()
        )
    }

    override val connectionManager: ConnectionManager by lazy {
        ConnectionManager(
            wifiPeripheralScanner = wifiPeripheralScanner
        )
    }
}