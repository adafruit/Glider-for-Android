package com.adafruit.glider.ui.scan

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.openroad.filetransfer.FileTransferClient
import io.openroad.filetransfer.ConnectionManager
import io.openroad.Peripheral
import com.adafruit.glider.utils.LogUtils
import io.openroad.filetransfer.Scanner
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScanViewModel(
    private val connectionManager: ConnectionManager,
) : ViewModel() {
    // UI State
    sealed class UiState {
        object Startup : UiState()
        data class Scanning(val peripherals: List<Peripheral>) : UiState()
        data class ScanningError(val cause: Throwable) : UiState()
        data class PreparingConnection(val peripherals: List<Peripheral>, val selectedPeripheral: Peripheral) : UiState()
        data class Connecting(val peripheral: Peripheral) : UiState()
        data class Connected(val client: FileTransferClient) : UiState()
        data class FileTransferEnabled(val client: FileTransferClient) : UiState()
    }

    // Internal state
    private data class ViewModelState(
        val connectionManagerState: Scanner.ScanningState,
    ) {
        fun toUiState(): UiState {
            return when (val state = connectionManagerState) {
                is Scanner.ScanningState.Scanning -> UiState.Scanning(state.peripherals)
                is Scanner.ScanningState.ScanningError -> UiState.ScanningError(state.cause)
                is Scanner.ScanningState.PreparingConnection -> UiState.PreparingConnection(state.peripherals, state.selectedPeripheral)
                is Scanner.ScanningState.Connecting -> UiState.Connecting(state.peripheral)
                is Scanner.ScanningState.Connected -> UiState.Connected(state.client)
                is Scanner.ScanningState.FileTransferEnabled -> UiState.FileTransferEnabled(state.client)
                else -> UiState.Startup
            }
        }
    }

    // Data - Private
    private val viewModelState = MutableStateFlow(ViewModelState(connectionManager.scanningState.value))
    private val log by LogUtils()

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
        viewModelScope.launch {
            connectionManager.scanningState.collect { state ->
                log.info("Connection state: $state")
                viewModelState.update { it.copy(connectionManagerState = state) }
            }
        }
    }

    fun onResume() {
        // Start scanning if we are in the scanning state
        if (uiState.value == UiState.Startup || uiState.value is UiState.Scanning || uiState.value is UiState.PreparingConnection) {
            startScan()
        }
    }

    fun onPause() {
        // Stop scanning if we are in the scanning state
        if (uiState.value is UiState.Scanning || uiState.value is UiState.PreparingConnection) {
            connectionManager.stopScan()
        }
    }

    fun startScan() {
        connectionManager.startScan()
    }

    // endregion


    /**
     * Factory that takes ProjectsRepository as a dependency
     */
    companion object {
        fun provideFactory(
            connectionManager: ConnectionManager,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ScanViewModel(
                    connectionManager,
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