package com.adafruit.glider.ui.fileexplorer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.theme.GliderTheme
import io.openroad.ble.filetransfer.BleFileTransferPeripheral
import io.openroad.ble.filetransfer.FileTransferConnectionManager
import io.openroad.ble.utils.upPath

/**
 * Created by Antonio García (antonio@openroad.es)
 */

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

    Box(Modifier.fillMaxSize()) {
        // Items
        val isRootDirectory by viewModel.isRootDirectory.collectAsState()
        val entries by viewModel.entries.collectAsState()

        val state = rememberScrollState()
        Column(modifier = Modifier.verticalScroll(state)) {
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
                            isHidden = entry.isHidden
                        ) {
                            // On click
                            onFileSelected(path + entry.name)
                        }
                    }

                    is BleFileTransferPeripheral.DirectoryEntry.EntryType.Directory -> {
                        Button(
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
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun FileRow(
    name: String,
    size: Int,
    isHidden: Boolean,
    onClick: () -> Unit
) {
    Button(
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
private fun ConnectedTabScreenPreview() {
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