package com.adafruit.glider.provider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import io.openroad.filetransfer.Peripheral
import io.openroad.filetransfer.ble.peripheral.BlePeripheral
import io.openroad.filetransfer.ble.peripheral.BondedBlePeripherals
import io.openroad.filetransfer.filetransfer.ConnectionManager

data class DiscoveredPeripherals(
    var bondedBlePeripherals: List<BondedBlePeripherals.Data> = emptyList(),
    var peripherals: List<Peripheral> = emptyList(),
    var lastUpdateMillis: Long = 0
) {
    var isEmpty: Boolean = bondedBlePeripherals.isEmpty() && peripherals.isEmpty()
}


// region Utils

fun createDiscoveredPeripherals(
    connectionManager: ConnectionManager,
    bondedBlePeripherals: BondedBlePeripherals,
): DiscoveredPeripherals {
    return DiscoveredPeripherals(
        bondedBlePeripherals = bondedBlePeripherals.peripheralsData.value,
        peripherals = connectionManager.peripherals.value,
        lastUpdateMillis = System.currentTimeMillis()
    )
}

fun getBondedPeripheralsNotDiscovered(discoveredPeripherals: DiscoveredPeripherals): List<BondedBlePeripherals.Data> {
    val blePeripherals = discoveredPeripherals.peripherals
        .filterIsInstance<BlePeripheral>()
    val blePeripheralsAddresses = blePeripherals.map { it.address }
    val bondedNotAdvertisingPeripherals = discoveredPeripherals.bondedBlePeripherals
        .filter { !blePeripheralsAddresses.contains(it.address) }
    return bondedNotAdvertisingPeripherals
}

// endregion
