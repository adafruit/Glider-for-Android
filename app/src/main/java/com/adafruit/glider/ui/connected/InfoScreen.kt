package com.adafruit.glider.ui.connected

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.theme.GliderTheme
import io.openroad.ble.filetransfer.FileTransferConnectionManager

/**
 * Created by Antonio García (antonio@openroad.es)
 */

@Composable
fun InfoScreen(
    innerPadding: PaddingValues,
) {
    val mainColor = Color.White.copy(alpha = 0.7f)
    val isReconnecting by FileTransferConnectionManager.isSelectedPeripheralReconnecting.collectAsState()
    val connectedPeripherals by FileTransferConnectionManager.connectedPeripherals.collectAsState()
    val selectedFileTransferClient by FileTransferConnectionManager.selectedFileTransferClient.collectAsState()

    Column(
    ) {
        // Peripheral
        Column(
            Modifier
                .padding(innerPadding)
                .padding(20.dp)
                .padding(top = 20.dp),      // Extra top padding
            verticalArrangement = Arrangement.Absolute.spacedBy(1.dp)
        ) {
            Text(
                modifier = Modifier
                    .align(CenterHorizontally)
                    .padding(bottom = 4.dp),
                text = "Connected Peripherals".uppercase(),
                style = MaterialTheme.typography.subtitle2
            )

            if (connectedPeripherals.isEmpty()) {
                Text("No peripherals found".uppercase())
            } else {
                val selectedPeripheral = selectedFileTransferClient?.bleFileTransferPeripheral

                connectedPeripherals.forEach { bleFileTransferPeripheral ->
                    Button(
                        onClick = { /*TODO*/ },
                        colors = ButtonDefaults.textButtonColors(
                            backgroundColor = mainColor,
                            contentColor = Color.Black
                        ),
                        enabled = !isReconnecting,
                        modifier = Modifier.height(IntrinsicSize.Min),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        Text(
                            bleFileTransferPeripheral.nameOrAddress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterVertically),
                            textAlign = TextAlign.Left,
                            fontWeight = if (selectedPeripheral?.address == bleFileTransferPeripheral.address) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// region Previews
@Preview(showSystemUi = true)
@Composable
private fun FileExplorerScreenPreview() {
    GliderTheme {
        BackgroundGradientDefault {
            InfoScreen(PaddingValues(0.dp))
        }
    }
}

//endregion