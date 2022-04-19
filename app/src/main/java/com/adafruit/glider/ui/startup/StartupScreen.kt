package com.adafruit.glider.ui.startup

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adafruit.glider.GliderApplication
import com.adafruit.glider.R
import com.adafruit.glider.ui.BackgroundDefault
import com.adafruit.glider.ui.theme.GliderTheme
import io.openroad.ble.state.BleState
import io.openroad.ble.state.BleStateViewModel
import io.openroad.ble.state.BleStateViewModelFactory

@Composable
fun StartupScreen(
    isInitialPermissionsCheckInProgress: Boolean,
    viewModel: StartupViewModel = viewModel(),
    onFinished: () -> Unit
) {
    // UI State
    val uiState by viewModel.uiState.collectAsState()
    val isRestoringConnection = uiState == StartupViewModel.StartupUiState.Reconnecting

    // Navigation
    when (uiState) {
        is StartupViewModel.StartupUiState.AwaitingPermissionsCheck -> {
            if (!isInitialPermissionsCheckInProgress) {
                LaunchedEffect(uiState) {
                    viewModel.reconnect()
                }
            }
        }
        /*
        is StartupViewModel.StartupUiState.Reconnecting -> {
            LaunchedEffect(uiState) {
                viewModel.reconnect()
            }
        }*/
        is StartupViewModel.StartupUiState.Finished -> {
            LaunchedEffect(uiState) {
                onFinished()
            }
        }
        else -> {}
    }

    // UI
    BackgroundDefault {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        )
        {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.glider_logo),
                    contentDescription = "Glider logo",
                )

                Text(
                    "Restoring Connection...",
                    Modifier.alpha(if (isRestoringConnection) 1.0f else 0.0f)
                )

                CircularProgressIndicator()
                //BluetoothState()
            }
        }
    }

}


@Composable
private fun BluetoothState() {
    val bleState = if (LocalInspectionMode.current) {       // In preview mode
        BleState.BleNotAvailable
    } else {
        val appContainer =
            (LocalContext.current.applicationContext as GliderApplication).appContainer
        val bleStateViewModel: BleStateViewModel = viewModel(
            factory = BleStateViewModelFactory(appContainer.bleStateRepository)
        )
        val bleStateState = bleStateViewModel.bleBleState.collectAsState()
        bleStateState.value
    }

    BluetoothStateContent(bleState = bleState)
}

@Composable
private fun BluetoothStateContent(bleState: BleState) {
    val text: String = when (bleState) {
        BleState.Unknown -> "unknown"
        BleState.BluetoothNotAvailable -> "bluetooth not available"
        BleState.BleNotAvailable -> "ble not available"
        BleState.Disabled -> "disabled"
        BleState.Enabled -> "enabled"
        BleState.TurningOn -> "turning on"
        BleState.TurningOff -> "turning off"
    }

    Text("State: $text")
}


// region Previews
@Preview(showSystemUi = true)
@Composable
private fun StartupPreview() {
    GliderTheme {
        StartupScreen(true) {}
    }
}
//endregion
