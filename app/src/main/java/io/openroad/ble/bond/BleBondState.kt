package io.openroad.ble.bond

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

/**
 * Created by Antonio García (antonio@openroad.es)
 */
enum class BleBondState {
    Unknown,
    NotBonded,
    Bonded,
    Bonding;

    fun isBonded() = this == Bonded

    companion object {
        fun from(bluetoothDeviceBondState: Int): BleBondState {
            return when (bluetoothDeviceBondState) {
                BluetoothDevice.BOND_BONDED -> Bonded
                BluetoothDevice.BOND_BONDING -> Bonding
                BluetoothDevice.BOND_NONE -> NotBonded
                else -> Unknown
            }
        }
    }
}