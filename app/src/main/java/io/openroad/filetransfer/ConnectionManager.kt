package io.openroad.filetransfer

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import com.adafruit.glider.utils.LogUtils
import io.openroad.Peripheral
import io.openroad.wifi.peripheral.WifiPeripheral
import io.openroad.wifi.scanner.WifiPeripheralScanner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ConnectionManager(
    private val wifiPeripheralScanner: WifiPeripheralScanner,
    private val externalScope: CoroutineScope = MainScope(),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    // Data - Private
    private val log by LogUtils()

    private var scanner =
        Scanner(
            wifiPeripheralScanner,
            externalScope,
            defaultDispatcher
        ) { wifiPeripheral, completion ->
            connect(wifiPeripheral = wifiPeripheral, completion = completion)
        }

    private var fileTransferClients: MutableMap<String, FileTransferClient> =
        mutableMapOf() // FileTransferClient for each peripheral

    private var _currentFileTransferClient = MutableStateFlow<FileTransferClient?>(null)
    private var _isReconnectingToCurrentPeripheral = MutableStateFlow(false)
    private var _peripherals = MutableStateFlow<List<Peripheral>>(emptyList())
    private var peripheralsUpdateJob: Job? = null

    // User selected client (or picked automatically by the system if user didn't pick or got disconnected)
    private var userSelectedTransferClient: FileTransferClient? = null


    // Data - Public
    var scanningState = scanner.scanningState

    val currentFileTransferClient = _currentFileTransferClient.asStateFlow()
    val isReconnectingToCurrentPeripheral = _isReconnectingToCurrentPeripheral.asStateFlow()
    val peripherals = _peripherals.asStateFlow()

    // region Actions
    fun startScan() {
        scanner.startScan()

        peripheralsUpdateJob = wifiPeripheralScanner.wifiPeripherals
            .onEach { wifiPeripherals ->
                _peripherals.update { wifiPeripherals }
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
            // Check if is a wifi peripheral and auto-connect
            val wifiPeripheral = peripheral as? WifiPeripheral
            if (wifiPeripheral != null) {
                connect(wifiPeripheral) {
                    // Select the newly connected peripheral
                    userSelectedTransferClient = it
                    updateSelectedPeripheral()
                }
            }
        }
    }

    // endregion


    // region
    private fun connect(wifiPeripheral: WifiPeripheral, completion: (FileTransferClient?) -> Unit) {

        // Create the fileTransferClient and that's all
        val wifiFileTransferPeripheral = WifiFileTransferPeripheral(wifiPeripheral)
        val fileTransferClient = FileTransferClient(wifiFileTransferPeripheral)
        fileTransferClients[wifiPeripheral.address] = fileTransferClient

        updateSelectedPeripheral()

        completion(fileTransferClient)
    }

    private fun updateSelectedPeripheral() {

        // Update selectedFileTransferClient
        _currentFileTransferClient.update {
            userSelectedTransferClient ?: fileTransferClients.values.firstOrNull()
        }

        log.info("selectedPeripheral: ${currentFileTransferClient.value?.peripheral?.nameOrAddress}")

        // Check that the selected client corresponds to the selected peripheral
        // TODO
    }

    // endregion
}


/*
externalScope.launch {
    var baseUrl = "http://" + wifiPeripheral.address
    if (wifiPeripheral.port != 80) {
        baseUrl += ":" + wifiPeripheral.port
    }
    log.info("base address: $baseUrl")
    val networkService =
        FileTransferNetworkService.getInstance(context = context, baseUrl = baseUrl)
    try {
        val version = networkService.getVersion().string()
        log.info("getVersion(): $version")
    } catch (exception: Exception) {
        log.severe("Error getVersion(): $exception")
    }
}*/