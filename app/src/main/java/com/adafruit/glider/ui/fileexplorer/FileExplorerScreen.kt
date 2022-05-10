package com.adafruit.glider.ui.fileexplorer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.BuildConfig
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.ui.theme.TabBackground
import com.adafruit.glider.ui.theme.TopBarBackground
import io.openroad.ble.filetransfer.FileTransferConnectionManager
import java.util.*

/**
 * Created by Antonio García (antonio@openroad.es)
 */

@Composable
fun FileExplorerScaffoldingScreen(
    onFileSelected: (String) -> Unit,
) {
    val scaffoldState = rememberScaffoldState()
    val isInfoOpen = remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Explorer") },
                backgroundColor = TopBarBackground,
                actions = {
                    IconButton(onClick = { isInfoOpen.value = true }) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "Info"
                        )
                    }
                }
            )
        },
        scaffoldState = scaffoldState,
    ) { innerPadding ->
        FileExplorerScreen(
            innerPadding,
            snackbarHostState = scaffoldState.snackbarHostState,
            onFileSelected = onFileSelected
        )

        // Info dialog
        if (isInfoOpen.value) {

            AlertDialog(
                onDismissRequest = { isInfoOpen.value = false },
                contentColor = Color.Black,
                title = { Text("Info") },
                text = {
                    Text("Version: ${BuildConfig.VERSION_NAME} b${BuildConfig.VERSION_CODE}")
                },
                confirmButton = {
                    OutlinedButton(
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.Black
                        ),
                        border = BorderStroke(1.dp, TabBackground),
                        onClick = {
                            isInfoOpen.value = false
                        }) {
                        Text("Ok")
                    }
                }
            )
        }
    }
}

@Composable
fun FileExplorerScreen(
    innerPadding: PaddingValues,
    viewModel: FileSystemViewModel = viewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onFileSelected: (String) -> Unit,
) {
    val mainColor = Color.White.copy(alpha = 0.7f)
    val fileTransferClient =
        FileTransferConnectionManager.selectedFileTransferClient.collectAsState()
    val peripheralName = fileTransferClient.value?.peripheralName
    var path by remember { mutableStateOf("/") }
    val isReconnecting by FileTransferConnectionManager.isSelectedPeripheralReconnecting.collectAsState()

    LaunchedEffect(isReconnecting) {
        if (isReconnecting) {
            snackbarHostState.showSnackbar(
                message = "Reconnecting...",
                duration = SnackbarDuration.Long
            )
        }
    }

    Column(
        Modifier
            .padding(innerPadding)
            .padding(20.dp),
        verticalArrangement = spacedBy(12.dp)
    ) {

        // Top Bars
        Column(verticalArrangement = spacedBy(8.dp)) {

            // Peripheral
            Column(
                verticalArrangement = spacedBy(1.dp)
            ) {
                Text(
                    "Selected Peripheral",
                    style = MaterialTheme.typography.caption
                )

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
                Text(
                    "Path",
                    style = MaterialTheme.typography.caption
                )

                Row(
                    modifier = Modifier
                        //.height(42.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Current Path
                    Text(
                        path,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .border(BorderStroke(1.dp, mainColor), RoundedCornerShape(4.dp))
                            .weight(1.0f)
                            .padding(9.dp),
                    )

                    // Action Buttons
                    InputTextActionButton(
                        mainColor = mainColor,
                        buttonIcon = Icons.Outlined.CreateNewFolder,
                        alertText = "New Directory",
                        alertMessage = "Enter name for the new directory",
                        enabled = !isReconnecting,
                    ) { inputText ->
                        val newDirectoryPath = viewModel.path.value + inputText
                        fileTransferClient.value?.let { fileTransferClient ->
                            viewModel.makeDirectory(newDirectoryPath, fileTransferClient)
                        }
                    }

                    InputTextActionButton(
                        mainColor = mainColor,
                        buttonIcon = Icons.Outlined.NoteAdd,
                        alertText = "New File",
                        alertMessage = "Enter name for the new file",
                        enabled = !isReconnecting,
                    ) { inputText ->
                        val newFilePath = viewModel.path.value + inputText
                        fileTransferClient.value?.let { fileTransferClient ->
                            viewModel.makeFile(newFilePath, fileTransferClient)
                        }
                    }
                }
            }
        }

        // FileSystem
        Box(
            Modifier
                .border(BorderStroke(1.dp, mainColor), RoundedCornerShape(4.dp))
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
                    path = path,
                    onPathChange = { path = it },
                    showOnlyDirectories = false,
                    isLoading = isReconnecting,
                    viewModel = viewModel
                ) { selectedFilePath ->
                    // on file selected
                    onFileSelected(selectedFilePath)
                }
            }
            // Status bar
            Box(Modifier.align(Alignment.BottomStart)) {
                FileCommandStatusBarView(viewModel = viewModel, backgroundColor = mainColor)
            }
        }
    }
}

@Composable
private fun InputTextActionButton(
    mainColor: Color,
    buttonIcon: ImageVector,
    alertText: String,
    alertMessage: String,
    enabled: Boolean,
    onNewInputText: (String) -> Unit
) {
    val isDialogOpen = remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    // New Directory
    OutlinedButton(
        onClick = {
            /*if (BuildConfig.DEBUG) {
                onNewInputText(UUID.randomUUID().toString())
            } else {*/
            isDialogOpen.value = true
            // }
        },
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            backgroundColor = Color.Transparent,
            contentColor = mainColor,
            disabledContentColor = Color.Gray,
        ),
        border = BorderStroke(1.dp, mainColor),
    ) {
        Icon(
            buttonIcon,
            contentDescription = "New Directory"
        )
    }

    // New Directory Alert
    if (isDialogOpen.value) {
        AlertDialog(
            onDismissRequest = { isDialogOpen.value = false },
            contentColor = Color.Black,
            title = { Text(alertText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(alertMessage)
                    TextField(
                        colors = TextFieldDefaults.textFieldColors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color.Black,
                        ),
                        value = inputText,
                        onValueChange = { inputText = it },
                    )
                }
            },
            confirmButton = {
                OutlinedButton(
                    enabled = !inputText.isEmpty(),
                    colors = ButtonDefaults.textButtonColors(
                        //backgroundColor = mainColor,
                        contentColor = Color.Black,
                    ),
                    border = BorderStroke(1.dp, TabBackground),
                    onClick = {
                        isDialogOpen.value = false
                        onNewInputText(inputText)
                    }) {
                    Text("Create")
                }
            },
            dismissButton = {
                OutlinedButton(
                    colors = ButtonDefaults.textButtonColors(
                        //backgroundColor = mainColor,
                        contentColor = Color.Black
                    ),
                    border = BorderStroke(1.dp, TabBackground),
                    onClick = {
                        isDialogOpen.value = false
                    }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// region Previews
@Preview(showSystemUi = true)
@Composable
private fun FileExplorerScreenPreview() {
    GliderTheme {

        BackgroundGradientDefault {
            FileExplorerScaffoldingScreen() {}
        }

    }
}

//endregion