package com.adafruit.glider.ui.fileexplorer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.theme.BackgroundGradientStart
import com.adafruit.glider.ui.theme.ButtonWarningBackground
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.ui.theme.TabBackground
import com.adafruit.glider.utils.ButtonWithLongPress
import com.adafruit.glider.utils.gesturesDisabled
import io.openroad.ble.filetransfer.BleFileTransferPeripheral
import io.openroad.ble.filetransfer.FileTransferConnectionManager
import io.openroad.ble.utils.upPath

/**
 * Created by Antonio García (antonio@openroad.es)
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileSystemScreen(
    path: String,
    onPathChange: (String) -> Unit,
    showOnlyDirectories: Boolean,
    isLoading: Boolean,
    viewModel: FileSystemViewModel = viewModel(),
    onFileSelected: (String) -> Unit,
) {
    val fileTransferClient =
        FileTransferConnectionManager.selectedFileTransferClient.collectAsState()

    // On Appear -> Setup
    LaunchedEffect(Unit) {
        fileTransferClient.value?.let { fileTransferClient ->
            viewModel.showOnlyDirectories = showOnlyDirectories
            viewModel.setup(
                directory = path,
                fileTransferClient = fileTransferClient
            )
        }
    }

    Box(
        Modifier
            .gesturesDisabled(isLoading)
            .fillMaxSize()
    ) {
        // Items
        val isRootDirectory by viewModel.isRootDirectory.collectAsState()
        val entries by viewModel.entries.collectAsState()
        val actionDialogEntry =
            remember { mutableStateOf<BleFileTransferPeripheral.DirectoryEntry?>(null) }
        val renameDialogEntry =
            remember { mutableStateOf<BleFileTransferPeripheral.DirectoryEntry?>(null) }

        val scrollState = rememberScrollState()
        Column(modifier = Modifier.verticalScroll(scrollState)) {
            if (!isRootDirectory) {
                //item {
                Button(
                    onClick = {
                        val newPath = upPath(from = path)
                        onPathChange(newPath)
                        fileTransferClient.value?.let { fileTransferClient ->
                            viewModel.listDirectory(newPath, fileTransferClient)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        backgroundColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    elevation = null,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    ItemRow(
                        imageVector = Icons.Outlined.DriveFolderUpload,
                        imageDescription = "Folder up",
                        name = "..",
                        size = null
                    )
                }
                //}
            }

            entries.forEach { entry ->
                //items(entries) { entry ->
                when (entry.type) {
                    is BleFileTransferPeripheral.DirectoryEntry.EntryType.File -> {
                        FileRow(
                            name = entry.name,
                            size = entry.type.size,
                            isHidden = entry.isHidden,
                            onLongClick = {
                                actionDialogEntry.value = entry
                            },
                            onClick = {
                                onFileSelected(path + entry.name)
                            })
                    }

                    is BleFileTransferPeripheral.DirectoryEntry.EntryType.Directory -> {
                        ButtonWithLongPress(
                            onLongClick = {
                                actionDialogEntry.value = entry
                            },
                            onClick = {
                                val newPath = path + entry.name + "/"
                                onPathChange(newPath)
                                fileTransferClient.value?.let { fileTransferClient ->
                                    viewModel.listDirectory(newPath, fileTransferClient)
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                backgroundColor = Color.Transparent,
                                contentColor = if (entry.isHidden) Color.Gray else Color.White
                            ),
                            elevation = null,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            ItemRow(
                                imageVector = Icons.Outlined.Folder,
                                imageDescription = "Directory",
                                name = entry.name,
                                size = null
                            )
                        }
                    }
                }
            }
        }

        // Actions dialog
        fileTransferClient.value?.let { fileTransferClient ->
            actionDialogEntry.value?.let { entry ->
                ActionDialog(
                    onRename = {
                        actionDialogEntry.value = null
                        renameDialogEntry.value = entry
                    },
                    onMove = {
                        actionDialogEntry.value = null
                        /*TODO*/
                    },
                    onDelete = {
                        actionDialogEntry.value = null
                        viewModel.delete(entry, fileTransferClient) {}
                    },
                    onDismiss = { actionDialogEntry.value = null })
            }


            renameDialogEntry.value?.let { entry ->
                RenameDialog(name = entry.name,
                    onConfirm = { filename ->
                        renameDialogEntry.value = null
                        val fromPath = viewModel.path.value + entry.name
                        val toPath = viewModel.path.value + filename
                        viewModel.renameFile(fromPath, toPath, fileTransferClient)
                    }, onDismiss = {
                        renameDialogEntry.value = null
                    })
            }
        }

        // Empty View / Wait View
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Empty view
            val isTransmitting by viewModel.isTransmitting.collectAsState()
            if (!showOnlyDirectories && !isTransmitting && entries.isEmpty()) {
                Text("No files found")
            }

            // Wait view
            val isInteractionDisabled = isTransmitting || isLoading
            if (isInteractionDisabled) {
                /*
                // Disable interactions
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {})*/
                // Show progress
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun RenameDialog(
    name: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        contentColor = Color.Black,
        title = { Text("Rename") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter new name for '$name'")
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
                    onConfirm(inputText)
                }) {
                Text("Rename")
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
                    onDismiss()
                }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ActionDialog(
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        contentColor = Color.Black,
        title = { Text("Available actions:") },
        buttons = {
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .padding(12.dp)
                    .defaultMinSize(minWidth = 200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onRename() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = BackgroundGradientStart)
                ) {
                    /* Icon(
                         tint = Color.White,
                         imageVector = Icons.Outlined.DriveFileRenameOutline,
                         contentDescription = null,
                         modifier = Modifier.padding(end = 4.dp)
                     )*/

                    Text("Rename", color = Color.White)
                }

                /*
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onMove() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = BackgroundGradientStart)
                ) {
                    /* Icon(
                         tint = Color.White,
                         imageVector = Icons.Outlined.DriveFileMove,
                         contentDescription = null,
                         modifier = Modifier.padding(end = 4.dp)
                     )*/

                    Text("Move", color = Color.White)
                }*/

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onDelete() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = ButtonWarningBackground)
                ) {
                    /*
                        Icon(
                            tint = Color.White,
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )*/

                    Text("Delete", color = Color.White)

                }
            }
        }
    )
}

@Composable
private fun FileRow(
    name: String,
    size: Int,
    isHidden: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ButtonWithLongPress(
        onLongClick = {
            onLongClick()
        },
        onClick = { onClick() },
        colors = ButtonDefaults.textButtonColors(
            backgroundColor = Color.Transparent,
            contentColor = if (isHidden) Color.Gray else Color.White
        ),
        elevation = null,
        contentPadding = PaddingValues(0.dp),
    ) {
        ItemRow(
            imageVector = Icons.Outlined.InsertDriveFile,
            imageDescription = "File",
            name = name,
            size = size
        )
    }
}


@Composable
private fun ItemRow(imageVector: ImageVector, imageDescription: String, name: String, size: Int?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            imageVector = imageVector,
            contentDescription = imageDescription
        )

        Text(
            name,
            modifier = Modifier
                .weight(1.0f)
                .align(Alignment.CenterVertically)
        )

        if (size != null) {
            val text =
                if (size > 1024) String.format("%.0f KB", size / 1024.0f) else "$size B"
            Text(
                text,
                style = MaterialTheme.typography.caption,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

// region Previews
@Preview(showSystemUi = true)
@Composable
private fun FileSystemScreenPreview() {
    GliderTheme {
        BackgroundGradientDefault {

            FileSystemScreen(
                path = "/",
                onPathChange = {},
                isLoading = false,
                showOnlyDirectories = false
            ) { selectedFilePath ->
                // on file selected
            }
        }
    }
}

//endregion