package com.adafruit.glider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.content.Context
import io.openroad.filetransfer.ble.peripheral.SavedBondedBlePeripherals
import io.openroad.filetransfer.ble.scanner.BlePeripheralScanner
import io.openroad.filetransfer.ble.scanner.BlePeripheralScannerImpl
import io.openroad.filetransfer.filetransfer.ConnectionManager
import io.openroad.filetransfer.wifi.peripheral.SavedSettingsWifiPeripherals
import io.openroad.filetransfer.wifi.scanner.WifiPeripheralScanner


import io.openroad.wifi.scanner.WifiPeripheralScannerImpl
import kotlinx.coroutines.MainScope

/**
 * Dependency Injection container at the application level.
 */
interface AppContainer {
    val blePeripheralScanner: BlePeripheralScanner
    val wifiPeripheralScanner: WifiPeripheralScanner
    val connectionManager: ConnectionManager
    val savedBondedBlePeripherals: SavedBondedBlePeripherals
    val savedSettingsWifiPeripherals: SavedSettingsWifiPeripherals
}

/**
 * Implementation for the Dependency Injection container at the application level.
 *
 * Variables are initialized lazily and the same instance is shared across the whole app.
 */
class AppContainerImpl(private val applicationContext: Context) : AppContainer {
    override val blePeripheralScanner: BlePeripheralScanner by lazy {
        BlePeripheralScannerImpl(
            context = applicationContext,
            scanFilters = null,
            externalScope = MainScope()
        )
    }

    override val wifiPeripheralScanner: WifiPeripheralScanner by lazy {
        WifiPeripheralScannerImpl(
            context = applicationContext,
            serviceType = "_circuitpython._tcp.",
        )
    }

    override val connectionManager: ConnectionManager by lazy {
        ConnectionManager(
            context = applicationContext,
            blePeripheralScanner = blePeripheralScanner,
            wifiPeripheralScanner = wifiPeripheralScanner,
            onBlePeripheralBonded = { name, address ->
                // Bluetooth peripheral -> Save bluetooth address when bonded to be able to reconnect later
                savedBondedBlePeripherals.add(name, address)
            },
            onWifiPeripheralGetPasswordForHostName = { _, hostName ->
                // Wifi peripheral -> Get saved password
                savedSettingsWifiPeripherals.getPassword(hostName)
            },
        )
    }

    override val savedBondedBlePeripherals: SavedBondedBlePeripherals by lazy {
        SavedBondedBlePeripherals(
            context = applicationContext
        )
    }

    override val savedSettingsWifiPeripherals: SavedSettingsWifiPeripherals by lazy {
        SavedSettingsWifiPeripherals(
            context = applicationContext
        )
    }
}