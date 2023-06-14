package com.adafruit.glider.ui.fileexplorer

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adafruit.glider.ui.components.BackgroundGradient
import com.adafruit.glider.ui.components.BackgroundGradientFillMaxSize
import com.adafruit.glider.ui.components.InputTextActionDialog
import com.adafruit.glider.ui.theme.ControlsOutline
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.utils.DocumentProviderUtils
import io.openroad.filetransfer.ble.scanner.BlePeripheralScannerFake
import io.openroad.filetransfer.filetransfer.ConnectionManager
import io.openroad.filetransfer.wifi.scanner.WifiPeripheralScannerFake

@Composable
fun FileExplorerScreen(
    modifier: Modifier = Modifier,
    viewModel: FileSystemViewModel = viewModel(),
    connectionManager: ConnectionManager,
    snackBarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onShowSelectDirectory: (String) -> Unit,
    onFileSelected: (String) -> Unit,
) {
    val fileTransferClient by connectionManager.currentFileTransferClient.collectAsState()

    // Empty state
    if (fileTransferClient == null) {
        Column(
            modifier = modifier
                .padding(40.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = spacedBy(12.dp),
        ) {
            Text(
                "No peripheral selected".uppercase(),
                textAlign = TextAlign.Center,
            )

            Text(
                "Select a peripheral on the 'Peripherals' tab to start using the File Explorer",
                textAlign = TextAlign.Center,
                color = Color.LightGray,
            )
        }

    } else {
        FileExplorerBody(
            modifier = modifier,
            viewModel = viewModel,
            connectionManager = connectionManager,
            snackBarHostState = snackBarHostState,
            onShowSelectDirectory = onShowSelectDirectory,
            onFileSelected = onFileSelected
        )
    }
}

@Composable
fun FileExplorerBody(
    modifier: Modifier,
    viewModel: FileSystemViewModel = viewModel(),
    connectionManager: ConnectionManager,
    snackBarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onShowSelectDirectory: (String) -> Unit,
    onFileSelected: (String) -> Unit,
) {
    val outlineColor = ControlsOutline
    var path by remember { mutableStateOf("/") }

    val fileTransferClient by connectionManager.currentFileTransferClient.collectAsState()
    val isReconnecting by connectionManager.isReconnectingToBondedPeripherals.collectAsState()
    val peripheralName = fileTransferClient?.peripheral?.nameOrAddress

    // Reconnecting snackbar
    LaunchedEffect(isReconnecting) {
        if (isReconnecting) {
            snackBarHostState.showSnackbar(
                message = "Reconnecting...", duration = SnackbarDuration.Long
            )
        }
    }

    // UI
    val mainColor = ControlsOutline
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = spacedBy(12.dp),
    ) {
        // Top Bars
        Column(verticalArrangement = spacedBy(8.dp)) {

            // Peripheral
            Column(
                verticalArrangement = spacedBy(2.dp)
            ) {
                Text("Selected Peripheral", style = MaterialTheme.typography.labelSmall)

                OutlinedButton(
                    onClick = { /*Do nothing*/ },
                    colors = ButtonDefaults.outlinedButtonColors(
                        //containerColor = outlineColor,
                        contentColor = mainColor//Color.Black
                    ),
                    border = BorderStroke(1.dp, mainColor),
                    shape = RoundedCornerShape(8.dp),
                    //enabled = !isReconnecting,
                    modifier = Modifier.height(IntrinsicSize.Min),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    Text(
                        peripheralName ?: "<unknown>",
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterVertically),
                        textAlign = TextAlign.Left,
                    )
                }
            }

            // Path
            Column(
                verticalArrangement = spacedBy(1.dp)
            ) {

                // Title
                Text("Path", style = MaterialTheme.typography.labelSmall)

                Row(
                    modifier = Modifier
                        //.height(42.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = spacedBy(8.dp)
                ) {
                    // Current Path
                    Text(
                        path,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .border(BorderStroke(1.dp, outlineColor), RoundedCornerShape(4.dp))
                            .weight(1.0f)
                            .padding(9.dp),
                    )

                    // Action Buttons
                    val context = LocalContext.current
                    ActionButtons(
                        mainColor = outlineColor,
                        enabled = !isReconnecting,
                        onMakeDirectory = { inputText ->
                            val newDirectoryPath = viewModel.path.value + inputText
                            fileTransferClient?.let { fileTransferClient ->
                                viewModel.makeDirectory(
                                    newDirectoryPath,
                                    fileTransferClient
                                )
                            }
                        },
                        onMakeFile = { inputText ->
                            val newFilePath = viewModel.path.value + inputText
                            fileTransferClient?.let { fileTransferClient ->
                                viewModel.makeFile(
                                    newFilePath,
                                    fileTransferClient = fileTransferClient
                                )
                            }
                        },
                        onUploadFile = { uri ->
                            DocumentProviderUtils.getFileName(context, uri)?.let { filename ->
                                val newFilePath = viewModel.path.value + filename
                                fileTransferClient?.let { fileTransferClient ->
                                    val item = context.contentResolver.openInputStream(uri)
                                    val data = item?.readBytes()
                                    item?.close()

                                    if (data != null) {
                                        viewModel.makeFile(newFilePath, data, fileTransferClient)
                                    }
                                }
                            }
                        },
                    )

                }
            }
        }

        // FileSystem
        Box(
            Modifier
                .border(BorderStroke(1.dp, outlineColor), RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .fillMaxWidth()
                .fillMaxHeight()

        ) {
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 26.dp)        // Extra padding to take into account the bottom status bar
            ) {
                FileSystemScreen(
                    connectionManager = connectionManager,
                    path = path,
                    onPathChange = { path = it },
                    showOnlyDirectories = false,
                    isLoading = isReconnecting,
                    viewModel = viewModel,
                    onShowSelectDirectory = onShowSelectDirectory,
                    onFileSelected = onFileSelected,
                )
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
    }
}

@Composable
private fun ActionButtons(
    mainColor: Color,
    enabled: Boolean,
    onMakeDirectory: (String) -> Unit,
    onMakeFile: (String) -> Unit,
    onUploadFile: (Uri) -> Unit,
) {
    // New directory button
    InputTextActionButton(
        mainColor = mainColor,
        buttonIcon = Icons.Outlined.CreateNewFolder,
        alertText = "New Directory",
        alertMessage = "Enter name for the new directory",
        placeholderText = "Directory name",
        actionText = "Create",
        enabled = enabled,
        onNewInputText = onMakeDirectory,
    )

    // New file button
    InputTextActionButton(
        mainColor = mainColor,
        buttonIcon = Icons.Outlined.NoteAdd,
        alertText = "New File",
        alertMessage = "Enter name for the new file",
        placeholderText = "Filename",
        actionText = "Create",
        enabled = enabled,
        onNewInputText = onMakeFile,
    )

    // Upload file button
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            onUploadFile(uri)
        }
    }
    OutlinedButton(
        onClick = {
            launcher.launch("*/*")
        },
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            containerColor = Color.Transparent,
            contentColor = mainColor,
            disabledContentColor = Color.Gray,
        ),
        border = BorderStroke(1.dp, mainColor),
    ) {
        Icon(
            Icons.Outlined.UploadFile, contentDescription = "Upload File"
        )
    }
}


@Composable
private fun InputTextActionButton(
    mainColor: Color,
    buttonIcon: ImageVector,
    alertText: String,
    alertMessage: String,
    placeholderText: String,
    actionText: String,
    enabled: Boolean,
    onNewInputText: (String) -> Unit
) {
    val isDialogOpen = remember { mutableStateOf(false) }

    // Button
    OutlinedButton(
        onClick = {
            isDialogOpen.value = true
        },
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            containerColor = Color.Transparent,
            contentColor = mainColor,
            disabledContentColor = Color.Gray,
        ),
        border = BorderStroke(1.dp, mainColor),
    ) {
        Icon(
            buttonIcon, contentDescription = alertText
        )
    }

    // Alert
    if (isDialogOpen.value) {
        InputTextActionDialog(
            alertText = alertText,
            alertMessage = alertMessage,
            placeholderText = placeholderText,
            actionText = actionText,
        ) { inputText ->
            if (inputText != null) {
                onNewInputText(inputText)
            }
            isDialogOpen.value = false
        }
    }
}

// region Previews

@Preview
@Composable
private fun FileExplorerActionButtonsPreview() {
    GliderTheme {
        BackgroundGradient {
            Row(
                Modifier.padding(8.dp),
                horizontalArrangement = spacedBy(8.dp)
            ) {
                ActionButtons(
                    mainColor = ControlsOutline,
                    enabled = true,
                    onMakeDirectory = {},
                    onMakeFile = {},
                    onUploadFile = {},
                )
            }
        }
    }
}

@Preview
@Composable
private fun FileExplorerScreenPreview() {
    GliderTheme {
        val connectionManager = ConnectionManager(
            LocalContext.current,
            BlePeripheralScannerFake(),
            WifiPeripheralScannerFake(),
        )

        BackgroundGradientFillMaxSize {
            FileExplorerScreen(
                connectionManager = connectionManager,
                onShowSelectDirectory = {},
                onFileSelected = {})
        }

    }
}

// endregion
