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
import androidx.compose.ui.Modifier
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
import com.adafruit.glider.R
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.ui.theme.TopBarBackground
import com.adafruit.glider.utils.observeAsState

@Composable
fun ScanScreen(viewModel: ScanViewModel = viewModel()) {
    // Start / Stop scanning based on lifecycle
    val lifeCycleState = LocalLifecycleOwner.current.lifecycle.observeAsState()
    if (lifeCycleState.value == Lifecycle.Event.ON_RESUME) {
        LaunchedEffect(lifeCycleState) {
            viewModel.startScanning()
        }
    } else if (lifeCycleState.value == Lifecycle.Event.ON_PAUSE) {
        LaunchedEffect(lifeCycleState) {
            viewModel.stopScanning()
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
            ScanBody(innerPadding, viewModel, scaffoldState.snackbarHostState)
        }
    }
}

@Composable
private fun ScanBody(
    innerPadding: PaddingValues,
    viewModel: ScanViewModel = viewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    // UI State
    val uiState by viewModel.uiState.collectAsState()

    // Scan vars
    //val isScanning = uiState == ScanViewModel.ScanUiState.Scanning
    val numDevicesFound by viewModel.numDevicesFound.collectAsState(0)
    val numMatchingDevicesInRangeFound by viewModel.numMatchingDevicesInRangeFound.collectAsState(0)
    val numMatchingDevicesOutOfRangeFound by viewModel.numMatchingDevicesOutOfRangeFound.collectAsState(
        0
    )

    // Show error snackbar if needed
    val bleError by viewModel.bleError.collectAsState()
    if (bleError != null) {
        LaunchedEffect(bleError) {
            snackbarHostState.showSnackbar(message = "Scan error: $bleError")
        }
    }

    // UI
    Column(
        modifier = Modifier.padding(innerPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Waves(Modifier.padding(top = 40.dp))

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Found: $numDevicesFound devices")
            Text("Matching: $numMatchingDevicesInRangeFound devices")

            // Status
            Crossfade(targetState = uiState) { state ->
                when (state) {
                    ScanViewModel.ScanUiState.Scanning -> {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text(
                                "Hold a File-Transfer compatible peripheral close to your device",
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                getFileTransferPeripheralsScannedText(
                                    numMatchingDevicesOutOfRangeFound
                                ),
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                            )
                        }
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text(
                                "Status:",
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            Text(getStatusDetailText(uiState), textAlign = TextAlign.Center)
                        }
                    }
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
        ScanViewModel.ScanUiState.Connected -> "Connected..."
        ScanViewModel.ScanUiState.Connecting -> "Connecting..."
        is ScanViewModel.ScanUiState.Disconnected -> "Disconnected: ${uiState.error}"
        ScanViewModel.ScanUiState.Discovering -> "Discovering Services..."
        is ScanViewModel.ScanUiState.FileTransferError -> "Error initializing FileTransfer"
        ScanViewModel.ScanUiState.FileTransferReady -> "FileTransfer service ready"
        ScanViewModel.ScanUiState.RestoringConnection -> "Restoring connection..."
        is ScanViewModel.ScanUiState.ScanningError -> "Scanning error: ${uiState.error}"
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
        modifier = Modifier
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
            //   Waves()
            ScanBody(PaddingValues(0.dp))
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
