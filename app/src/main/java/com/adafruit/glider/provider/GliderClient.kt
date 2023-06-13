package com.adafruit.glider.provider

import android.Manifest
import android.annotation.SuppressLint
import androidx.annotation.RequiresPermission
import io.openroad.filetransfer.ble.utils.LogUtils
import io.openroad.filetransfer.Peripheral
import io.openroad.filetransfer.ble.peripheral.BondedBlePeripherals
import io.openroad.filetransfer.filetransfer.ConnectionManager
import io.openroad.filetransfer.filetransfer.DirectoryEntry
import io.openroad.filetransfer.filetransfer.FileTransferClient
import io.openroad.filetransfer.filetransfer.FileTransferProgressHandler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class GliderClient(var address: String) {
    companion object {
        var sharedInstances: HashMap<String, GliderClient> = hashMapOf()

        fun getInstance(peripheralId: String): GliderClient {
            val gliderClient = sharedInstances[peripheralId]
            return if (gliderClient == null) {
                val newInstance = GliderClient(peripheralId)
                sharedInstances[peripheralId] = newInstance
                newInstance
            } else {
                gliderClient
            }
        }
    }

    // Data - Private
    private val log by LogUtils()
    private val scope = MainScope()

    // region Actions
    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    fun readFile(
        path: String,
        connectionManager: ConnectionManager,
        bondedBlePeripherals: BondedBlePeripherals,
        progress: FileTransferProgressHandler? = null,
        completion: ((Result<ByteArray>) -> Unit)?
    ) {
        getFileTransferClient(connectionManager, bondedBlePeripherals) { result ->
            result.fold(
                onSuccess = {
                    it.readFile(scope, path, progress, completion)
                },
                onFailure = { exception ->
                    completion?.let { it(Result.failure(exception)) }
                }
            )
        }
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    fun writeFile(
        path: String,
        data: ByteArray,
        connectionManager: ConnectionManager,
        bondedBlePeripherals: BondedBlePeripherals,
        progress: FileTransferProgressHandler? = null,
        completion: ((Result<Date?>) -> Unit)?
    ) {
        getFileTransferClient(connectionManager, bondedBlePeripherals) { result ->
            result.fold(
                onSuccess = {
                    it.writeFile(scope, path, data, progress, completion)
                },
                onFailure = { exception ->
                    completion?.let { it(Result.failure(exception)) }
                }
            )
        }
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    fun listDirectory(
        path: String,
        connectionManager: ConnectionManager,
        bondedBlePeripherals: BondedBlePeripherals,
        completion: ((Result<List<DirectoryEntry>?>) -> Unit)?
    ) {
        getFileTransferClient(connectionManager, bondedBlePeripherals) { result ->
            result.fold(
                onSuccess = {
                    it.listDirectory(scope, path, completion)
                },
                onFailure = { exception ->
                    completion?.let { it(Result.failure(exception)) }
                }
            )
        }
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private fun getFileTransferClient(
        connectionManager: ConnectionManager,
        bondedBlePeripherals: BondedBlePeripherals,
        completion: ((Result<FileTransferClient>) -> Unit)
    ) {

        // Check if we already have the fileTransferClient peripheral, or even a discovered peripheral
        val existingPeripheral = connectionManager.getFileTransferClient(address)?.peripheral
            ?: connectionManager.getDiscoveredPeripheral(address)
        existingPeripheral?.let { peripheral ->
            // We already have connected to this peripheral
            getFileTransferClient(peripheral, connectionManager, completion)
        } ?: run {
            // If we don't have connected previously. Check if is a bonded peripheral or a discovered peripheral (because the method for connecting is different)

            val bondedBlePeripheralData =
                (bondedBlePeripherals.peripheralsData.value.firstOrNull { it.address == address })
            if (bondedBlePeripheralData != null) {
                // If is a bonded peripheral, reconnect
                connectionManager.reconnectToBondedBlePeripherals(setOf(address)) {
                    val fileTransferClient = connectionManager.getFileTransferClient(address)
                    if (fileTransferClient != null) {
                        // Success
                        getFileTransferClient(
                            fileTransferClient.peripheral,
                            connectionManager,
                            completion
                        )
                    } else {
                        // Failed
                        log.warning("Couldn't reconnect to bonded peripehral peripheral: $address")
                        completion(Result.failure(UnknownPeripheralGliderClientException(address)))
                    }
                }
            } else {
                // Is not a bonded peripheral. Scan again to try to discover it
                log.info("operation with unknown peripheral: $address")
                log.info(
                    "current discovered peripherals: ${connectionManager.peripherals.value.map { it.nameOrAddress }}"
                )

                // Try to scan peripherals to discover
                scope.launch {
                    log.info("discoverPeripherals launch")
                    bondedBlePeripherals.refresh()
                    connectionManager.startScan()
                    delay(2000)
                    log.info("discoverPeripherals delay finished")
                    connectionManager.stopScan()

                    log.info("updated discovered peripherals: ${connectionManager.peripherals.value.map { it.nameOrAddress }}")

                    // Connect if has been discovered
                    connectionManager.getDiscoveredPeripheral(address)?.let { peripheral ->
                        getFileTransferClient(peripheral, connectionManager, completion)
                    } ?: run {
                        log.warning("scan didn't find peripheral: $address")
                        completion(Result.failure(UnknownPeripheralGliderClientException(address)))
                    }
                }
            }
        }
    }

    /*
    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private fun reconnectToBondedBlePeripherals(
        bondedBlePeripheralData: BondedBlePeripherals.Data,
        connectionManager: ConnectionManager,
        completion: (isConnected: Boolean) -> Unit
    ) {
        connectionManager.reconnectToBondedBlePeripherals(
            setOf(bondedBlePeripheralData.address),
            completion
        )
    }*/

    private fun getFileTransferClient(
        peripheral: Peripheral,
        connectionManager: ConnectionManager,
        completion: ((Result<FileTransferClient>) -> Unit)
    ) {
        try {
            connectionManager.setSelectedPeripheral(peripheral) { result ->
                result.fold(
                    onSuccess = {
                        completion(Result.success(it))
                    },
                    onFailure = { exception ->
                        log.severe("Can't connect to peripheral: $address")
                        completion(Result.failure(exception))
                    }
                )
            }
        } catch (exception: SecurityException) {
            log.severe("Security exception when connecting to peripheral: $address")
            completion(Result.failure(exception))
        }
    }

    private class GliderListDirectoryOperation : GliderOperation() {
        override fun execute() {
            TODO("Not yet implemented")
        }
    }

    // endregion
}


