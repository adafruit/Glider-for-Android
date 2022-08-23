package io.openroad.filetransfer

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.content.Context
import androidx.annotation.RequiresPermission
import com.adafruit.glider.utils.LogUtils
import io.openroad.Peripheral
import io.openroad.ble.peripheral.BlePeripheral
import io.openroad.ble.scanner.BlePeripheralScanner
import io.openroad.ble.utils.BleManager
import io.openroad.wifi.peripheral.WifiPeripheral
import io.openroad.wifi.scanner.WifiPeripheralScanner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private const val reconnectTimeout = 5000

class ConnectionManager(
    private val context: Context,
    blePeripheralScanner: BlePeripheralScanner,
    wifiPeripheralScanner: WifiPeripheralScanner,
    private val onBlePeripheralBonded: ((name: String?, address: String) -> Unit)? = null,
    private val onWifiPeripheralGetPasswordForHostName: ((name: String, hostName: String) -> String?)? = null,
    private val externalScope: CoroutineScope = MainScope(),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /*
    sealed class ConnectionState {
        object Idle : ConnectionState()
        data class Scanning(val peripherals: List<Peripheral>) : ConnectionState()
        data class ConnectionError(val cause: Throwable) : ConnectionState()
    }*/

    // Data - Private
    private val log by LogUtils()

    private var scanner = Scanner(
        blePeripheralScanner,
        wifiPeripheralScanner,
        externalScope,
        defaultDispatcher
    )

    private var fileTransferClients: MutableMap<String, FileTransferClient> =
        mutableMapOf() // FileTransferClient for each peripheral

    private var _currentFileTransferClient = MutableStateFlow<FileTransferClient?>(null)
    private var _isReconnectingToBondedPeripherals = MutableStateFlow(false)
    private var _peripherals = MutableStateFlow<List<Peripheral>>(emptyList())
    private var peripheralsUpdateJob: Job? = null
    private var managedBlePeripherals: MutableList<BleFileTransferPeripheral> = mutableListOf()
    private var _peripheralAddressesBeingSetup = MutableStateFlow<List<String>>(emptyList())
    private val _connectionLastException = MutableStateFlow<Exception?>(null)

    // Is reconnecting the peripheral with identifier
    private var isReconnectingPeripheral: MutableMap<String, Boolean> = mutableMapOf()

    // User selected client (or picked automatically by the system if user didn't pick or got disconnected)
    private var userSelectedTransferClient: FileTransferClient? = null

    // Data - Public
    var scanningState = scanner.scanningState
    var isScanning = scanner.isScanning
    val bleScanningLastException = blePeripheralScanner.bleLastException
    val wifiScanningLastException = wifiPeripheralScanner.wifiLastException
    val connectionLastException = _connectionLastException.asStateFlow()

    val peripherals = _peripherals.asStateFlow()

    val currentFileTransferClient = _currentFileTransferClient.asStateFlow()
    val isReconnectingToBondedPeripherals = _isReconnectingToBondedPeripherals.asStateFlow()
    val peripheralAddressesBeingSetup = _peripheralAddressesBeingSetup.asStateFlow()

    // region Actions
    fun startScan() {
        scanner.startScan()

        peripheralsUpdateJob = scanningState
            .onEach { state ->
                _peripherals.update {
                    if (state is Scanner.ScanningState.Scanning) {
                        state.peripherals
                    } else {
                        emptyList()
                    }
                }
            }
            .flowOn(defaultDispatcher)
            .launchIn(externalScope)
    }

    fun stopScan() {
        // Clean
        peripheralsUpdateJob?.cancel()
        peripheralsUpdateJob = null

        scanner.stopScan()
    }

    fun setSelectedPeripheral(peripheral: Peripheral) {
        fileTransferClients[peripheral.address]?.let {
            userSelectedTransferClient = it
            updateSelectedPeripheral()
        } ?: run {
            connect(peripheral) {
                // Select the newly connected peripheral
                userSelectedTransferClient = it
                updateSelectedPeripheral()
            }
        }
    }
    // endregion

    // region Actions
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    fun reconnectToBondedBlePeripherals(
        knownAddresses: Set<String>,
        completion: (isConnected: Boolean) -> Unit
    ) {
        // Filter-out addresses that are not bonded
        val pairedAddresses: Set<String> =
            BleManager.getPairedPeripherals(context)?.map { it.address }?.toSet() ?: emptySet()
        val addresses = knownAddresses.intersect(pairedAddresses)

        if (addresses.isNotEmpty()) {
            _isReconnectingToBondedPeripherals.update { true }
            _peripheralAddressesBeingSetup.update {
                val newList = it.toMutableList(); newList.addAll(addresses); newList
            }

            BleManager.reconnectToPeripherals(
                context = context,
                externalScope = externalScope,
                addresses = addresses,
                connectionTimeout = reconnectTimeout,
                onBonded = { name, address -> onBlePeripheralBonded?.invoke(name, address) },
            ) { readyFileTransferPeripherals ->
                _isReconnectingToBondedPeripherals.update { false }
                _peripheralAddressesBeingSetup.update {
                    val newList = it.toMutableList(); newList.removeAll(addresses); newList
                }

                if (readyFileTransferPeripherals.isNotEmpty()) {
                    val firstConnectedBleFileTransferPeripheral =
                        readyFileTransferPeripherals.first()
                    log.info("Reconnected to ${firstConnectedBleFileTransferPeripheral.nameOrAddress}")

                    readyFileTransferPeripherals.forEach { bleFileTransferPeripheral ->
                        fileTransferClients[bleFileTransferPeripheral.address] =
                            FileTransferClient(bleFileTransferPeripheral)

                        addPeripheralToAutomaticallyManagedBleConnection(bleFileTransferPeripheral)
                    }

                    setSelectedPeripheral(firstConnectedBleFileTransferPeripheral.peripheral)
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

    private fun connect(peripheral: Peripheral, completion: (FileTransferClient?) -> Unit) {
        // Create the fileTransferClient and that's all
        var fileTransferPeripheral: FileTransferPeripheral? = null
        when (peripheral) {
            is WifiPeripheral -> fileTransferPeripheral = WifiFileTransferPeripheral(peripheral, onGetPasswordForHostName = onWifiPeripheralGetPasswordForHostName)
            is BlePeripheral -> fileTransferPeripheral = BleFileTransferPeripheral(peripheral, onBonded = onBlePeripheralBonded)
        }

        if (fileTransferPeripheral == null) {
            log.severe("Error: can't connect to unknown peripheral type")
            completion(null)
            return
        }

        // Connect
        connect(fileTransferPeripheral = fileTransferPeripheral, completion = completion)
    }

    fun connect(
        fileTransferPeripheral: FileTransferPeripheral,
        completion: (FileTransferClient?) -> Unit
    ) {
        _peripheralAddressesBeingSetup.update {
            val newList = it.toMutableList()
            newList.add(fileTransferPeripheral.peripheral.address)
            newList
        }
        fileTransferPeripheral.connectAndSetup(
            externalScope = externalScope,
            //onSetupDataReceived = { name, address -> onSetupDataReceived(name, address, fileTransferPeripheral.peripheral.type) },
        ) { isConnected ->
            log.info("FileTransferClient connect result: $isConnected")
            _peripheralAddressesBeingSetup.update {
                val newList =
                    it.toMutableList(); newList.remove(fileTransferPeripheral.peripheral.address); newList
            }

            if (isConnected) {
                val fileTransferClient = FileTransferClient(fileTransferPeripheral)
                fileTransferClients[fileTransferPeripheral.peripheral.address] = fileTransferClient

                updateSelectedPeripheral()

                // If is a Bluetooth Peripheral, add it to managed connections
                (fileTransferPeripheral as? BleFileTransferPeripheral)?.let {
                    addPeripheralToAutomaticallyManagedBleConnection(it)
                }

                completion(fileTransferClient)
            } else {
                // TODO change callbacks to add internal exception when it fails instead of just isConnected
                _connectionLastException.update { Exception("Can't connect to ${fileTransferPeripheral.peripheral.nameOrAddress}") }
                completion(null)
            }
        }
    }

    fun disconnectFileTransferClient(address: String) {
        // Disconnect if exists
        fileTransferClients[address]?.peripheral?.disconnect(null)
    }


    fun updateWifiPeripheralPassword(address: String, newPassword: String): Boolean {
        val wifiFileTransferPeripheral = fileTransferClients[address]?.fileTransferPeripheral as? WifiFileTransferPeripheral
        if (wifiFileTransferPeripheral != null) {
            wifiFileTransferPeripheral.password = newPassword
            return true         // Successfully changed
        }
        else {
            return false        // The peripheral is not in connectionManager
        }
    }

    // endregion

    private fun updateSelectedPeripheral() {
        // Update selectedFileTransferClient
        _currentFileTransferClient.update {
            userSelectedTransferClient ?: fileTransferClients.values.firstOrNull()
        }

        log.info("selectedPeripheral: ${currentFileTransferClient.value?.peripheral?.nameOrAddress}")
    }

    // region Managed Peripherals
    private fun addPeripheralToAutomaticallyManagedBleConnection(
        fileTransferPeripheral: BleFileTransferPeripheral
    ) {
        // Check that doesn't already exists
        if (managedBlePeripherals.firstOrNull { it.address == fileTransferPeripheral.address } != null) {
            log.info("trying to add an already managed peripheral: ${fileTransferPeripheral.nameOrAddress}")
            return
        }

        managedBlePeripherals.add(fileTransferPeripheral)

        externalScope.launch {
            fileTransferPeripheral.fileTransferState.collect { fileTransferState ->
                when (fileTransferState) {

                    BleFileTransferPeripheral.FileTransferState.Enabled -> {
                        isReconnectingPeripheral[fileTransferPeripheral.address] = false

                        fileTransferClients[fileTransferPeripheral.address] =
                            FileTransferClient(fileTransferPeripheral)

                        // If it is the selectedPeripheral, then the reconnection finished successfully
                        if (currentFileTransferClient.value?.peripheral?.address == fileTransferPeripheral.address) {
                            _isReconnectingToBondedPeripherals.update { false }
                        }

                        updateSelectedPeripheral()
                    }

                    is BleFileTransferPeripheral.FileTransferState.Disconnected -> {
                        if (isReconnectingPeripheral[fileTransferPeripheral.address] == true) {
                            log.info("recover failed for ${fileTransferPeripheral.address}")
                            setReconnectionFailed(fileTransferPeripheral.address)

                            updateSelectedPeripheral()
                        }
                        // If it was the selected peripheral -> try to recover the connection because a peripheral can be disconnected momentarily when writing to the filesystem.
                        else if (currentFileTransferClient.value?.peripheral?.address == fileTransferPeripheral.address) {
                            currentFileTransferClient.value?.let { selectedFileTransferClient ->
                                userSelectedTransferClient = null
                                //updateSelectedPeripheral()

                                // Wait for recovery before connecting to a different one
                                log.info("Try to recover disconnected peripheral: ${selectedFileTransferClient.peripheral.nameOrAddress}")
                                _isReconnectingToBondedPeripherals.update { true }

                                // Reconnect
                                removePeripheralFromAutomaticallyManagedConnection(
                                    fileTransferPeripheral.address
                                )
                                isReconnectingPeripheral[fileTransferPeripheral.address] = true
                                BleManager.reconnectToPeripherals(
                                    context = context,
                                    externalScope = externalScope,
                                    addresses = setOf(fileTransferPeripheral.address),
                                    connectionTimeout = reconnectTimeout,
                                    onBonded = { name, address -> onBlePeripheralBonded?.invoke(name, address) },
                                ) { reconnectedPeripherals ->

                                    if (reconnectedPeripherals.isEmpty()) {
                                        log.info("recover failed. Auto-select another peripheral")
                                        fileTransferClients.remove(fileTransferPeripheral.address)
                                        updateSelectedPeripheral()
                                        _isReconnectingToBondedPeripherals.update { false }
                                    } else {
                                        reconnectedPeripherals.forEach {
                                            addPeripheralToAutomaticallyManagedBleConnection(it)
                                        }
                                    }

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
                                context = context,
                                externalScope = externalScope,
                                addresses = setOf(fileTransferPeripheral.address),
                                connectionTimeout = reconnectTimeout,
                                onBonded = { name, address -> onBlePeripheralBonded?.invoke(name, address) },
                            ) { reconnectedPeripherals ->

                                reconnectedPeripherals.forEach {
                                    addPeripheralToAutomaticallyManagedBleConnection(it)
                                }

                                if (reconnectedPeripherals.isEmpty()) {
                                    fileTransferClients.remove(fileTransferPeripheral.address)
                                }
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
        if (currentFileTransferClient.value?.peripheral?.address == address) {
            _isReconnectingToBondedPeripherals.update { false }
        }
        fileTransferClients.remove(address)     // Remove info from disconnected peripheral
    }

    fun clean() {
        managedBlePeripherals.map { removePeripheralFromAutomaticallyManagedConnection(it) }
    }

    private fun removePeripheralFromAutomaticallyManagedConnection(address: String) {
        managedBlePeripherals.firstOrNull { it.address == address }?.let {
            removePeripheralFromAutomaticallyManagedConnection(it)
        }
    }

    private fun removePeripheralFromAutomaticallyManagedConnection(bleFileTransferPeripheral: BleFileTransferPeripheral) {
        managedBlePeripherals.remove(bleFileTransferPeripheral)
        //managedPeripheralsJobs.remove(bleFileTransferPeripheral.address)?.cancel()
    }

// endregion
}

