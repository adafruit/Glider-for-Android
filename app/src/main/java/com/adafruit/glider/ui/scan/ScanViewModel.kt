package com.adafruit.glider.ui.scan

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.Manifest
import android.annotation.SuppressLint
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adafruit.glider.utils.LogUtils
import io.openroad.filetransfer.*
import io.openroad.filetransfer.ble.peripheral.BlePeripheral
import io.openroad.filetransfer.ble.utils.BleConnectionException
import io.openroad.filetransfer.filetransfer.*
import io.openroad.filetransfer.wifi.peripheral.WifiPeripheral
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

const val kPeriodForCheckingAutoConnect = 500L                  // in millis
const val kMinTimeDetectingPeripheralForAutoConnect = 1000L     // in millis

class ScanViewModel(
    val connectionManager: ConnectionManager,
    isAutoConnectEnabled: Boolean,
    private val onBlePeripheralBonded: ((name: String?, address: String) -> Unit)? = null,
    private val onWifiPeripheralGetPasswordForHostName: ((name: String, hostName: String) -> String?)? = null,
) : ViewModel() {
    // UI State
    sealed class UiState {
        object Startup : UiState()
        data class Scanning(val peripherals: List<Peripheral>) : UiState()
        data class Connecting(val peripheral: Peripheral, val description: String? = null) :
            UiState()

        data class Connected(val client: FileTransferClient) : UiState()
        data class FileTransferEnabled(val client: FileTransferClient) : UiState()
        data class Error(val cause: Throwable) : UiState()
    }

    // Internal state
    private data class ViewModelState(
        val connectionManagerState: Scanner.ScanningState,
        val selectedPeripheral: Peripheral?,
        val fileTransferClient: FileTransferClient?,
        val fileTransferState: BleFileTransferPeripheral.FileTransferState?,
        val error: Throwable?,
    ) {
        fun toUiState(): UiState {
            val result: UiState
            if (error != null) {
                result = UiState.Error(error)
            } else if (fileTransferState != null && fileTransferClient != null) {
                when (fileTransferState) {
                    BleFileTransferPeripheral.FileTransferState.Start ->
                        result = UiState.Connecting(fileTransferClient.peripheral)
                    BleFileTransferPeripheral.FileTransferState.Connecting ->
                        result = UiState.Connecting(fileTransferClient.peripheral)
                    is BleFileTransferPeripheral.FileTransferState.Disconnecting ->
                        result = UiState.Connecting(fileTransferClient.peripheral, "disconnecting")
                    is BleFileTransferPeripheral.FileTransferState.Disconnected ->
                        result = UiState.Error(
                            cause = fileTransferState.cause ?: Exception("Disconnected")
                        )
                    is BleFileTransferPeripheral.FileTransferState.GattError ->
                        result = UiState.Error(
                            cause = BleConnectionException(status = fileTransferState.gattErrorCode)
                        )
                    BleFileTransferPeripheral.FileTransferState.Discovering ->
                        result = UiState.Connecting(fileTransferClient.peripheral, "discovering")
                    BleFileTransferPeripheral.FileTransferState.CheckingFileTransferVersion ->
                        result =
                            UiState.Connecting(fileTransferClient.peripheral, "checking version")
                    BleFileTransferPeripheral.FileTransferState.Bonding ->
                        result = UiState.Connecting(fileTransferClient.peripheral, "bonding")
                    BleFileTransferPeripheral.FileTransferState.EnablingNotifications ->
                        result = UiState.Connecting(
                            fileTransferClient.peripheral,
                            "enabling notifications"
                        )
                    BleFileTransferPeripheral.FileTransferState.Enabled ->
                        result = UiState.FileTransferEnabled(fileTransferClient)
                    is BleFileTransferPeripheral.FileTransferState.Error ->
                        result = UiState.Error(
                            fileTransferState.cause ?: Exception("Error")
                        )
                    else -> {
                        result = UiState.Startup
                    }
                }
            } else if (fileTransferClient != null) {
                result = UiState.Connected(fileTransferClient)
            } else if (selectedPeripheral != null) {
                result = UiState.Connecting(selectedPeripheral)
            } else when (val state = connectionManagerState) {
                is Scanner.ScanningState.Scanning -> result = UiState.Scanning(state.peripherals)
                is Scanner.ScanningState.ScanningError -> result =
                    UiState.Error(state.cause)
                else -> result = UiState.Startup
            }

            return result
        }
    }

    // Data - Private
    private val viewModelState =
        MutableStateFlow(
            ViewModelState(
                connectionManagerState = connectionManager.scanningState.value,
                selectedPeripheral = null,
                fileTransferClient = null,
                fileTransferState = null,
                error = null,
            )
        )
    private val log by LogUtils()
    private var autoConnectJob: Job? = null
    private var delayBeforeConnectingJob: Job? = null
    private var fileTransferPeripheralStateJob: Job? = null

    // Data
    val uiState = viewModelState
        .map { it.toUiState() }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            viewModelState.value.toUiState()
        )


    // region Lifecycle
    init {
        // Monitor scanning
        viewModelScope.launch {
            connectionManager.scanningState.collect { state ->
                log.info("Connection state: $state")

                // Update internal state
                viewModelState.update { it.copy(connectionManagerState = state) }
            }
        }

        // Auto-connect
        if (isAutoConnectEnabled) {
            viewModelScope.launch {
                while (isActive) {

                    // Check if scanning and no selected peripheral and a candidate peripheral is available
                    val state = viewModelState.value
                    if (state.connectionManagerState is Scanner.ScanningState.Scanning && state.selectedPeripheral == null) {
                        val currentTime = System.currentTimeMillis()
                        val selectedPeripheral = state.connectionManagerState.peripherals
                            // Take peripherals that have been matching more than kMinTimeDetectingPeripheralForAutoConnect
                            .filter { currentTime - it.createdMillis > kMinTimeDetectingPeripheralForAutoConnect }
                            // Take the one that is closer
                            .maxByOrNull {
                                if (it is BlePeripheral) {
                                    // For bluetooth peripherals, take the one with higher RSSI
                                    it.rssi.value
                                } else {
                                    // Wifi peripherals are assumed to be father, so a bluetooth peripheral will always take precedence
                                    -1000// Smaller than -127 (min value for RSSI)
                                }
                            }

                        if (selectedPeripheral != null) {
                            try {
                                stopScanningAndConnect(selectedPeripheral)
                            }
                            catch (e: SecurityException) {
                                log.severe("Invalid permissions")
                            }
                        }
                    }

                    // Wait for next check
                    delay(kPeriodForCheckingAutoConnect)
                }
            }
        }
    }

    fun onResume() {
        // Start scanning if we are in the scanning state
        if (uiState.value == UiState.Startup || uiState.value is UiState.Scanning) {
            startScan()
        }
    }

    fun onPause() {
        // Stop scanning if we are in the scanning state
        if (uiState.value is UiState.Scanning) {
            stopScan()
        }
    }

    // endregion

    // region Actions
    fun startScan() {
        stopScan()
        viewModelState.update {
            it.copy(
                selectedPeripheral = null,
                fileTransferClient = null,
                error = null
            )
        }
        connectionManager.startScan()
    }

    private fun stopScan() {
        autoConnectJob?.cancel()
        autoConnectJob = null
        delayBeforeConnectingJob?.cancel()
        delayBeforeConnectingJob = null

        connectionManager.stopScan()
    }
    // endregion

    // region Utils
    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private fun stopScanningAndConnect(peripheral: Peripheral) {
        log.info("Connect to ${peripheral.nameOrAddress}")

        stopScan()
        viewModelState.update { it.copy(selectedPeripheral = peripheral) }

        // Wait some time until scanning is really stopped to avoid some connection problems
        val kTimeToWaitForScannerToStopBeforeConnection = 500L
        viewModelScope.launch {
            delayBeforeConnectingJob = async(Dispatchers.IO) {
                delay(kTimeToWaitForScannerToStopBeforeConnection)
                if (isActive) {
                    delayBeforeConnectingJob?.cancel()

                    connect(peripheral)
                }
            }
        }
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private fun connect(
        peripheral: Peripheral,
    ) {
        var fileTransferPeripheral: FileTransferPeripheral? = null
        when (peripheral) {
            is WifiPeripheral -> fileTransferPeripheral =
                WifiFileTransferPeripheral(peripheral, onWifiPeripheralGetPasswordForHostName)
            is BlePeripheral -> {
                fileTransferPeripheral =
                    BleFileTransferPeripheral(peripheral, onBlePeripheralBonded)

                // Link state changes during connection to UI. This is different for wifi or bluetooth peripherals (because bluetooth could potentially need a lot of steps to connect)
                linkBleFileTransferState(fileTransferPeripheral)
            }
        }

        if (fileTransferPeripheral == null) {
            log.severe("Error: can't connect to unknown peripheral type")
            viewModelState.update {
                it.copy(
                    fileTransferClient = null,
                    error = Exception("Error connecting to: ${peripheral.nameOrAddress}")
                )
            }
            return
        }

        // Connect
        connectionManager.connect(fileTransferPeripheral) { result ->
            viewModelState.update { viewModelState ->

                result.fold(
                    onSuccess = {
                        viewModelState.copy(fileTransferClient = it)
                    },
                    onFailure = {
                        viewModelState.copy(
                            fileTransferClient = null,
                            error = Exception("Error connecting to: ${peripheral.nameOrAddress}")
                        )
                    }
                )
            }
        }
    }

    private fun linkBleFileTransferState(fileTransferPeripheral: BleFileTransferPeripheral) {
        fileTransferPeripheralStateJob = viewModelScope.launch {
            fileTransferPeripheral.fileTransferState
                .onCompletion { exception ->
                    log.info("fileTransferPeripheralStateJob onCompletion: $exception")
                }
                .collect { fileTransferState ->
                    //log.info("fileTransferPeripheralStateJob when: $fileTransferState")
                    when (fileTransferState) {
                        is BleFileTransferPeripheral.FileTransferState.Disconnected ->
                            cancel()
                        else -> {}
                    }
                }
        }
    }

    // endregion

    /**
     * Factory that takes ProjectsRepository as a dependency
     */
    companion object {
        fun provideFactory(
            connectionManager: ConnectionManager,
            isAutoConnectEnabled: Boolean,
            onBlePeripheralBonded: ((name: String?, address: String) -> Unit)? = null,
            onWifiPeripheralGetPasswordForHostName: ((name: String, hostName: String) -> String?)? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ScanViewModel(
                    connectionManager,
                    isAutoConnectEnabled,
                    onBlePeripheralBonded,
                    onWifiPeripheralGetPasswordForHostName,
                ) as T
            }
        }
    }
}


/*
val wifiPeripheralsFlow = wifiPeripheralScanner.wifiPeripheralsFlow.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(),
    initialValue = emptyList()
)*/