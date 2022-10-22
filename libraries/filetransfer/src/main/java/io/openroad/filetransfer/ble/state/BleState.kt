package io.openroad.filetransfer.ble.state

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.bluetooth.BluetoothAdapter


enum class BleState {
    Unknown,
    BluetoothNotAvailable,              // General Bluetooth not available
    BleNotAvailable,                    // Bluetooth-low-energy not available
    Disabled,
    Enabled,
    TurningOn,
    TurningOff;

    fun isEnabled() = this == Enabled

    companion object {
        fun from(bluetoothAdapterState: Int): BleState {
            return when (bluetoothAdapterState) {
                BluetoothAdapter.STATE_OFF -> Disabled
                BluetoothAdapter.STATE_TURNING_OFF -> TurningOff
                BluetoothAdapter.STATE_ON -> Enabled
                BluetoothAdapter.STATE_TURNING_ON -> TurningOn
                else -> Unknown
            }
        }
    }
}