package com.adafruit.glider.provider

import com.adafruit.glider.utils.LogUtils
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

    private fun getFileTransferClient(
        connectionManager: ConnectionManager,
        bondedBlePeripherals: BondedBlePeripherals,
        completion: ((Result<FileTransferClient>) -> Unit)
    ) {

        connectionManager.getPeripheral(address)?.let { peripheral ->
            getFileTransferClient(peripheral, connectionManager, completion)
        } ?: run {
            log.info("operation with unknown peripheral: $address")
            log.info("current peripherals: ${connectionManager.peripherals.value.map { it.nameOrAddress }}")

            // Try to scan peripherals to discover
            scope.launch {
                log.info("discoverPeripherals launch")
                bondedBlePeripherals.refresh()
                connectionManager.startScan()
                delay(2000)
                log.info("discoverPeripherals delay finished")
                connectionManager.stopScan()

                val peripherals = connectionManager.peripherals.value
                log.info("current peripherals: ${peripherals.map { it.nameOrAddress }}")

                connectionManager.getPeripheral(address)?.let { peripheral ->
                    getFileTransferClient(peripheral, connectionManager, completion)
                } ?: run {
                    log.warning("scan didn't find peripheral: $address")
                    completion(Result.failure(UnknownPeripheralGliderClientException(address)))
                }
            }
        }
    }

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


