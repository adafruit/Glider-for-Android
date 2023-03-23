package com.adafruit.glider.provider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import io.openroad.filetransfer.Peripheral
import io.openroad.filetransfer.ble.peripheral.BondedBlePeripherals

data class DiscoveredPeripherals(
    var bondedBlePeripherals: List<BondedBlePeripherals.Data> = emptyList(),
    var peripherals: List<Peripheral> = emptyList(),
    var lastUpdateMillis: Long = 0
) {
    var isEmpty: Boolean = bondedBlePeripherals.isEmpty() && peripherals.isEmpty()
}