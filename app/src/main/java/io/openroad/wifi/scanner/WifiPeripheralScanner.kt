package io.openroad.wifi.scanner

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import io.openroad.wifi.peripheral.WifiPeripheral
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface WifiPeripheralScanner {
    // State flows that are updated while running. Use start() and stop()
    val isRunning: Boolean
    val wifiLastException: StateFlow<NsdException?>
    val wifiPeripherals: StateFlow<List<WifiPeripheral>>

    // Actions
    fun start()
    fun stop()

    // Cold flow independent from start() stop()
    val wifiPeripheralsFlow: Flow<List<WifiPeripheral>>

}