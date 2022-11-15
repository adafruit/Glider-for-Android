package io.openroad.filetransfer.filetransfer

import androidx.lifecycle.viewModelScope
import com.adafruit.glider.utils.LogUtils
import io.openroad.filetransfer.Peripheral
import io.openroad.filetransfer.ble.peripheral.BlePeripheral
import io.openroad.filetransfer.ble.scanner.BlePeripheralScanner
import io.openroad.filetransfer.ble.scanner.isManufacturerAdafruit
import io.openroad.filetransfer.wifi.peripheral.WifiPeripheral
import io.openroad.filetransfer.wifi.scanner.WifiPeripheralScanner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

const val kMinRssiToAutoConnect = -90//-80                   // in dBM
const val isWifiScanEnabled = true
const val isBleScanEnabled = true

class Scanner(
    private val blePeripheralScanner: BlePeripheralScanner,
    private val wifiPeripheralScanner: WifiPeripheralScanner,
    private val externalScope: CoroutineScope = MainScope(),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    sealed class ScanningState {
        object Idle : ScanningState()
        data class Scanning(val peripherals: List<Peripheral>) : ScanningState()
        data class ScanningError(val cause: Throwable) : ScanningState()
    }

    // Data - Private
    private val log by LogUtils()
    private var scanningStateWifiJob: Job? = null
    private var scanningStateBleJob: Job? = null

    private var scanningExceptionDetectorJob: Job? = null

    private var _scanningState = MutableStateFlow<ScanningState>(ScanningState.Idle)

    private var scannedWifiPeripherals: List<WifiPeripheral> =
        emptyList()     // Cached list of peripherals
    private var scannedBlePeripherals: List<BlePeripheral> =
        emptyList()     // Cached list of peripherals

    // Data - Public
    //var isScanning = wifiPeripheralScanner.isRunning || blePeripheralScanner.isRunning
    val scanningState = _scanningState.asStateFlow()
    val isScanning = _scanningState.map { it is ScanningState.Scanning }.stateIn(
        externalScope, SharingStarted.Lazily, scanningState.value is ScanningState.Scanning
    )

    // region Actions
    fun startScan() {
        if (_scanningState.value is ScanningState.Scanning) return

        // Clean
        _scanningState.update { ScanningState.Scanning(emptyList()) }

        // Start Wifi Scan
        if (isWifiScanEnabled) {
            scanningStateWifiJob =
                wifiPeripheralScanner.wifiPeripheralsFlow
                    .onStart { log.info("wifiPeripheralsFlow start") }
                    .onEach { wifiPeripherals ->
                        scannedWifiPeripherals = wifiPeripherals.sortedBy { it.createdMillis }
                        updateScanningState()
                    }.onCompletion { exception ->
                        val cause = exception?.cause
                        log.info("wifiPeripheralsFlow completion: $cause")
                    }.flowOn(defaultDispatcher).launchIn(externalScope)
        }

        // Start Bluetooth Scan
        if (isBleScanEnabled) {
            scanningStateBleJob =
                blePeripheralScanner.blePeripheralsFlow
                    .onStart { log.info("blePeripheralsFlow start") }
                    .onEach { blePeripherals ->
                        val filteredPeripherals = blePeripherals
                            // Only peripherals that are manufactured by Adafruit
                            .filter { blePeripheral ->
                                blePeripheral.scanRecord()?.isManufacturerAdafruit() ?: false
                            }
                            // Only peripherals than include the FileTransfer service
                            .filter { blePeripheral ->
                                val serviceUuids: List<UUID>? =
                                    blePeripheral.scanRecord()?.serviceUuids?.map { it.uuid }
                                serviceUuids?.contains(kFileTransferServiceUUID) ?: false
                            }
                            /*.map {
                                log.info("found: ${it.nameOrAddress} -> rssi: ${it.rssi.value}")
                                it
                            }*/
                            // Only peripherals that are closer than kMinRssiToAutoConnect
                            .filter { it.rssi.value > kMinRssiToAutoConnect }

                        scannedBlePeripherals = filteredPeripherals.sortedBy { it.createdMillis }
                        updateScanningState()
                    }.onCompletion { exception ->
                        val cause = exception?.cause
                        log.info("blePeripheralsFlow completion: $cause")
                    }.flowOn(defaultDispatcher).launchIn(externalScope)
        }

/*
        // Listen to scanning errors and map it to the UI state
        scanningExceptionDetectorJob = externalScope.launch {
            wifiPeripheralScanner.wifiLastException
                .filterNotNull()
                .collect { wifiException ->
                    _scanningState.update {
                        ScanningState.ScanningError(wifiException)
                    }
                    stopScan()
                }
        }
*/
    }

    /*
        Update scanning state merging results from wifi peripherals and bluetooth peripherals
     */
    @Synchronized
    private fun updateScanningState() {
        val allPeripherals = scannedWifiPeripherals + scannedBlePeripherals
        _scanningState.update { ScanningState.Scanning(allPeripherals) }
    }

    fun stopScan() {
        // Clean
        scanningStateWifiJob?.cancel()
        scanningStateWifiJob = null
        scanningStateBleJob?.cancel()
        scanningStateBleJob = null

        scanningExceptionDetectorJob?.cancel()
        scanningExceptionDetectorJob = null
    }
}