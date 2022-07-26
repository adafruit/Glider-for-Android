package com.adafruit.glider.ui.scan

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.openroad.filetransfer.FileTransferClient
import io.openroad.filetransfer.ConnectionManager
import io.openroad.Peripheral
import io.openroad.wifi.scanner.WifiPeripheralScannerFake
import com.adafruit.glider.ui.components.BackgroundGradientFillMaxSize
import com.adafruit.glider.ui.components.GliderSnackbarHost
import com.adafruit.glider.ui.components.BackgroundGradient
import com.adafruit.glider.ui.theme.AccentMain
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.utils.observeAsState
import io.openroad.wifi.scanner.NsdScanException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onSelected: (FileTransferClient) -> Unit,
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

    // Special behaviours that depend on uiState
    val uiState by viewModel.uiState.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }

    when (val state = uiState) {
        is ScanViewModel.UiState.ScanningError -> {
            // Show snackBar if state is ScanningError
            val cause = state.cause
            LaunchedEffect(cause) {
                snackBarHostState.showSnackbar(message = "Scan error: $cause")
            }
        }

        is ScanViewModel.UiState.FileTransferEnabled -> {
            LaunchedEffect(state) {
                onSelected(state.client)
            }
        }
        else -> {}
    }

    // UI
    Scaffold(
        snackbarHost = { GliderSnackbarHost(snackBarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Searching") }
            )
        },
    ) { innerPadding ->
        BackgroundGradientFillMaxSize {
            val availableHeight =
                LocalConfiguration.current.screenHeightDp.dp - (innerPadding.calculateTopPadding() + innerPadding.calculateBottomPadding())

            ScanBody(
                modifier = Modifier.padding(innerPadding),
                availableHeight = availableHeight,
                uiState = uiState,
                onRestartScanning = { viewModel.startScan() }
            )
        }
    }

}

@Composable
private fun ScanBody(
    modifier: Modifier = Modifier,
    availableHeight: Dp = LocalConfiguration.current.screenHeightDp.dp,
    uiState: ScanViewModel.UiState,
    onRestartScanning: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(40.dp),
    ) {

        Waves(
            Modifier
                .requiredWidthIn(max = 400.dp)
                .requiredHeightIn(max = availableHeight * 0.6f)
                .padding(top = 20.dp)
        )

        Info(uiState = uiState, onRestartScanning = onRestartScanning)
    }
}

@Composable
private fun Info(
    uiState: ScanViewModel.UiState,
    onRestartScanning: () -> Unit = {}
) {
    // Status
    //Crossfade(targetState = uiState) { state ->
    when (uiState) {
        ScanViewModel.UiState.Startup -> {
            Text(
                "Starting up...",
                fontWeight = FontWeight.Bold,
            )
        }
        is ScanViewModel.UiState.Scanning -> {
            InfoScanning(peripherals = uiState.peripherals, selectedPeripheral = null)
        }
        is ScanViewModel.UiState.PreparingConnection -> {
            InfoScanning(
                peripherals = uiState.peripherals,
                selectedPeripheral = uiState.selectedPeripheral
            )
        }
        is ScanViewModel.UiState.Connecting -> {
            InfoPeripheral(text = "Connecting to:\n${uiState.peripheral.nameOrAddress}")
        }
        is ScanViewModel.UiState.Connected -> {
            InfoPeripheral(text = "Connected to:\n${uiState.client.peripheral.nameOrAddress}")
        }
        is ScanViewModel.UiState.FileTransferEnabled -> {
            InfoPeripheral(text = "Enabled:\n${uiState.client.peripheral.nameOrAddress}")
        }
        is ScanViewModel.UiState.ScanningError -> {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Scanning error:\n${uiState.cause}",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                OutlinedButton(
                    onClick = onRestartScanning,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = AccentMain,
                    ),
                    border = BorderStroke(1.dp, AccentMain),
                ) {
                    Text("Restart scanning".uppercase())
                }

            }
        }
        else -> {
            Text(
                "[Debug] Internal state: $uiState",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
    // }
}

@Composable
private fun InfoPeripheral(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun InfoScanning(peripherals: List<Peripheral>, selectedPeripheral: Peripheral?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        //.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Text(
            "Hold a CircuitPython peripheral close to your device",
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        /*
        Text(
            "Wifi peripherals detected: ${peripherals.size}",
            color = Color.LightGray,
        )*/
        selectedPeripheral?.let {
            Text(
                "Selected: ${selectedPeripheral.nameOrAddress}",
                color = Color.LightGray,
            )
        }
    }
}

@Composable
private fun Waves(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Config
        val numWaves = 5
        val fadeInDuration = 1f
        val fadeOutDuration = 0.8f

        val totalFadeInAnimationDuration = fadeInDuration * numWaves
        val totalAnimationDuration = totalFadeInAnimationDuration + fadeOutDuration

        val infiniteTransition = rememberInfiniteTransition()
        val animationProgress by infiniteTransition.animateFloat(
            0.0f,
            1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = (totalAnimationDuration * 1000).toInt(),
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            )
        )

        val waveOpacity0 = waveOpacity(
            0,
            animationProgress,
            totalAnimationDuration,
            fadeInDuration,
            fadeOutDuration
        )
        val waveOpacity1 = waveOpacity(
            1,
            animationProgress,
            totalAnimationDuration,
            fadeInDuration,
            fadeOutDuration
        )
        val waveOpacity2 = waveOpacity(
            2,
            animationProgress,
            totalAnimationDuration,
            fadeInDuration,
            fadeOutDuration
        )
        val waveOpacity3 = waveOpacity(
            3,
            animationProgress,
            totalAnimationDuration,
            fadeInDuration,
            fadeOutDuration
        )
        val waveOpacity4 = waveOpacity(
            4,
            animationProgress,
            totalAnimationDuration,
            fadeInDuration,
            fadeOutDuration
        )

        Wave(color = Color.White.copy(alpha = 0.1f * waveOpacity4), scale = 1.05f)
        Wave(color = Color.White.copy(alpha = 0.3f * waveOpacity3), scale = 0.90f)
        Wave(color = Color.White.copy(alpha = 0.5f * waveOpacity2), scale = 0.75f)
        Wave(color = Color.White.copy(alpha = 0.7f * waveOpacity1), scale = 0.60f)
        Wave(color = Color.White.copy(alpha = 0.9f * waveOpacity0), scale = 0.45f)

        Box(
            Modifier
                .aspectRatio(1f)
                .scale(0.3f)
                .shadow(20.dp, CircleShape)
                //.clip(CircleShape)
                .background(Color.White)
        )

        Icon(Icons.Rounded.Wifi, null, tint = AccentMain, modifier = Modifier.size(48.dp))
    }
}

private fun waveOpacity(
    index: Int,
    animationProgress: Float,
    totalAnimationDuration: Float,
    fadeInDuration: Float,
    fadeOutDuration: Float
): Float {
    val animationTime = animationProgress * totalAnimationDuration
    val fadeOutTime = totalAnimationDuration - fadeOutDuration

    return if (animationTime > fadeOutTime)
        1f - (animationTime - fadeOutTime) / fadeOutDuration         // Fade out
    else {
        if (animationTime < index * fadeInDuration)
            0f                                                      // Not visible (wait for turn to fade-in)
        else if (animationTime - index * fadeInDuration < fadeInDuration)
            (animationTime - index * fadeInDuration) / fadeInDuration                          // Fade in
        else
            1f                                                      // Visible
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
@Preview(showSystemUi = true, device = Devices.NEXUS_5)
@Composable
fun ScanSmartphonePreview() {
    GliderTheme {
        val connectionManager = ConnectionManager(WifiPeripheralScannerFake())

        val scanViewModel: ScanViewModel = viewModel(
            factory = ScanViewModel.provideFactory(connectionManager)
        )

        ScanScreen(
            viewModel = scanViewModel,
            onSelected = {},
        )
    }
}

@Preview
@Composable
fun ScanInfoStartupPreview() {
    GliderTheme {
        BackgroundGradient(
            Modifier
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Info(ScanViewModel.UiState.Startup)
            }
        }
    }
}

@Preview
@Composable
fun ScanInfoScanningPreview() {
    GliderTheme {
        BackgroundGradient(
            Modifier
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Info(ScanViewModel.UiState.Scanning(emptyList()))
            }
        }
    }
}


@Preview
@Composable
fun ScanInfoErrorPreview() {
    GliderTheme {
        BackgroundGradient(
            Modifier
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Info(ScanViewModel.UiState.ScanningError(NsdScanException(errorCode = 123)))
            }
        }
    }
}

// endregion


/*
    val wifiPeripherals by viewModel.wifiPeripheralsFlow.collectAsStateWithLifecycle()
    Text(text = "Scanning wifi: ${wifiPeripherals.size}")
    LaunchedEffect(wifiPeripherals) {
        Log.d("test", "num wifi: ${wifiPeripherals.size}" )
    }
*/