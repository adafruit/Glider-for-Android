package com.adafruit.glider.ui.startup

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adafruit.glider.R
import com.adafruit.glider.ui.components.BackgroundGradientFillMaxSize
import com.adafruit.glider.ui.theme.GliderTheme

@Composable
fun StartupScreen(
    // isInitialPermissionsCheckInProgress: Boolean,
    //viewModel: StartupViewModel = viewModel(),
    onFinished: () -> Unit
) {

    val isRestoringConnection = false
    /*
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
    }*/

    // UI
    BackgroundGradientFillMaxSize(contentAlignment = Alignment.Center) {
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

            CircularProgressIndicator(color = Color.White)
            //BluetoothState()
        }
    }
}

/*
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
*/

// region Previews
@Preview(showSystemUi = true)
@Composable
private fun StartupPreview() {
    GliderTheme {
        StartupScreen(/*true*/) {}
    }
}
//endregion
