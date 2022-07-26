package com.adafruit.glider.ui.connected

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adafruit.glider.ui.components.BackgroundGradient
import com.adafruit.glider.ui.theme.ControlsOutline
import com.adafruit.glider.ui.theme.GliderTheme
import io.openroad.Peripheral
import io.openroad.filetransfer.ConnectionManager
import io.openroad.filetransfer.FileTransferClient
import io.openroad.wifi.peripheral.WifiPeripheral

@Composable
fun InfoScreen(
    modifier: Modifier = Modifier,
    connectionManager: ConnectionManager,
   // onSelectPeripheral: (Peripheral) -> Unit,
    ) {
    val isReconnecting by connectionManager.isReconnectingToCurrentPeripheral.collectAsState()
    val connectedPeripherals by connectionManager.peripherals.collectAsState()
    val currentFileTransferClient by connectionManager.currentFileTransferClient.collectAsState()

    InfoScreenBody(
        modifier = modifier,
        isReconnecting = isReconnecting,
        connectedPeripherals = connectedPeripherals,
        currentFileTransferClient = currentFileTransferClient,
        onSelectPeripheral = { peripheral ->
            connectionManager.setSelectedPeripheral(peripheral)
        },
    )
}


@Composable
private fun InfoScreenBody(
    modifier: Modifier = Modifier,
    isReconnecting: Boolean,
    connectedPeripherals: List<Peripheral>,
    currentFileTransferClient: FileTransferClient?,
    onSelectPeripheral: (Peripheral) -> Unit,
) {
    val mainColor = ControlsOutline

    Column(
        modifier
            .fillMaxWidth()
            .padding(20.dp)
            .padding(top = 20.dp),     // Extra top padding
        //.border(1.dp, Color.Red),
        verticalArrangement = spacedBy(12.dp),
        horizontalAlignment = CenterHorizontally,
    ) {
        Text(
            modifier = Modifier
                .padding(bottom = 12.dp),
            text = "Available Peripherals".uppercase(),
            style = MaterialTheme.typography.labelMedium
        )

        if (connectedPeripherals.isEmpty()) {
            Button(
                onClick = { /*No action*/ },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = mainColor,
                ),
                enabled = false,
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                Text(
                    "No peripherals found".uppercase(),
                )
            }
        } else {
            val selectedPeripheral = currentFileTransferClient?.peripheral

            connectedPeripherals.forEach { peripheral ->
                val isSelected = selectedPeripheral?.address == peripheral.address
                val name = peripheral.nameOrAddress

                if (isSelected) {
                    Button(
                        onClick = { /*Nothing to do*/ },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = mainColor,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isReconnecting,
                        modifier = Modifier.height(IntrinsicSize.Min),
                    ) {
                        PeripheralName(name = name, isSelected = isSelected)

                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelectPeripheral(peripheral) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = mainColor
                        ),
                        border = BorderStroke(1.dp, mainColor),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isReconnecting,
                        modifier = Modifier.height(IntrinsicSize.Min),
                    ) {
                        PeripheralName(name = name, isSelected = isSelected)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.PeripheralName(name: String, isSelected: Boolean) {
    Text(
        name,
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.CenterVertically),
        textAlign = TextAlign.Left,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
    )
}

// region Previews
@Preview
@Composable
private fun InfoScreenPreview() {
    val peripheral =
        WifiPeripheral(name = "Adafruit Feather ESP32-S2 TFT", address = "127.0.0.1", port = 80)
    val peripheral2 =
        WifiPeripheral(name = "Adafruit Test Device", address = "127.0.0.2", port = 80)
    val connectedPeripherals = arrayListOf<Peripheral>(peripheral, peripheral2)

    GliderTheme {
        BackgroundGradient {
            InfoScreenBody(
                isReconnecting = false,
                connectedPeripherals = connectedPeripherals,
                currentFileTransferClient = null,
            ) {}
        }
    }
}

@Preview
@Composable
private fun InfoScreenEmptyPreview() {
    GliderTheme {
        BackgroundGradient {
            InfoScreenBody(
                isReconnecting = false,
                connectedPeripherals = emptyList(),
                currentFileTransferClient = null,
            ) {}
        }
    }
}
//endregion