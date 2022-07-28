package io.openroad.filetransfer

import io.openroad.Peripheral
import io.openroad.wifi.peripheral.WifiPeripheral
import io.openroad.wifi.scanner.WifiPeripheralScanner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class Scanner(
    private val wifiPeripheralScanner: WifiPeripheralScanner,
    private val externalScope: CoroutineScope = MainScope(),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connect: (wifiPeripheral: WifiPeripheral, completion: (FileTransferClient?) -> Unit) -> Unit
) {
    sealed class ScanningState {
        object Idle : ScanningState()
        data class Scanning(val peripherals: List<Peripheral>) : ScanningState()
        data class ScanningError(val cause: Throwable) : ScanningState()
        data class PreparingConnection(
            val peripherals: List<Peripheral>,
            val selectedPeripheral: Peripheral
        ) : ScanningState()

        data class Connecting(val peripheral: Peripheral) : ScanningState()
        data class Connected(val client: FileTransferClient) : ScanningState()
        data class FileTransferEnabled(val client: FileTransferClient) : ScanningState()
    }

    // Data - Private
    private var scanningStateJob: Job? = null
    private var delayBeforeConnectingJob: Job? = null
    private var scanningExceptionDetectorJob: Job? = null

    private var _scanningState = MutableStateFlow<ScanningState>(ScanningState.Idle)
    private var scannedWifiPeripherals = wifiPeripheralScanner.wifiPeripherals

    //private var autoConnectSelectedPeripheral: WifiPeripheral? =  null   // Selected peripheral, awaiting minTimeDetectingPeripheralForAutoConnect to expire to be connected


    // Data - Public
    var isAutoConnectEnabled = true
    var minTimeDetectingPeripheralForAutoConnect = 4500L     // in millis

    var isScanning = wifiPeripheralScanner.isRunning
    val scanningState = _scanningState.asStateFlow()

    // region Actions
    fun startScan() {
        // Clean
        delayBeforeConnectingJob?.cancel()
        _scanningState.update { ScanningState.Scanning(emptyList()) }

        // Start Wifi scan
        wifiPeripheralScanner.start()

        scanningStateJob = scannedWifiPeripherals
            .onEach { wifiPeripherals ->
                if (scanningState.value !is ScanningState.Scanning) return@onEach

                _scanningState.update { ScanningState.Scanning(wifiPeripherals) }

                if (isAutoConnectEnabled) {// && autoConnectSelectedPeripheral == null) {
                    wifiPeripherals.firstOrNull()?.let { selectedPeripheral ->
                        //autoConnectSelectedPeripheral = selectedPeripheral
                        delayedConnect(
                            peripherals = wifiPeripherals,
                            selectedPeripheral = selectedPeripheral,
                            delay = minTimeDetectingPeripheralForAutoConnect
                        )
                    }
                }
            }
            .flowOn(defaultDispatcher)
            .launchIn(externalScope)

        // Listen to scanning errors and map it to the UI state
        scanningExceptionDetectorJob = externalScope.launch {
            wifiPeripheralScanner.wifiLastException
                .filterNotNull()
                .collect { wifiException ->
                    _scanningState.update {
                        Scanner.ScanningState.ScanningError(wifiException)
                    }
                    stopScan()
                }
        }
    }

    fun stopScan() {
        // Clean
        scanningStateJob?.cancel()
        delayBeforeConnectingJob?.cancel()
        scanningExceptionDetectorJob?.cancel()
        scanningExceptionDetectorJob = null
        //autoConnectSelectedPeripheral = null

        // Stop Wifi scan
        wifiPeripheralScanner.stop()
    }

    private fun delayedConnect(
        peripherals: List<WifiPeripheral>,
        selectedPeripheral: WifiPeripheral,
        delay: Long
    ) {
        delayBeforeConnectingJob?.cancel()

        _scanningState.update {
            ScanningState.PreparingConnection(
                peripherals,
                selectedPeripheral
            )
        }

        // Wait
        externalScope.launch {
            delayBeforeConnectingJob = async(defaultDispatcher) {
                delay(delay)
                if (isActive) {

                    // Connect
                    delayBeforeConnectingJob?.cancel()
                    stopScan()
                    _scanningState.update { ScanningState.Connecting(selectedPeripheral) }
                    connect(selectedPeripheral) { fileTransferClient ->
                        _scanningState.update {
                            if (fileTransferClient != null) {
                                ScanningState.FileTransferEnabled(fileTransferClient)
                            } else {
                                ScanningState.ScanningError(Exception("Error connecting to: ${selectedPeripheral.nameOrAddress}"))
                            }
                        }
                    }
                }
            }
        }
    }
}