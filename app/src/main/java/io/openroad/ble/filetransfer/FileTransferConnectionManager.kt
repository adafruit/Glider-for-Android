package io.openroad.ble.filetransfer

import io.openroad.ble.BleManager
import io.openroad.ble.FileTransferClient
import io.openroad.ble.applicationContext
import io.openroad.ble.peripheral.BlePeripheral
import io.openroad.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Created by Antonio García (antonio@openroad.es)
 */
object FileTransferConnectionManager {
    // Config
    private const val reconnectTimeout = 5000

    // Data - Private
    private val log by LogUtils()
    private val externalScope: CoroutineScope = MainScope()

    private var managedPeripherals: MutableList<BleFileTransferPeripheral> = mutableListOf()
    private var managedPeripheralsJobs: MutableMap<String, Job> = mutableMapOf()
    //private var recoveryPeripheralIdentifier: String? = null        // Peripheral that was recently disconnected and that we are trying to reconnect. There is a timer

    // FileTransferClient for each peripheral
    private var fileTransferClients: MutableMap<String, FileTransferClient> = mutableMapOf()

    // Is reconnecting the peripheral with identifier
    private var isReconnectingPeripheral: MutableMap<String, Boolean> = mutableMapOf()

    // User selected client (or picked automatically by the system if user didn't pick or got disconnected)
    private var userSelectedTransferClient: FileTransferClient? = null

    // Data

    // Selected peripheral from all the connected peripherals. User can select it using
    private var _selectedFileTransferClient = MutableStateFlow<FileTransferClient?>(null)
    val selectedFileTransferClient = _selectedFileTransferClient.asStateFlow()

    private var _isSelectedPeripheralReconnecting = MutableStateFlow(false)
    val isSelectedPeripheralReconnecting = _isSelectedPeripheralReconnecting.asStateFlow()

    //private var _isConnectedOrReconnecting = MutableStateFlow(false)
    //val isConnectedOrReconnecting = _isConnectedOrReconnecting.asStateFlow()

    private var _isAnyPeripheralConnectingOrConnected = MutableStateFlow(false)
    val isAnyPeripheralConnectingOrConnected = _isAnyPeripheralConnectingOrConnected.asStateFlow()

    private var _connectedPeripherals =
        MutableStateFlow<List<BleFileTransferPeripheral>>(emptyList())
    val connectedPeripherals = _connectedPeripherals.asStateFlow()

    // region Actions
    fun setSelectedPeripheral(bleFileTransferPeripheral: BleFileTransferPeripheral) {

        fileTransferClients[bleFileTransferPeripheral.address]?.let {
            userSelectedTransferClient = it
            updateSelectedPeripheral()
        }
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
                addresses,
                connectionTimeout = reconnectTimeout,
            ) { readyFileTransferPeripherals ->
                _isSelectedPeripheralReconnecting.update { false }
                if (readyFileTransferPeripherals.isNotEmpty()) {
                    val firstConnectedBleFileTransferPeripheral =
                        readyFileTransferPeripherals.first()
                    log.info("Reconnected to ${firstConnectedBleFileTransferPeripheral.nameOrAddress}")

                    readyFileTransferPeripherals.forEach {
                        addPeripheralToAutomaticallyManagedConnection(it)
                    }

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

    // region Connection Management

    private fun updateConnectionStatus() {
        // Update isAnyPeripheralConnecting
        _isAnyPeripheralConnectingOrConnected.update { managedPeripherals.firstOrNull { it.connectionState.value.isConnectingOrConnected() } != null }

        _connectedPeripherals.update { managedPeripherals.filter { it.connectionState.value == BlePeripheral.ConnectionState.Connected } }

        // Update isConnectedOrReconnecting
        //_isConnectedOrReconnecting.update { _isAnyPeripheralConnecting.value || recoveryPeripheralIdentifier != null }

        log.info("isAnyPeripheralConnecting: ${isAnyPeripheralConnectingOrConnected.value}")
    }

    private fun updateSelectedPeripheral() {

        // Update selectedFileTransferClient
        _selectedFileTransferClient.update {
            userSelectedTransferClient ?: fileTransferClients.values.firstOrNull()
        }

        log.info("selectedPeripheral: ${selectedFileTransferClient.value?.peripheralName}")

        // Check that the selected client corresponds to the selected peripheral
        // TODO
    }

    // endregion

    // region Managed Peripherals

    fun addPeripheralToAutomaticallyManagedConnection(fileTransferPeripheral: BleFileTransferPeripheral) {
        // Check that doesn't already exists
        if (managedPeripherals.firstOrNull { it.address == fileTransferPeripheral.address } != null) {
            log.info("trying to add an already managed peripheral: ${fileTransferPeripheral.nameOrAddress}")
            return
        }

        managedPeripherals.add(fileTransferPeripheral)
        updateConnectionStatus()

        externalScope.launch {
            fileTransferPeripheral.fileTransferState.collect { fileTransferState ->
                when (fileTransferState) {
                    /*
                    BleFileTransferPeripheral.FileTransferState.Connecting -> {
                        isReconnectingPeripheral[fileTransferPeripheral.address] = true
                        updateConnectionStatus()
                    }*/
                    BleFileTransferPeripheral.FileTransferState.Enabled -> {
                        isReconnectingPeripheral[fileTransferPeripheral.address] = false

                        fileTransferClients[fileTransferPeripheral.address] =
                            FileTransferClient(fileTransferPeripheral)

                        // If it is the selectedPeripheral, then the reconnection finished successfully
                        if (selectedFileTransferClient.value?.address == fileTransferPeripheral.address) {
                            _isSelectedPeripheralReconnecting.update { false }
                        }

                        updateSelectedPeripheral()
                    }
                    /*
                    is BleFileTransferPeripheral.FileTransferState.Disconnecting -> {
                    }*/
                    is BleFileTransferPeripheral.FileTransferState.Disconnected -> {

                        if (isReconnectingPeripheral[fileTransferPeripheral.address] == true) {
                            log.info("recover failed for ${fileTransferPeripheral.address}")
                            setReconnectionFailed(fileTransferPeripheral.address)

                            /*
                            // If it was recovering then remove it because it failed
                            if (recoveryPeripheralIdentifier == fileTransferPeripheral.address) {
                                recoveryPeripheralIdentifier = null
                            }*/
                            updateSelectedPeripheral()
                            updateConnectionStatus()
                        }
                        // If it was the selected peripheral try to recover the connection because a peripheral can be disconnected momentarily when writing to the filesystem.
                        else if (selectedFileTransferClient.value?.address == fileTransferPeripheral.address) {
                            selectedFileTransferClient.value?.let { selectedFileTransferClient ->
                                userSelectedTransferClient = null
                                //updateSelectedPeripheral()

                                // Wait for recovery before connecting to a different one
                                log.info("Try to recover disconnected peripheral: ${selectedFileTransferClient.peripheralName}")
                                //recoveryPeripheralIdentifier = selectedFileTransferClient.address
                                _isSelectedPeripheralReconnecting.update { true }

                                // Reconnect
                                removePeripheralFromAutomaticallyManagedConnection(
                                    fileTransferPeripheral.address
                                )
                                isReconnectingPeripheral[fileTransferPeripheral.address] = true
                                BleManager.reconnectToPeripherals(
                                    applicationContext,
                                    addresses = setOf(fileTransferPeripheral.address),
                                    connectionTimeout = reconnectTimeout
                                ) { reconnectedPeripherals ->

                                    if (reconnectedPeripherals.isEmpty()) {
                                        log.info("recover failed. Autoselect another peripheral")
                                        fileTransferClients.remove(fileTransferPeripheral.address)
                                        updateSelectedPeripheral()
                                        _isSelectedPeripheralReconnecting.update { false }
                                    } else {
                                        reconnectedPeripherals.forEach {
                                            addPeripheralToAutomaticallyManagedConnection(it)
                                        }
                                    }

                                    updateConnectionStatus()
                                }
                            }
                        }
                        // Any other peripheral -> Also try to reconnect but status will not affect the selected client
                        else {
                            removePeripheralFromAutomaticallyManagedConnection(
                                fileTransferPeripheral.address
                            )
                            isReconnectingPeripheral[fileTransferPeripheral.address] = true
                            BleManager.reconnectToPeripherals(
                                applicationContext,
                                addresses = setOf(fileTransferPeripheral.address),
                                connectionTimeout = reconnectTimeout
                            ) { reconnectedPeripherals ->

                                reconnectedPeripherals.forEach {
                                    addPeripheralToAutomaticallyManagedConnection(it)
                                }

                                if (reconnectedPeripherals.isEmpty()) {
                                    fileTransferClients.remove(fileTransferPeripheral.address)
                                }

                                updateConnectionStatus()
                            }
                        }
                    }

                    is BleFileTransferPeripheral.FileTransferState.Error -> {
                        setReconnectionFailed(fileTransferPeripheral.address)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun setReconnectionFailed(address: String) {
        // If it the selectedPeripheral, then the reconnection failed
        if (selectedFileTransferClient.value?.address == address) {
            _isSelectedPeripheralReconnecting.update { false }
        }
        fileTransferClients.remove(address)     // Remove info from disconnected peripheral
    }

    fun clean() {
        managedPeripherals.map { removePeripheralFromAutomaticallyManagedConnection(it) }
    }

    fun removePeripheralFromAutomaticallyManagedConnection(address: String) {
        managedPeripherals.firstOrNull { it.address == address }?.let {
            removePeripheralFromAutomaticallyManagedConnection(it)
        }
    }

    fun removePeripheralFromAutomaticallyManagedConnection(bleFileTransferPeripheral: BleFileTransferPeripheral) {
        managedPeripherals.remove(bleFileTransferPeripheral)
        managedPeripheralsJobs.remove(bleFileTransferPeripheral.address)?.cancel()
    }

// endregion
}