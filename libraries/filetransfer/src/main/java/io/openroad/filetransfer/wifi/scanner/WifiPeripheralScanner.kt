package io.openroad.filetransfer.wifi.scanner

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import io.openroad.filetransfer.wifi.peripheral.WifiPeripheral
import io.openroad.filetransfer.wifi.scanner.NsdException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface WifiPeripheralScanner {
    // State flows that are updated while running. Use start() and stop()
    val isRunning: Boolean
    val wifiLastException: StateFlow<NsdException?>
    val wifiPeripherals: StateFlow<List<WifiPeripheral>>

    fun clearWifiLastException()

    // Cold flow independent from start() stop()
    val wifiPeripheralsFlow: Flow<List<WifiPeripheral>>
}