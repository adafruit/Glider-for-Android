package com.adafruit.glider.ui.status

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adafruit.glider.GliderApplication
import com.adafruit.glider.R
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.theme.GliderTheme
import io.openroad.ble.state.BleState
import io.openroad.ble.state.BleStateViewModel
import io.openroad.ble.state.BleStateViewModelFactory

/**
 * Created by Antonio García (antonio@openroad.es)
 */

@Composable
fun BluetoothStatusScreen() {
    // BleState
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

    // Texts
    val title = when (bleState) {
        BleState.Unknown -> "Bluetooth is in an unknown state"
        BleState.BluetoothNotAvailable -> "This device doesn't support Bluetooth"
        BleState.BleNotAvailable -> "This device doesn't support Bluetooth LE"
        BleState.Disabled -> "Bluetooth is currently powered off"
        BleState.TurningOn -> "Bluetooth is currently turning on"
        BleState.TurningOff -> "Bluetooth is currently turning off"
        else -> ""
    }

    val message = when (bleState) {
        BleState.Unknown -> "Bluetooth is in an unknown state"
        BleState.BluetoothNotAvailable -> "Bluetooth support and specifically Bluetooth Low Energy support is needed to communicate with a Bluefruit Device"
        BleState.BleNotAvailable -> "Bluetooth support and specifically Bluetooth Low Energy support is needed to communicate with a Bluefruit Device"
        BleState.Disabled -> "Bluetooth should be enabled on your device for the app to connect to a Bluetooth device"
        BleState.TurningOn -> ""
        BleState.TurningOff -> ""
        else -> ""
    }

    // UI
    BackgroundGradientDefault {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(R.drawable.bluetooth_status_logo),
                contentDescription = "Bluetooth logo",
            )

            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = title,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(

                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.subtitle2,
                color = Color.White
            )
        }
    }
}


// region Previews
@Preview(showSystemUi = true)
@Composable
private fun BluetoothStatusScreenPreview() {
    GliderTheme {
        BluetoothStatusScreen()
    }
}
//endregion