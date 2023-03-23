package com.adafruit.glider.ui.connected

import android.Manifest
import android.annotation.SuppressLint
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adafruit.glider.utils.LogUtils
import io.openroad.filetransfer.ble.peripheral.BondedBlePeripherals
import io.openroad.filetransfer.filetransfer.ConnectionManager
import io.openroad.filetransfer.filetransfer.WifiFileTransferPeripheral
import io.openroad.filetransfer.wifi.peripheral.SavedSettingsWifiPeripherals
import io.openroad.filetransfer.wifi.peripheral.WifiPeripheral
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PeripheralsViewModel(
    val connectionManager: ConnectionManager,
    val bondedBlePeripherals: BondedBlePeripherals,
    private val savedSettingsWifiPeripherals: SavedSettingsWifiPeripherals,
) : ViewModel() {

    // Data - Private
    private val log by LogUtils()
    private val _wifiDialogSettings =
        MutableStateFlow<Pair<String, String>?>(null)       // address, currentPassword

    // Data - Public
    val wifiDialogSettings = _wifiDialogSettings.asStateFlow()

    // region Lifecycle
    fun onResume() {
        // Start scanning
        try {
            startScan()
        } catch (e: SecurityException) {
            log.warning("startScan security exception: $e")
        }
    }

    fun onPause() {
        stopScan()
    }
    // endregion

    // region Actions
    @SuppressLint("InlinedApi")
    @RequiresPermission(value = Manifest.permission.BLUETOOTH_CONNECT)
    private fun startScan() {
        stopScan()
        bondedBlePeripherals.refresh()
        connectionManager.startScan()
    }

    private fun stopScan() {
        connectionManager.stopScan()
    }

    fun openWifiDialogSettings(wifiPeripheral: WifiPeripheral) {
        getWifiPeripheralCurrentPassword(wifiPeripheral) { password ->
            if (password != null) {
                _wifiDialogSettings.update { Pair(wifiPeripheral.address, password) }
            }
        }
    }

    fun closeWifiDialogSettings() {
        _wifiDialogSettings.update { null }
    }

    private fun getWifiPeripheralCurrentPassword(
        wifiPeripheral: WifiPeripheral,
        completion: (String?) -> Unit
    ) {
        // Send request to get hostName
        val fileTransferPeripheral = WifiFileTransferPeripheral(wifiPeripheral, null)
        fileTransferPeripheral.getVersion(viewModelScope) { version ->
            if (version != null) {
                // Get password
                val password = savedSettingsWifiPeripherals.getPassword(version.hostName)
                    ?: WifiFileTransferPeripheral.defaultPassword
                completion(password)
            } else {
                log.warning("Error retrieving hostName. Cannot get password")
                completion(null)
            }
        }
    }

    fun updateWifiPeripheralPassword(wifiPeripheral: WifiPeripheral, newPassword: String) {
        // Send request to get hostName
        val fileTransferPeripheral = WifiFileTransferPeripheral(wifiPeripheral, null)
        fileTransferPeripheral.getVersion(viewModelScope) { version ->
            if (version != null) {
                // Save new password
                savedSettingsWifiPeripherals.add(version.boardName, version.hostName, newPassword)

                // Update password if the peripheral is already in connection manager
                val success = connectionManager.updateWifiPeripheralPassword(
                    wifiPeripheral.address,
                    newPassword
                )
                if (success) {
                    log.info("Password update for connected wifi peripheral")
                }
            } else {
                log.warning("Error retrieving hostName. Cannot update password")
            }
        }
    }
    // endregion


    /**
     * Factory that takes ProjectsRepository as a dependency
     */
    companion object {
        fun provideFactory(
            connectionManager: ConnectionManager,
            bondedBlePeripherals: BondedBlePeripherals,
            savedSettingsWifiPeripherals: SavedSettingsWifiPeripherals,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PeripheralsViewModel(
                    connectionManager,
                    bondedBlePeripherals,
                    savedSettingsWifiPeripherals,
                ) as T
            }
        }
    }
}