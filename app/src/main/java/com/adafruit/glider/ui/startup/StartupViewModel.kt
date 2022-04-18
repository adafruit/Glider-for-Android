package com.adafruit.glider.ui.startup

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.lifecycle.ViewModel
import io.openroad.ble.filetransfer.FileTransferConnectionManager
import io.openroad.utils.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class StartupViewModel : ViewModel() {
    // States
    sealed class StartupUiState {       // https://proandroiddev.com/traditional-mvvm-with-jetpack-compose-and-stateflow-7ca934e12784
        object AwaitingPermissionsCheck : StartupUiState()
        object Reconnecting : StartupUiState()
        object Finished : StartupUiState()
    }

    // Data - Private
    private val log by LogUtils()
    private val _uiState = MutableStateFlow<StartupUiState>(StartupUiState.AwaitingPermissionsCheck)

    // Data
    val uiState = _uiState.asStateFlow()

    // region Actions
    fun reconnect() {
        log.info("Startup reconnect")
        _uiState.value = StartupUiState.Reconnecting

        FileTransferConnectionManager.reconnect { isConnected ->

            log.info("Startup finished")
            _uiState.value = StartupUiState.Finished
        }
    }
    // endregion
}
