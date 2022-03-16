package com.adafruit.glider.ui.scan

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.openroad.ble.device.BlePeripheral
import io.openroad.ble.device.kFileTransferServiceUUID
import io.openroad.ble.scanner.isManufacturerAdafruit
import io.openroad.ble.scanner.BlePeripheralScanner
import io.openroad.utils.LogUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

const val kMinRssiToAutoConnect = -60 //-100                    // in dBM
const val kMinTimeDetectingPeripheralForAutoconnect = 1000L     // in millis

class ScanViewModel(
    application: Application,
) : AndroidViewModel(application) {
    // States
    sealed class ScanUiState {
        object Scanning : ScanUiState()
        data class ScanningError(val error: Throwable) : ScanUiState()
        object RestoringConnection : ScanUiState()
        object Connecting : ScanUiState()
        object Connected : ScanUiState()
        object Discovering : ScanUiState()
        data class FileTransferError(val error: Throwable) : ScanUiState()
        object FileTransferReady : ScanUiState()
        data class Disconnected(val error: Throwable) : ScanUiState()
    }

    // Data - Private
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val log by LogUtils()
    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Scanning)
    private var bleDeviceScanner =
        BlePeripheralScanner(getApplication(), null, viewModelScope, defaultDispatcher)
    private var scannerStartingTime = System.currentTimeMillis()
    private var autoConnectJob: Job? = null
    private var waitForScanStopToConnectJob: Job? = null

    // Data
    val uiState = _uiState.asStateFlow()
    val bleDevices =
        bleDeviceScanner.blePeripheralsState            // State with all the scanned devices
    val bleError =
        bleDeviceScanner.bleErrorState              // State with scanning error or null if no error found

    val numDevicesFound = bleDevices
        .map { bleDevices ->
            bleDevices.size
        }

    private val matchingDevicesFound = bleDevices
        .map { bleDevices ->
            // Only devices that are manufactured by Adafruit and include theFileTransfer service
            bleDevices
                .filter { it.scanRecord?.isManufacturerAdafruit() ?: false }
                .filter { it.scanRecord?.serviceUuids?.contains(kFileTransferServiceUUID) ?: false }
                .sortedBy { it.createdMillis }
        }

    /*
    private val numMatchingDevicesFound = matchingDevicesFound
        .map { bleDevices ->
            bleDevices.size
        }*/

    val numMatchingDevicesOutOfRangeFound = matchingDevicesFound
        .map { it.filter { it.rssi ?: -127 <= kMinRssiToAutoConnect }.size }
    val numMatchingDevicesInRangeFound = matchingDevicesFound
        .map { it.filter { it.rssi ?: -127 > kMinRssiToAutoConnect }.size }


    // region Actions
    fun startScanning() {
        scannerStartingTime = System.currentTimeMillis()
        bleDeviceScanner.start()

        // Start a job that checks if we should auto-connect
        waitForScanStopToConnectJob?.cancel()
        autoConnectJob?.cancel()
        autoConnectJob = matchingDevicesFound
            .onEach { bleDevices ->

                val currentTime = System.currentTimeMillis()
                val selectedDevice = bleDevices
                    .filter { it.createdMillis - currentTime >= kMinTimeDetectingPeripheralForAutoconnect }     // Take peripherals that have been matching more than kMinTimeDetectingPeripheralForAutoconnect seconds
                    .sortedByDescending { it.rssi }         // Sort by rssi
                    .firstOrNull()                          // Take first

                if (selectedDevice != null) {
                    // Connect
                    connect(selectedDevice)
                }
            }
            .flowOn(defaultDispatcher)
            .launchIn(viewModelScope)
    }

    fun stopScanning() {
        autoConnectJob?.cancel()
        autoConnectJob = null

        bleDeviceScanner.stop()
    }

    private fun connect(blePeripheral: BlePeripheral) {
        log.info("Connect to ${blePeripheral.nameOrAddress}")
        _uiState.update { ScanUiState.Connecting }

        stopScanning()

        // Wait some time until scanning is really stopped to avoid some connection problems
        val kTimeToWaitForScannerToStopBeforeConnection = 500L
        runBlocking {
            waitForScanStopToConnectJob = async(defaultDispatcher) {
                delay(kTimeToWaitForScannerToStopBeforeConnection)
                if (isActive) {
                    blePeripheral.connect(context = getApplication())
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