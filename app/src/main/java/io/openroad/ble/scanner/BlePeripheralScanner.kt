package io.openroad.ble.scanner

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import io.openroad.ble.peripheral.BlePeripheral
import io.openroad.ble.utils.BleException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BlePeripheralScanner {
    val isRunning: Boolean
    val bleLastException : StateFlow<BleException?>
    val blePeripherals: StateFlow<List<BlePeripheral>>

    /*
    // Actions
    fun start()
    fun stop()
*/
    // Cold flow independent from start() stop()
    val blePeripheralsFlow: Flow<List<BlePeripheral>>
}