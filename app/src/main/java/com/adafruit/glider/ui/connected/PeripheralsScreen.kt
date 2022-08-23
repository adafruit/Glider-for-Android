package com.adafruit.glider.ui.connected

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.annotation.RequiresPermission
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.adafruit.glider.ui.components.BackgroundGradient
import com.adafruit.glider.ui.components.InputTextActionDialog
import com.adafruit.glider.ui.theme.ControlsOutline
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.utils.observeAsState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.openroad.Peripheral
import io.openroad.ble.peripheral.SavedBondedBlePeripherals
import io.openroad.ble.peripheral.BlePeripheral
import io.openroad.ble.utils.BleManager
import io.openroad.wifi.peripheral.WifiPeripheral
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
@Composable
fun PeripheralsScreen(
    modifier: Modifier = Modifier,
    viewModel: PeripheralsViewModel,
    snackBarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    // Permissions
    val isInitialPermissionsCheckInProgress: Boolean
    if (LocalInspectionMode.current) {
        // Simulate permissions for Compose Preview
        isInitialPermissionsCheckInProgress = false
    } else {
        // Check Bluetooth-related permissions state
        val bluetoothPermissionState =
            rememberMultiplePermissionsState(BleManager.getNeededPermissions())

        isInitialPermissionsCheckInProgress =
            !bluetoothPermissionState.allPermissionsGranted && !bluetoothPermissionState.shouldShowRationale
        LaunchedEffect(isInitialPermissionsCheckInProgress) {
            if (isInitialPermissionsCheckInProgress) {
                // First time that permissions are needed at startup
                bluetoothPermissionState.launchMultiplePermissionRequest()
            } else {
                // Permissions ready
            }
        }
    }

    // Start / Stop scanning based on lifecycle
    val lifeCycleState = LocalLifecycleOwner.current.lifecycle.observeAsState()
    if (!isInitialPermissionsCheckInProgress && lifeCycleState.value == Lifecycle.Event.ON_RESUME) {
        LaunchedEffect(lifeCycleState) {
            viewModel.onResume()
        }
    } else if (lifeCycleState.value == Lifecycle.Event.ON_PAUSE) {
        LaunchedEffect(lifeCycleState) {
            viewModel.onPause()
        }
    }

    val connectionManager = viewModel.connectionManager
    val scannedPeripherals by connectionManager.peripherals.collectAsState()
    val peripheralAddressesBeingSetup by connectionManager.peripheralAddressesBeingSetup.collectAsState()
    val bondedBlePeripheralsData by viewModel.savedBondedBlePeripherals.peripheralsData.collectAsState()
    val openWifiDialogSettings by viewModel.openWifiDialogSettings.collectAsState()

    val currentFileTransferClient by connectionManager.currentFileTransferClient.collectAsState()

    // Errors
    val bleScanningLastException by connectionManager.bleScanningLastException.collectAsState()
    LaunchedEffect(bleScanningLastException) {
        bleScanningLastException?.let { bleException ->
            snackBarHostState.showSnackbar(message = "Bluetooth scanning error: $bleException")
        }
    }

    val wifiScanningLastException by connectionManager.wifiScanningLastException.collectAsState()
    LaunchedEffect(wifiScanningLastException) {
        wifiScanningLastException?.let { nsdException ->
            snackBarHostState.showSnackbar(message = "Wifi scanning error: $nsdException")
        }
    }

    val connectionLastException by connectionManager.connectionLastException.collectAsState()
    LaunchedEffect(connectionLastException) {
        connectionLastException?.let { exception ->
            snackBarHostState.showSnackbar(message = "Connection error: ${exception.message}")
        }
    }

    val coroutineScope = rememberCoroutineScope()
    PeripheralsScreenBody(
        modifier = modifier,
        scannedPeripherals = scannedPeripherals,
        bondedBlePeripheralsData = bondedBlePeripheralsData,
        peripheralAddressesBeingSetup = peripheralAddressesBeingSetup,
        selectedPeripheral = currentFileTransferClient?.peripheral,
        onSelectPeripheral = { peripheral ->
            connectionManager.setSelectedPeripheral(peripheral)
        },
        onSelectBondedPeripheral = { data ->
            connectionManager.reconnectToBondedBlePeripherals(
                setOf(data.address),
                completion = { isConnected ->
                    if (!isConnected) {
                        coroutineScope.launch {
                            snackBarHostState.showSnackbar(message = "Cannot connect to bonded peripheral: ${data.name ?: data.address}")
                        }
                    }
                })
        },
        onDeleteBondedPeripheral = { address ->
            connectionManager.disconnectFileTransferClient(address)
            viewModel.savedBondedBlePeripherals.remove(address)
        },
        openWifiDialogSettings = openWifiDialogSettings,
        onOpenWifiDialogSettings = { wifiPeripheral ->
            viewModel.openWifiDialogSettings(wifiPeripheral)
        },
        onWifiPeripheralPasswordChanged = { wifiPeripheral, newPassword ->
            if (newPassword != null) {
                viewModel.updateWifiPeripheralPassword(wifiPeripheral, newPassword)
            }
            viewModel.closeWifiDialogSettings()
        }
    )
}

@Composable
private fun PeripheralsScreenBody(
    modifier: Modifier = Modifier,
    scannedPeripherals: List<Peripheral>,
    bondedBlePeripheralsData: List<SavedBondedBlePeripherals.Data>,
    peripheralAddressesBeingSetup: List<String>,
    selectedPeripheral: Peripheral?,
    onSelectPeripheral: ((Peripheral) -> Unit)? = null,
    onSelectBondedPeripheral: ((SavedBondedBlePeripherals.Data) -> Unit)? = null,
    onDeleteBondedPeripheral: ((String) -> Unit)? = null,
    openWifiDialogSettings: Pair<String, String>? = null,
    onOpenWifiDialogSettings: ((wifiPeripheral: WifiPeripheral) -> Unit)? = null,
    onWifiPeripheralPasswordChanged: ((wifiPeripheral: WifiPeripheral, newPassword: String?) -> Unit)? = null,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(20.dp)
            .padding(top = 20.dp),     // Extra top padding
        //.border(1.dp, Color.Red),
        verticalArrangement = spacedBy(24.dp),
        horizontalAlignment = CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.Absolute.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /*CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )*/

            Text(
                //text = "Scanning Peripherals...".uppercase(),
                text = "Select Peripheral".uppercase(),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        PeripheralsListByType(
            peripherals = scannedPeripherals,
            bondedPeripherals = bondedBlePeripheralsData,
            selectedPeripheral = selectedPeripheral,
            peripheralAddressesBeingSetup = peripheralAddressesBeingSetup,
            onSelectPeripheral = onSelectPeripheral,
            onSelectBondedPeripheral = onSelectBondedPeripheral,
            onDeleteBondedPeripheral = onDeleteBondedPeripheral,
            onOpenWifiDialogSettings = onOpenWifiDialogSettings,
        )
    }

    // Wifi settings dialog
    openWifiDialogSettings?.let { settings ->
        val address = settings.first
        val currentPassword = settings.second
        val peripheralForWifiSettings = scannedPeripherals.filterIsInstance<WifiPeripheral>()
            .firstOrNull { it.address == address }
        if (peripheralForWifiSettings != null) {
            InputTextActionDialog(
                alertText = "Settings", //peripheralForWifiSettings.nameOrAddress,
                alertMessage = "Password:",
                currentText = currentPassword,
                placeholderText = "Enter password",
                actionText = "Set",
            ) { inputText ->
                onWifiPeripheralPasswordChanged?.invoke(
                    peripheralForWifiSettings,
                    inputText
                )
            }
        }
    }

}

@Composable
private fun PeripheralsListByType(
    peripherals: List<Peripheral>,
    bondedPeripherals: List<SavedBondedBlePeripherals.Data>,
    selectedPeripheral: Peripheral?,
    peripheralAddressesBeingSetup: List<String>,
    onSelectPeripheral: ((Peripheral) -> Unit)?,
    onSelectBondedPeripheral: ((SavedBondedBlePeripherals.Data) -> Unit)?,
    onDeleteBondedPeripheral: ((String) -> Unit)?,
    onOpenWifiDialogSettings: ((wifiPeripheral: WifiPeripheral) -> Unit)?,
) {
    val mainColor = ControlsOutline

    // Empty state
    if (peripherals.isEmpty() && bondedPeripherals.isEmpty()) {
        Text(
            "No peripherals found".uppercase(),
            modifier = Modifier.padding(top = 20.dp),
            color = Color.LightGray,
        )
    } else {

        // Wifi peripherals
        val wifiPeripherals = peripherals.filterIsInstance<WifiPeripheral>()
        if (wifiPeripherals.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = spacedBy(12.dp),
                //horizontalAlignment = CenterHorizontally,
            ) {
                Text(
                    modifier = Modifier
                        .padding(bottom = 4.dp),
                    text = "Wifi".uppercase(),
                    style = MaterialTheme.typography.labelMedium
                )

                WifiPeripheralsList(
                    wifiPeripherals = wifiPeripherals,
                    selectedPeripheral = selectedPeripheral,
                    peripheralAddressesBeingSetup = peripheralAddressesBeingSetup,
                    onSelectPeripheral = { address ->
                        val peripheral = wifiPeripherals.firstOrNull { it.address == address }
                        if (peripheral != null) {
                            onSelectPeripheral?.invoke(peripheral)
                        }
                    },
                    onStateAction = { address ->
                        val peripheral = wifiPeripherals.firstOrNull { it.address == address }
                        if (peripheral != null) {
                            onOpenWifiDialogSettings?.invoke(peripheral)
                        }
                    }
                )
            }
        }

        // Bluetooth advertising peripherals
        val bondedAddresses = bondedPeripherals.map { it.address }
        val blePeripherals = peripherals
            .filterIsInstance<BlePeripheral>()
            .filter { !bondedAddresses.contains(it.address) }       // Don't show bonded
        if (blePeripherals.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = spacedBy(12.dp),
            ) {
                Text(
                    modifier = Modifier
                        .padding(bottom = 4.dp),
                    text = "Bluetooth Advertising".uppercase(),
                    style = MaterialTheme.typography.labelMedium
                )

                BlePeripheralsList(
                    blePeripherals = blePeripherals,
                    selectedPeripheral = selectedPeripheral,
                    peripheralAddressesBeingSetup = peripheralAddressesBeingSetup,
                    onSelectPeripheral = { address ->
                        val peripheral = blePeripherals.firstOrNull { it.address == address }
                        if (peripheral != null) {
                            onSelectPeripheral?.invoke(peripheral)
                        }
                    },
                )
            }
        }

        // Bluetooth bonded peripherals
        if (bondedPeripherals.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = spacedBy(12.dp),
            ) {
                Text(
                    modifier = Modifier
                        .padding(bottom = 4.dp),
                    text = "Bluetooth bonded".uppercase(),
                    style = MaterialTheme.typography.labelMedium
                )

                BondedBlePeripheralsList(
                    bondedBlePeripherals = bondedPeripherals,
                    selectedPeripheral = selectedPeripheral,
                    onSelectPeripheral = onSelectBondedPeripheral,
                    peripheralAddressesBeingSetup = peripheralAddressesBeingSetup,
                    onStateAction = { address ->
                        onDeleteBondedPeripheral?.invoke(address)
                    }
                )
            }
        }
    }
}

@Composable
private fun BlePeripheralsList(
    blePeripherals: List<BlePeripheral>,
    selectedPeripheral: Peripheral?,
    peripheralAddressesBeingSetup: List<String>,
    onSelectPeripheral: (String) -> Unit,
) {
    blePeripherals.forEach { blePeripheral ->
        val address = blePeripheral.address
        val isSelected = selectedPeripheral?.address == address
        val name = blePeripheral.nameOrAddress
        val isOperationInProgress = peripheralAddressesBeingSetup.contains(address)

        val state =
            if (isOperationInProgress) PeripheralButtonState.Wait else PeripheralButtonState.Default

        PeripheralButton(
            name = name,
            address = address,
            isSelected = isSelected,
            state = state,
            onSelectPeripheral = onSelectPeripheral,
            onStateAction = {}
        )
    }
}

@Composable
private fun BondedBlePeripheralsList(
    bondedBlePeripherals: List<SavedBondedBlePeripherals.Data>,
    selectedPeripheral: Peripheral?,
    peripheralAddressesBeingSetup: List<String>,
    onSelectPeripheral: ((SavedBondedBlePeripherals.Data) -> Unit)?,
    onStateAction: (String) -> Unit,
) {
    bondedBlePeripherals.forEach { data ->
        val address = data.address
        val isSelected = selectedPeripheral?.address == address
        val name = data.name ?: address

        val isOperationInProgress = peripheralAddressesBeingSetup.contains(address)
        val state =
            if (isOperationInProgress) PeripheralButtonState.Wait else PeripheralButtonState.Delete

        PeripheralButton(
            name = name,
            address = address,
            isSelected = isSelected,
            state = state,
            onSelectPeripheral = {
                onSelectPeripheral?.invoke(data)
            },
            onStateAction = onStateAction
        )
    }
}

@Composable
private fun WifiPeripheralsList(
    wifiPeripherals: List<WifiPeripheral>,
    selectedPeripheral: Peripheral?,
    peripheralAddressesBeingSetup: List<String>,
    onSelectPeripheral: (String) -> Unit,
    onStateAction: (String) -> Unit,
) {
    wifiPeripherals.forEach { peripheral ->
        val address = peripheral.address
        val isSelected = selectedPeripheral?.address == address
        val name = peripheral.nameOrAddress
        val isOperationInProgress = peripheralAddressesBeingSetup.contains(address)
        val state =
            if (isOperationInProgress) PeripheralButtonState.Wait else PeripheralButtonState.Settings

        PeripheralButton(
            name = name,
            address = address,
            isSelected = isSelected,
            state = state,
            onSelectPeripheral = onSelectPeripheral,
            onStateAction = onStateAction
        )
    }
}

// region Button

private sealed class PeripheralButtonState {
    object Default : PeripheralButtonState()
    object Wait : PeripheralButtonState()
    object Delete : PeripheralButtonState()
    object Settings : PeripheralButtonState()
}

@Composable
private fun PeripheralButton(
    name: String,
    address: String,
    isSelected: Boolean,
    state: PeripheralButtonState,
    onSelectPeripheral: (String) -> Unit,
    onStateAction: (String) -> Unit,
) {
    val mainColor = ControlsOutline

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Absolute.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        if (isSelected) {
            Button(
                onClick = { /*Nothing to do*/ },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = mainColor,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                //enabled = !isReconnecting,
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .weight(1f),
            ) {
                PeripheralName(
                    name = name,
                    isSelected = isSelected,
                    state = state
                )
            }
        } else {
            OutlinedButton(
                onClick = { onSelectPeripheral(address) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = mainColor
                ),
                border = BorderStroke(1.dp, mainColor),
                shape = RoundedCornerShape(8.dp),
                //enabled = !isReconnecting,
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .weight(1f),
            ) {
                PeripheralName(
                    name = name,
                    isSelected = isSelected,
                    state = state
                )
            }
        }

        if (state == PeripheralButtonState.Delete || state == PeripheralButtonState.Settings) {
            OutlinedButton(
                onClick = { onStateAction(address) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = mainColor
                ),
                border = BorderStroke(1.dp, mainColor),
                shape = RoundedCornerShape(8.dp),
                //contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .height(IntrinsicSize.Min)
            ) {
                if (state == PeripheralButtonState.Delete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete"
                    )
                } else if (state == PeripheralButtonState.Settings) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
        }
    }
}

@Composable
private fun PeripheralName(
    name: String,
    isSelected: Boolean,
    state: PeripheralButtonState,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Text(
            name,
            modifier = Modifier.weight(1f),//.border(BorderStroke(1.dp, Color.Red), RoundedCornerShape(4.dp)),
            textAlign = TextAlign.Left,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )

        when (state) {
            /*
            PeripheralButtonState.Delete -> {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete"
                )
            }*/
            PeripheralButtonState.Wait -> {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(18.dp)
                )
            }
            else -> {}
        }
    }
}
// endregion

// region Previews
@Preview
@Composable
private fun InfoScreenPreview() {
    val peripheral =
        WifiPeripheral(name = "Adafruit Feather ESP32-S2 TFT", address = "127.0.0.1", port = 80)
    val peripheral2 =
        WifiPeripheral(name = "Adafruit Test Device", address = "127.0.0.2", port = 80)
    val peripherals = arrayListOf<Peripheral>(peripheral, peripheral2)

    GliderTheme {
        BackgroundGradient {
            PeripheralsScreenBody(
                scannedPeripherals = peripherals,
                peripheralAddressesBeingSetup = emptyList(),
                bondedBlePeripheralsData = emptyList(),
                selectedPeripheral = peripheral2,
            )
        }
    }
}

@Preview
@Composable
private fun InfoScreenEmptyPreview() {
    GliderTheme {
        BackgroundGradient {

            PeripheralsScreenBody(
                scannedPeripherals = emptyList(),
                peripheralAddressesBeingSetup = emptyList(),
                bondedBlePeripheralsData = emptyList(),
                selectedPeripheral = null,
            )
        }
    }
}
//endregion