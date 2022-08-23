package com.adafruit.glider.ui.fileexplorer

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adafruit.glider.ui.components.BackgroundGradientFillMaxSize
import com.adafruit.glider.ui.components.InputTextActionDialog
import com.adafruit.glider.ui.theme.BackgroundDefault
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.ui.theme.WarningBackground
import com.adafruit.glider.ui.components.gesturesDisabled
import io.openroad.ble.peripheral.BlePeripheral
import io.openroad.filetransfer.ConnectionManager
import io.openroad.filetransfer.DirectoryEntry

@Composable
fun FileSystemScreen(
    connectionManager: ConnectionManager,
    path: String,
    onPathChange: (String) -> Unit,
    showOnlyDirectories: Boolean,
    isLoading: Boolean,
    viewModel: FileSystemViewModel = viewModel(),
    onShowSelectDirectory: (String) -> Unit,
    onFileSelected: (String) -> Unit,
) {
    val fileTransferClient by
    connectionManager.currentFileTransferClient.collectAsState()
    val isRootDirectory by viewModel.isRootDirectory.collectAsState()

    // On Appear -> Setup
    LaunchedEffect(Unit) {
        fileTransferClient?.let { fileTransferClient ->
            viewModel.showOnlyDirectories = showOnlyDirectories
            viewModel.setup(
                directory = path,
                fileTransferClient = fileTransferClient
            )
        }
    }

    // Back button management
    BackHandler {
        // For non-root directory, pressing back lists the parent directory
        fileTransferClient?.let { fileTransferClient ->
            if (!isRootDirectory) {
                viewModel.listParentDirectory(fileTransferClient)?.let { newPath ->
                    onPathChange(newPath)
                }
            }/* else {
                viewModel.disconnect(fileTransferClient)
            }*/
        }
    }

    //
    Box(
        Modifier
            .gesturesDisabled(isLoading)
            .fillMaxSize()
    ) {
        // Items
        val entries by viewModel.entries.collectAsState()
        val actionDialogEntry =
            remember { mutableStateOf<DirectoryEntry?>(null) }
        val renameDialogEntry =
            remember { mutableStateOf<DirectoryEntry?>(null) }

        FileSystemEntries(
            entries = entries,
            isRootDirectory = isRootDirectory,
            onListParentDirectory = {
                fileTransferClient?.let { fileTransferClient ->
                    viewModel.listParentDirectory(fileTransferClient)?.let { newPath ->
                        onPathChange(newPath)
                    }
                }
            },
            onEntrySelected = { entry ->
                if (entry.isDirectory) {
                    val newPath = path + entry.name + "/"
                    onPathChange(newPath)
                    fileTransferClient?.let { fileTransferClient ->
                        viewModel.listDirectory(newPath, fileTransferClient)
                    }
                } else {
                    onFileSelected(path + entry.name)
                }
            },
            onDelete = { entry ->
                actionDialogEntry.value = null

                fileTransferClient?.let { fileTransferClient ->
                    viewModel.delete(entry, fileTransferClient) {}
                }
            },
            onShowActions = { entry ->
                actionDialogEntry.value = entry
            }
        )

        // Actions dialog
        fileTransferClient?.let { fileTransferClient ->
            actionDialogEntry.value?.let { entry ->
                // Move is only available for Ble Peripherals (REST API doesn't include the move command)
                val onMove = if (fileTransferClient.peripheral is BlePeripheral) {
                    {
                        actionDialogEntry.value = null
                        onShowSelectDirectory(path)
                    }
                } else null

                FileActionsDialog(
                    onRename = {
                        actionDialogEntry.value = null
                        renameDialogEntry.value = entry
                    },
                    onMove = onMove,
                    onDelete = {
                        actionDialogEntry.value = null
                        viewModel.delete(entry, fileTransferClient) {}
                    },
                    onDismiss = { actionDialogEntry.value = null })
            }

            renameDialogEntry.value?.let { entry ->
                InputTextActionDialog(
                    alertText = "Rename",
                    alertMessage = "Enter new name for '${entry.name}'",
                    placeholderText = "New name",
                    actionText = "Rename",
                ) { filename ->
                    renameDialogEntry.value = null
                    filename?.let {
                        val fromPath = viewModel.path.value + entry.name
                        val toPath = viewModel.path.value + filename
                        viewModel.renameFile(fromPath, toPath, fileTransferClient)
                    }
                }
            }
        }

        // Empty View / Wait View
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = spacedBy(12.dp),
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
                // Show progress
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FileSystemEntries(
    modifier: Modifier = Modifier,
    entries: List<DirectoryEntry>,
    isRootDirectory: Boolean,
    onListParentDirectory: () -> Unit,
    onEntrySelected: (DirectoryEntry) -> Unit,
    onDelete: (DirectoryEntry) -> Unit,
    onShowActions: (DirectoryEntry) -> Unit,
) {
    // TODO: use LazyColumn instead of Column

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier.verticalScroll(scrollState),
    ) {
        Spacer(Modifier.height(8.0.dp))

        if (!isRootDirectory) {
            //item {
            Button(
                onClick = onListParentDirectory,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = Color.Transparent,
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
            key(entry.hashCode()) {     // Needed for SwipeToDismiss. Use key in LazyColumn
                // Delete action
                val dismissState = rememberDismissState(
                    initialValue = DismissValue.Default,
                    confirmStateChange = {
                        if (it == DismissValue.DismissedToStart) {
                            onDelete(entry)
                        } else if (it == DismissValue.DismissedToEnd) {
                            onShowActions(entry)
                        }

                        val success =
                            it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart
                        success
                    }
                )

                // Auto-reset SwipeToDismiss
                LaunchedEffect(dismissState.currentValue) {
                    if (dismissState.currentValue != DismissValue.Default) {
                        dismissState.reset()
                    }
                }

                // Swipe actions
                SwipeToDismiss(
                    state = dismissState,
                    //modifier = Modifier.padding(vertical = 4.dp),
                    directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
                    background = {
                        val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
                        val color by animateColorAsState(
                            when (dismissState.targetValue) {
                                DismissValue.Default -> Color.LightGray
                                DismissValue.DismissedToEnd -> Color.Green
                                DismissValue.DismissedToStart -> Color.Red
                            }
                        )

                        val alignment = when (direction) {
                            DismissDirection.StartToEnd -> Alignment.CenterStart
                            DismissDirection.EndToStart -> Alignment.CenterEnd
                        }
                        val iconAlignment = when (direction) {
                            DismissDirection.StartToEnd -> Icons.Default.MoreHoriz
                            DismissDirection.EndToStart -> Icons.Default.Delete
                        }
                        val iconDescription = when (direction) {
                            DismissDirection.StartToEnd -> "More actions"
                            DismissDirection.EndToStart -> "Delete"
                        }
                        val scale by animateFloatAsState(
                            if (dismissState.targetValue == DismissValue.Default) 0.75f else 1f
                        )

                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(color)
                                .padding(horizontal = 20.dp),
                            contentAlignment = alignment
                        ) {
                            Icon(
                                iconAlignment,
                                contentDescription = iconDescription,
                                modifier = Modifier.scale(scale)
                            )
                        }
                    },
                    dismissContent = {
                        val elevation = animateDpAsState(
                            if (dismissState.dismissDirection != null) 4.dp else 0.dp
                        ).value

                        val backgroundColor =
                            if (dismissState.dismissDirection != null) BackgroundDefault else Color.Transparent

                        // Add a surface to control the background and elevation while is being swiped
                        Surface(
                            color = backgroundColor,
                            tonalElevation = elevation,
                            //shadowElevation = elevation,
                        ) {
                            FileSystemEntry(
                                entry = entry,
                                onEntrySelected = onEntrySelected,
                            )
                        }
                    })
            }
        }

        Spacer(Modifier.height(20.0.dp))
    }
}


@Composable
private fun FileSystemEntry(
    entry: DirectoryEntry,
    onEntrySelected: (DirectoryEntry) -> Unit,
) {
    when (entry.type) {
        is DirectoryEntry.EntryType.File -> {

            FileRow(
                //modifier = Modifier.border(1.dp, Color.Green),
                name = entry.name,
                size = entry.type.size,
                isHidden = entry.isHidden,
                onClick = {
                    onEntrySelected(entry)
                })
        }

        is DirectoryEntry.EntryType.Directory -> {
            Button(
                onClick = {
                    onEntrySelected(entry)
                },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = Color.Transparent,
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

@Composable
fun FileActionsDialog(
    onRename: (() -> Unit)?,
    onMove: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    // TODO: update to material3
    AlertDialog(
        onDismissRequest = { onDismiss() },
        contentColor = BackgroundDefault,
        title = { Text("Available actions:", color = Color.Black, textAlign = TextAlign.Center) },
        buttons = {
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .padding(12.dp)
                    .defaultMinSize(minWidth = 200.dp),
                verticalArrangement = spacedBy(4.dp)
            ) {
                onRename?.let {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { it() },
                        colors = ButtonDefaults.outlinedButtonColors()//(contentColor = BackgroundDefault)
                    ) {
                        Text("Rename")
                    }
                }

                onMove?.let {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { it() },
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("Move")
                    }
                }

                onDelete?.let {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { it() },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningBackground)
                    ) {
                        Text("Delete", color = Color.White)
                    }
                }
            }
        }
    )
}

@Composable
private fun FileRow(
    modifier: Modifier = Modifier,
    name: String,
    size: Int,
    isHidden: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier,
        onClick = { onClick() },
        colors = ButtonDefaults.textButtonColors(
            containerColor = Color.Transparent,
            contentColor = if (isHidden) Color.Gray else Color.White,
        ),
        shape = RoundedCornerShape(8.dp),
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = spacedBy(12.dp)) {
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
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

// region Previews
/*
@Preview(showSystemUi = true)
@Composable
private fun FileSystemScreenPreview() {
    val connectionManager = ConnectionManager(WifiPeripheralScannerFake())

    GliderTheme {
        BackgroundGradientFillMaxSize {
            FileSystemScreen(
                connectionManager = connectionManager,
                path = "/",
                onPathChange = {},
                isLoading = false,
                showOnlyDirectories = false
            ) {}
        }
    }
}
*/
@Preview(showSystemUi = true)
@Composable
private fun FileSystemEntriesPreview() {
    val entries = listOf(
        DirectoryEntry("folder", DirectoryEntry.EntryType.Directory),
        DirectoryEntry(".hidden folder", DirectoryEntry.EntryType.Directory),
        DirectoryEntry("file.txt", DirectoryEntry.EntryType.File(123)),
    )

    GliderTheme {
        BackgroundGradientFillMaxSize {
            FileSystemEntries(
                modifier = Modifier.padding(20.dp),
                entries = entries,
                isRootDirectory = false,
                onListParentDirectory = {},
                onEntrySelected = { },
                onDelete = {},
                onShowActions = {},
            )
        }
    }
}

//endregion