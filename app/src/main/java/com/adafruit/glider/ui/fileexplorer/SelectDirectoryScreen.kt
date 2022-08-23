package com.adafruit.glider.ui.fileexplorer

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.ui.components.BackgroundGradientFillMaxSize
import com.adafruit.glider.ui.theme.ControlsOutline
import com.adafruit.glider.ui.theme.GliderTheme
import io.openroad.ble.scanner.BlePeripheralScannerFake
import io.openroad.filetransfer.ConnectionManager
import io.openroad.wifi.scanner.WifiPeripheralScannerFake

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectDirectoryScreen(
    viewModel: FileSystemViewModel = viewModel(),
    connectionManager: ConnectionManager,
    fromPath: String,
    actionText: String,
    navController: NavController = rememberNavController(),
    onSelect: (String?) -> Unit,
) {

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Move File") },
                navigationIcon = {
                    if (navController.previousBackStackEntry != null) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        },
    ) { innerPadding ->

        BackgroundGradientFillMaxSize {
            SelectDirectoryBody(
                modifier = Modifier.padding(innerPadding),
                viewModel = viewModel,
                connectionManager = connectionManager,
                fromPath = fromPath,
                actionText = actionText,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun SelectDirectoryBody(
    modifier: Modifier = Modifier,
    viewModel: FileSystemViewModel = viewModel(),
    connectionManager: ConnectionManager,
    fromPath: String,
    actionText: String,
    onSelect: (String?) -> Unit,
) {
    val outlineColor = ControlsOutline

    var toPath by remember { mutableStateOf("/") }

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.Absolute.spacedBy(12.dp),
    ) {

        // From
        Column(
            verticalArrangement = Arrangement.Absolute.spacedBy(2.dp)
        ) {
            Text("From:", style = MaterialTheme.typography.labelSmall)

            Text(
                fromPath,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    //.align(Alignment.CenterVertically)
                    .border(BorderStroke(1.dp, outlineColor), RoundedCornerShape(4.dp))
                    .padding(9.dp),
            )
        }

        // To
        Column(
            verticalArrangement = Arrangement.Absolute.spacedBy(2.dp)
        ) {
            Text("To:", style = MaterialTheme.typography.labelSmall)

            Text(
                toPath,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    //.align(Alignment.CenterVertically)
                    .border(BorderStroke(1.dp, outlineColor), RoundedCornerShape(4.dp))
                    .padding(9.dp),
            )
        }


        // FileSystem
        val isReconnecting by connectionManager.isReconnectingToBondedPeripherals.collectAsState()

        Box(
            Modifier
                .border(BorderStroke(1.dp, outlineColor), RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .fillMaxWidth()
                .weight(1f)

        ) {
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 26.dp)        // Extra padding to take into account the bottom status bar
            ) {
                FileSystemScreen(
                    connectionManager = connectionManager,
                    path = toPath,
                    onPathChange = { toPath = it },
                    showOnlyDirectories = true,
                    isLoading = isReconnecting,
                    viewModel = viewModel,
                    onShowSelectDirectory = {},
                    onFileSelected = { selectedFilePath ->
                        // onFileSelected(selectedFilePath)
                    })
            }

            // Status bar
            val lastTransmit by viewModel.lastTransmit.collectAsState()
            val transmissionProgress by viewModel.transmissionProgress.collectAsState()

            Box(Modifier.align(Alignment.BottomStart)) {
                FileCommandStatusBarView(
                    // backgroundColor = outlineColor,
                    lastTransmit = lastTransmit,
                    transmissionProgress = transmissionProgress,
                )
            }
        }

        // Actions
        val isEnabled = toPath != fromPath
        Row(
            modifier = Modifier.align(CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OutlinedButton(
                onClick = { onSelect(null) },
                enabled = isEnabled,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = ControlsOutline,
                    disabledContentColor = Color.Gray,
                ),
                border = BorderStroke(1.dp, ControlsOutline),
            ) {
                Text("Cancel")
            }

            OutlinedButton(
                onClick = { onSelect(toPath) },
                enabled = true,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = ControlsOutline,
                    disabledContentColor = Color.Gray,
                ),
                border = BorderStroke(1.dp, ControlsOutline),
            ) {
                Text(actionText)
            }
        }
    }
}


// region Previews
@Preview(showSystemUi = true)
@Composable
private fun SelectDirectoryScreenPreview() {
    val connectionManager = ConnectionManager(LocalContext.current, BlePeripheralScannerFake(), WifiPeripheralScannerFake())

    GliderTheme {
        SelectDirectoryScreen(
            connectionManager = connectionManager,
            fromPath = "/test/blahblah/",
            actionText = "Move",
        ) {}

    }
}
//endregion