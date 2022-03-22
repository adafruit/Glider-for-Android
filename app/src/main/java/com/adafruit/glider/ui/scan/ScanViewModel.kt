package com.adafruit.glider.ui.scan

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.openroad.ble.filetransfer.BleFileTransferPeripheral
import io.openroad.ble.filetransfer.kFileTransferServiceUUID
import io.openroad.ble.peripheral.BlePeripheral
import io.openroad.ble.scanner.BlePeripheralScanner
import io.openroad.ble.scanner.isManufacturerAdafruit
import io.openroad.utils.LogUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

const val kMinRssiToAutoConnect = -80 //-100                    // in dBM
const val kMinTimeDetectingPeripheralForAutoconnect = 1000L     // in millis

class ScanViewModel(
    application: Application,
) : AndroidViewModel(application) {
    // States
    sealed class ScanUiState {
        object Scanning : ScanUiState()
        data class ScanningError(val cause: Throwable) : ScanUiState()
        object RestoringConnection : ScanUiState()
        object SetupConnection : ScanUiState()
        object Connecting : ScanUiState()
        object Connected : ScanUiState()
        object CheckingFileTransferVersion : ScanUiState()
        object SetupFileTransfer: ScanUiState()
        object Discovering : ScanUiState()
        data class FileTransferError(val cause: Throwable) : ScanUiState()
        object FileTransferReady : ScanUiState()
        data class Disconnected(val cause: Throwable?) : ScanUiState()
    }

    // Data - Private
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val log by LogUtils()
    private var scannerStartingTime = System.currentTimeMillis()
    private var autoConnectJob: Job? = null
    private var stoppingScannerDelayBeforeConnectingJob: Job? = null
    private var fileTransferPeripheralStateJob: Job? = null

    private var blePeripheralScanner =
        BlePeripheralScanner(getApplication(), null, viewModelScope, defaultDispatcher)

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Scanning)

    // Data
    val uiState = _uiState.asStateFlow()

    private val blePeripherals =
        blePeripheralScanner.blePeripheralsState            // State with all the scanned peripherals

    val numPeripheralsFound = blePeripherals
        .map { blePeripherals ->
            blePeripherals.size
        }

    private val matchingPeripheralsFound = blePeripherals
        .map { blePeripherals ->
            // Only peripherals that are manufactured by Adafruit and include theFileTransfer service
            blePeripherals
                .filter { it.scanRecord?.isManufacturerAdafruit() ?: false }
                /*
            .map {
                log.info("found: ${it.nameOrAddress} -> rssi: ${it.currentRssi}")
                it
            }*/
                .filter {
                    val serviceUuids: List<UUID>? = it.scanRecord?.serviceUuids?.map { it.uuid }
                    serviceUuids?.contains(kFileTransferServiceUUID) ?: false
                }
                .sortedBy { it.createdMillis }

        }

    /*
    private val numMatchingDevicesFound = matchingDevicesFound
        .map { blePeripherals ->
            blePeripherals.size
        }*/

    val numMatchingPeripheralsOutOfRangeFound = matchingPeripheralsFound
        .map { it.filter { it.currentRssi <= kMinRssiToAutoConnect }.size }
    val numMatchingPeripheralsInRangeFound = matchingPeripheralsFound
        .map { it.filter { it.currentRssi > kMinRssiToAutoConnect }.size }


    init {
        // Listen to scanning errors and map it to the UI state
        viewModelScope.launch {
            blePeripheralScanner.bleErrorException
                .filterNotNull()
                .collect { bleException ->
                    _uiState.update { ScanUiState.ScanningError(bleException) }
                }
        }
    }

    // region Actions
    fun startScanning() {
        scannerStartingTime = System.currentTimeMillis()
        blePeripheralScanner.start()

        // Start a job that checks if we should auto-connect
        stoppingScannerDelayBeforeConnectingJob?.cancel()
        autoConnectJob?.cancel()
        autoConnectJob = matchingPeripheralsFound
            .onEach { blePeripherals ->

                val currentTime = System.currentTimeMillis()
                val selectedPeripheral =
                    blePeripherals
/*
                        .map {
                            log.info("found: ${it.nameOrAddress} -> rssi: ${it.currentRssi} - elapsed: ${it.createdMillis - currentTime}")
                            it
                        }*/
                        // Take peripherals that have been matching more than kMinTimeDetectingPeripheralForAutoconnect
                        .filter { currentTime - it.createdMillis > kMinTimeDetectingPeripheralForAutoconnect }
                        // Take the one with higher RSSI
                        .maxByOrNull { it.currentRssi }


                if (selectedPeripheral != null) {
                    // Connect
                    connect(selectedPeripheral)
                }
            }
            .flowOn(defaultDispatcher)
            .launchIn(viewModelScope)
    }

    fun stopScanning() {
        autoConnectJob?.cancel()
        autoConnectJob = null

        blePeripheralScanner.stop()
    }


    private fun connect(blePeripheral: BlePeripheral) {
        log.info("Connect to ${blePeripheral.nameOrAddress}")
        _uiState.update { ScanUiState.SetupConnection }

        stopScanning()

        // Wait some time until scanning is really stopped to avoid some connection problems
        val kTimeToWaitForScannerToStopBeforeConnection = 500L
        runBlocking {
            stoppingScannerDelayBeforeConnectingJob = async(defaultDispatcher) {
                delay(kTimeToWaitForScannerToStopBeforeConnection)
                if (isActive) {
                    val fileTransferPeripheral = BleFileTransferPeripheral(blePeripheral)


                    fileTransferPeripheralStateJob = viewModelScope.launch {
                        fileTransferPeripheral.fileTransferState
                            .collect { fileTransferState ->
                                when (fileTransferState) {
                                    is BleFileTransferPeripheral.FileTransferState.Connecting -> {
                                        _uiState.update { ScanUiState.Connecting }
                                    }
                                    is BleFileTransferPeripheral.FileTransferState.Disconnected -> {
                                        _uiState.update { ScanUiState.Disconnected(fileTransferState.cause) }
                                        cancel()
                                    }
                                    BleFileTransferPeripheral.FileTransferState.CheckingFileTransferVersion -> {
                                        _uiState.update { ScanUiState.CheckingFileTransferVersion }
                                    }
                                    BleFileTransferPeripheral.FileTransferState.EnablingNotifications -> {
                                        _uiState.update { ScanUiState.SetupFileTransfer }
                                    }
                                    is BleFileTransferPeripheral.FileTransferState.Disconnecting -> {}
                                    BleFileTransferPeripheral.FileTransferState.Discovering -> {
                                        _uiState.update { ScanUiState.Discovering }
                                    }
                                }
                            }
                    }

                    fileTransferPeripheral.connect()
                }
            }
        }
    }

    // endregion
}

/*
// region Factory
class ScanViewModelFactory(private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return ScanViewModelFactory(defaultDispatcher) as T
    }
}
// endregion
 */