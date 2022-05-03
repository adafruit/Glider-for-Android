package com.adafruit.glider.ui.scan

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adafruit.glider.BuildConfig
import com.adafruit.glider.R
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.ui.theme.TopBarBackground
import com.adafruit.glider.utils.observeAsState
import io.openroad.ble.BleManager
import io.openroad.ble.FileTransferClient
import io.openroad.ble.applicationContext
import io.openroad.ble.filetransfer.BleFileTransferPeripheral

@Composable
fun ScanScreen(
    viewModel: ScanViewModel = viewModel(),
    onFinished: (fileTransferPeripheral: BleFileTransferPeripheral) -> Unit
) {
    // Start / Stop scanning based on lifecycle
    val lifeCycleState = LocalLifecycleOwner.current.lifecycle.observeAsState()
    if (lifeCycleState.value == Lifecycle.Event.ON_RESUME) {
        LaunchedEffect(lifeCycleState) {
            viewModel.onResume()
        }
    } else if (lifeCycleState.value == Lifecycle.Event.ON_PAUSE) {
        LaunchedEffect(lifeCycleState) {
            viewModel.onPause()
        }
    }

    // UI
    val scaffoldState = rememberScaffoldState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Searching") }, backgroundColor = TopBarBackground
            )
        },
        scaffoldState = scaffoldState,
    ) { innerPadding ->
        BackgroundGradientDefault {
            ScanBody(innerPadding, viewModel, scaffoldState.snackbarHostState, onFinished)
        }
    }
}

@Composable
private fun ScanBody(
    innerPadding: PaddingValues,
    viewModel: ScanViewModel = viewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onFinished: (fileTransferPeripheral: BleFileTransferPeripheral) -> Unit
) {
    // UI State
    val uiState by viewModel.uiState.collectAsState()

    // Scan vars
    //val isScanning = uiState == ScanViewModel.ScanUiState.Scanning
    val numDevicesFound by viewModel.numPeripheralsFound.collectAsState(0)
    val numMatchingDevicesInRangeFound by viewModel.numMatchingPeripheralsInRangeFound.collectAsState(
        0
    )
    val numMatchingDevicesOutOfRangeFound by viewModel.numMatchingPeripheralsOutOfRangeFound.collectAsState(
        0
    )

    when (uiState) {
        is ScanViewModel.ScanUiState.ScanningError -> {
            // Show snackbar if state is ScanningError
            val cause = (uiState as ScanViewModel.ScanUiState.ScanningError).cause
            LaunchedEffect(cause) {
                snackbarHostState.showSnackbar(message = "Scan error: $cause")
            }
        }

        is ScanViewModel.ScanUiState.FileTransferEnabled -> {
            LaunchedEffect(uiState) {
                onFinished((uiState as ScanViewModel.ScanUiState.FileTransferEnabled).fileTransferPeripheral)
            }
        }
        else -> {}
    }

    // UI
    Column(
        modifier = Modifier.padding(innerPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Waves(
            Modifier
                //.weight(1f)
                .requiredWidthIn(max = 400.dp)
                .padding(top = 20.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // Status
            Crossfade(targetState = uiState) { state ->
                when (state) {
                    ScanViewModel.ScanUiState.Scanning -> {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text(
                                "Hold a File-Transfer compatible peripheral close to your device",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                getFileTransferPeripheralsScannedText(
                                    numMatchingDevicesOutOfRangeFound
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (numMatchingDevicesOutOfRangeFound > 0) 1f else 0f),
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                            )
                        }
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text(
                                "Status:",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                getStatusDetailText(uiState),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                Text("[Debug] Found: $numDevicesFound devices")
                Text("[Debug] Matching: $numMatchingDevicesInRangeFound devices")
            }

            Spacer(Modifier.weight(1f))


            // Force remove paired devices
            val bondedDevices = BleManager.getPairedPeripherals(applicationContext)
            if (bondedDevices?.isEmpty() == false) {
                val mainColor = Color.White.copy(alpha = 0.7f)
                OutlinedButton(
                    modifier = Modifier.align(CenterHorizontally),
                    colors = ButtonDefaults.textButtonColors(
                        backgroundColor = Color.Transparent,
                        contentColor = mainColor,
                        disabledContentColor = Color.Gray,
                    ),
                    border = BorderStroke(1.dp, mainColor),
                    onClick = {
                        BleManager.removeAllPairedPeripheralInfo(applicationContext)
                    }) {
                    Text("Remove paired peripherals info", style = MaterialTheme.typography.caption)
                }
            }

        }
    }
}

private fun getFileTransferPeripheralsScannedText(numDevices: Int): String {
    return if (numDevices == 1) {
        "$numDevices peripheral detected nearby but not close enough to establish link"
    } else {
        "$numDevices peripherals detected nearby but not close enough to establish link"
    }
}

private fun getStatusDetailText(uiState: ScanViewModel.ScanUiState): String {
    val text = when (uiState) {
        ScanViewModel.ScanUiState.Scanning -> "Scanning..."
        ScanViewModel.ScanUiState.SetupConnection -> "Preparing connection..."
        ScanViewModel.ScanUiState.Connected -> "Connected..."
        ScanViewModel.ScanUiState.Connecting -> "Connecting..."
        //ScanViewModel.ScanUiState.CheckingFileTransferVersion -> "Checking FileTransfer version"
        ScanViewModel.ScanUiState.SetupFileTransfer -> "Setup FileTransfer service"
        ScanViewModel.ScanUiState.Bonding -> "Waiting to pair peripheral..."
        ScanViewModel.ScanUiState.Discovering -> "Discovering Services..."
        is ScanViewModel.ScanUiState.FileTransferEnabled -> "FileTransfer service ready"
        ScanViewModel.ScanUiState.RestoringConnection -> "Restoring connection..."
        is ScanViewModel.ScanUiState.FileTransferError -> "Error initializing FileTransfer"
        is ScanViewModel.ScanUiState.Disconnected -> if (uiState.cause != null) "Disconnected: ${uiState.cause}" else "Disconnected"
        is ScanViewModel.ScanUiState.ScanningError -> "Scanning error: ${uiState.cause}"
    }

    return text
}

@Composable
private fun Waves(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Wave(color = Color.White.copy(alpha = 0.1f), scale = 1.05f)
        Wave(color = Color.White.copy(alpha = 0.3f), scale = 0.90f)
        Wave(color = Color.White.copy(alpha = 0.5f), scale = 0.75f)
        Wave(color = Color.White.copy(alpha = 0.7f), scale = 0.60f)
        Wave(color = Color.White.copy(alpha = 0.9f), scale = 0.45f)

        Box(
            Modifier
                .aspectRatio(1f)
                .scale(0.3f)
                .shadow(20.dp, CircleShape)
                //.clip(CircleShape)
                .background(Color.White)
        )

        Image(
            painter = painterResource(R.drawable.ic_baseline_bluetooth_24),
            contentDescription = "Bluetooth Scanning",
        )
    }
}

@Composable
private fun Wave(color: Color = Color.Black, scale: Float = 1f, lineWidth: Float = 60f) {
    val strokeWidth = LocalDensity.current.run { (lineWidth / scale).toDp() }

    Box(
        Modifier
            .aspectRatio(1f)
            .scale(scale)
            .border(BorderStroke(strokeWidth, color), CircleShape)
    )
}


// region Previews
@Preview(showSystemUi = true)
@Composable
private fun ScanPreview() {
    GliderTheme {

        BackgroundGradientDefault {
            Waves()
            //ScanBody(PaddingValues(0.dp))
        }

    }
}


//endregion


/*
// Start / Stop scanner based on lifecycle: https://medium.com/nerd-for-tech/handling-lifecycle-events-on-jetpack-compose-f4f53de41f0a
DisposableEffect(key1 = viewModel) {
    viewModel.onStart()
    onDispose { viewModel.onStop() }
}*/


/* // from: https://medium.com/androiddevelopers/a-safer-way-to-collect-flows-from-android-uis-23080b1f8bda
val lifecycleOwner = LocalLifecycleOwner.current
val numDevicesFoundFlow = viewModel.numDevicesFoundFlow
val numDevicesFlowLifecycleAware = remember(numDevicesFoundFlow, lifecycleOwner) {
    numDevicesFoundFlow.flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
}
val numDevices by numDevicesFlowLifecycleAware.collectAsState()
*/
