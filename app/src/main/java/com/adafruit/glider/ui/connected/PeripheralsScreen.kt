package com.adafruit.glider.ui.connected

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.util.Log
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
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.adafruit.glider.ui.theme.WarningBackground
import com.adafruit.glider.utils.observeAsState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.openroad.filetransfer.Config
import io.openroad.filetransfer.Peripheral
import io.openroad.filetransfer.ble.peripheral.BlePeripheral
import io.openroad.filetransfer.ble.peripheral.SavedBondedBlePeripherals
import io.openroad.filetransfer.wifi.peripheral.WifiPeripheral
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PeripheralsScreen(
    modifier: Modifier = Modifier,
    viewModel: PeripheralsViewModel,
    snackBarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val logTag = "PeripheralsScreen"

    // Permissions
    val isInitialPermissionsCheckInProgress: Boolean
    if (LocalInspectionMode.current) {
        // Simulate permissions for Compose Preview
        isInitialPermissionsCheckInProgress = false
    } else {
        // Check Bluetooth-related permissions state
        val bluetoothPermissionState =
            rememberMultiplePermissionsState(Config.getNeededPermissions())

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
    val wifiDialogSettings by viewModel.wifiDialogSettings.collectAsState()

    val currentFileTransferClient by connectionManager.currentFileTransferClient.collectAsState()

    // Errors
    val bleScanningLastException by connectionManager.bleScanningLastException.collectAsState()
    LaunchedEffect(bleScanningLastException) {
        bleScanningLastException?.let { bleException ->
            snackBarHostState.showSnackbar(message = "Bluetooth scanning error: $bleException")
            connectionManager.clearBleLastException()
        }
    }

    val wifiScanningLastException by connectionManager.wifiScanningLastException.collectAsState()
    LaunchedEffect(wifiScanningLastException) {
        wifiScanningLastException?.let { nsdException ->
            snackBarHostState.showSnackbar(message = "Wifi scanning error: $nsdException")
            connectionManager.clearWifiLastException()
        }
    }

    val connectionLastException by connectionManager.connectionLastException.collectAsState()
    LaunchedEffect(connectionLastException) {
        connectionLastException?.let { exception ->
            snackBarHostState.showSnackbar(message = "Connection error: ${exception.message}")
            connectionManager.clearConnectionLastException()
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    PeripheralsScreenBody(
        modifier = modifier,
        scannedPeripherals = scannedPeripherals,
        bondedBlePeripheralsData = bondedBlePeripheralsData,
        peripheralAddressesBeingSetup = peripheralAddressesBeingSetup,
        selectedPeripheral = currentFileTransferClient?.peripheral,
        onSelectPeripheral = { peripheral ->
            if (Config.areNeededPermissionsGranted(context)) {
                try {
                    connectionManager.setSelectedPeripheral(peripheral)
                } catch (e: SecurityException) {
                    Log.e(logTag, "Security exception: $e")
                }
            } else {
                Log.d(logTag, "TODO: show permissions needed")
            }
        },
        onSelectBondedPeripheral = { data ->
            try {
                connectionManager.reconnectToBondedBlePeripherals(
                    setOf(data.address),
                    completion = { isConnected ->
                        if (!isConnected) {
                            coroutineScope.launch {
                                snackBarHostState.showSnackbar(message = "Cannot connect to bonded peripheral: ${data.name ?: data.address}")
                            }
                        }
                    })
            } catch (e: SecurityException) {
                Log.e(logTag, "Security exception: $e")
            }
        },
        onDeleteBondedPeripheral = { address ->
            connectionManager.disconnectFileTransferClient(address)
            viewModel.savedBondedBlePeripherals.remove(address)
        },
        wifiDialogSettings = wifiDialogSettings,
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
    wifiDialogSettings: Pair<String, String>? = null,
    onOpenWifiDialogSettings: ((wifiPeripheral: WifiPeripheral) -> Unit)? = null,
    onWifiPeripheralPasswordChanged: ((wifiPeripheral: WifiPeripheral, newPassword: String?) -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    val isDeleteBondedPeripheralDialogOpen = remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(20.dp)
            .padding(top = 20.dp),     // Extra top padding
        verticalArrangement = spacedBy(24.dp),
        horizontalAlignment = CenterHorizontally,
    ) {
        Text(
            text = "Select Peripheral".uppercase(),
            style = MaterialTheme.typography.bodyLarge
        )

        PeripheralsListByType(
            peripherals = scannedPeripherals,
            bondedPeripherals = bondedBlePeripheralsData,
            selectedPeripheral = selectedPeripheral,
            peripheralAddressesBeingSetup = peripheralAddressesBeingSetup,
            onSelectPeripheral = onSelectPeripheral,
            onSelectBondedPeripheral = onSelectBondedPeripheral,
            onDeleteBondedPeripheral = { address ->
                isDeleteBondedPeripheralDialogOpen.value = address
            },
            onOpenWifiDialogSettings = onOpenWifiDialogSettings,
        )
    }

    // Wifi settings dialog
    wifiDialogSettings?.let { settings ->
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

    // Delete bonded peripheral dialog
    isDeleteBondedPeripheralDialogOpen.value?.let { address ->

        AlertDialog(
            onDismissRequest = { isDeleteBondedPeripheralDialogOpen.value = null },
            title = { Text("Delete bonding information") },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            text = { Text("Warning: You will not be able to connect to this peripheral until you reset the bonding information on the peripheral too.") },
            confirmButton = {
                OutlinedButton(
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = WarningBackground,
                        contentColor = Color.White,
                        disabledContentColor = Color.Gray,
                    ),
                    border = BorderStroke(1.dp, Color.Black),
                    onClick = {
                        onDeleteBondedPeripheral?.invoke(address)
                        isDeleteBondedPeripheralDialogOpen.value = null

                    }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Black
                    ),
                    border = BorderStroke(1.dp, Color.Black),
                    onClick = {
                        isDeleteBondedPeripheralDialogOpen.value = null
                    }) {
                    Text("Cancel")
                }
            }
        )
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
            ) {
                Text(
                    modifier = Modifier
                        .padding(bottom = 4.dp),
                    text = "Wifi ".uppercase(),
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
        //val bondedAddresses = bondedPeripherals.map { it.address }
        val blePeripherals = peripherals
            .filterIsInstance<BlePeripheral>()
            //.filter { !bondedAddresses.contains(it.address) }       // Don't show bonded
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
        val blePeripheralsAddresses = blePeripherals.map { it.address }
        val bondedNotAdvertisingPeripherals = bondedPeripherals
            .filter { !blePeripheralsAddresses.contains(it.address)}     // Don't show if advertising
        if (bondedNotAdvertisingPeripherals.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = spacedBy(12.dp),
            ) {
                Text(
                    modifier = Modifier
                        .padding(bottom = 4.dp),
                    text = "Bluetooth Bonded".uppercase(),
                    style = MaterialTheme.typography.labelMedium
                )

                BondedBlePeripheralsList(
                    bondedBlePeripherals = bondedNotAdvertisingPeripherals,
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
            if (isOperationInProgress) PeripheralButtonState.Wait else PeripheralButtonState.Standard

        PeripheralButton(
            name = name,
            details = null,
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
            details = null,
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
            details = "IP: $address",
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
    object Standard : PeripheralButtonState()
    object Wait : PeripheralButtonState()
    object Delete : PeripheralButtonState()
    object Settings : PeripheralButtonState()
}

@Composable
private fun PeripheralButton(
    name: String,
    details: String?,
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
                    details = details,
                    isSelected = isSelected,
                    state = state,
                    mainColor = mainColor,
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
                    details = details,
                    isSelected = isSelected,
                    state = state,
                    mainColor = mainColor,
                    )
            }
        }

        // State button
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
    details: String?,
    isSelected: Boolean,
    state: PeripheralButtonState,
    mainColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                modifier = Modifier.wrapContentHeight(),//.border(BorderStroke(1.dp, Color.Red), RoundedCornerShape(4.dp)),
                textAlign = TextAlign.Left,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )

            details?.let {
                Text(
                    details,
                    textAlign = TextAlign.Left,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        when (state) {
            PeripheralButtonState.Wait -> {
                CircularProgressIndicator(
                    color = mainColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .offset(x = 12.dp)      // Offset because there a big trailing padding to the button border
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