package io.openroad.ble.filetransfer

import io.openroad.ble.BleManager
import io.openroad.ble.FileTransferClient
import io.openroad.ble.applicationContext
import io.openroad.utils.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Created by Antonio García (antonio@openroad.es)
 */
object FileTransferConnectionManager {
    // Data - Private
    private val log by LogUtils()

    private var _selectedFileTransferClient = MutableStateFlow<FileTransferClient?>(null)
    val selectedFileTransferClient = _selectedFileTransferClient.asStateFlow()

    private var _isSelectedPeripheralReconnecting = MutableStateFlow(false)
    val isSelectedPeripheralReconnecting = _isSelectedPeripheralReconnecting.asStateFlow()

    // region Actions
    fun setSelectedPeripheral(bleFileTransferPeripheral: BleFileTransferPeripheral) {
        val selectedFileTransferClient = FileTransferClient(bleFileTransferPeripheral)
        _selectedFileTransferClient.update { selectedFileTransferClient }
    }

    fun reconnect(completion: (isConnected: Boolean) -> Unit) {

        val knownAddresses = BleKnownPeripheralAddresses.knownAddresses

        // Filter-out address that are not bonded
        val pairedAddresses =
            BleManager.getPairedPeripherals(applicationContext)?.map { it.address } ?: emptySet()
        val addresses = knownAddresses.intersect(pairedAddresses)

        if (addresses.isNotEmpty()) {
            _isSelectedPeripheralReconnecting.update { true }
            BleManager.reconnectToPeripherals(
                applicationContext,
                addresses
            ) { firstConnectedBleFileTransferPeripheral ->
                _isSelectedPeripheralReconnecting.update { false }
                if (firstConnectedBleFileTransferPeripheral != null) {
                    log.info("Reconnected to ${firstConnectedBleFileTransferPeripheral.nameOrAddress}")
                    setSelectedPeripheral(firstConnectedBleFileTransferPeripheral)
                    completion(true)
                } else {
                    log.info("No reconnection")
                    completion(false)
                }
            }
        } else {
            completion(false)
        }

    }

    // endregion
}