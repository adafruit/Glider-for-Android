package com.adafruit.glider.ui.fileexplorer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.ui.theme.TabBackground
import com.adafruit.glider.ui.theme.TopBarBackground
import io.openroad.ble.filetransfer.FileTransferConnectionManager

/**
 * Created by Antonio García (antonio@openroad.es)
 */


@Composable
fun FileExplorerScaffoldingScreen(
    onFileSelected: (String) -> Unit,
) {
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Explorer") }, backgroundColor = TopBarBackground
            )
        },
    ) { innerPadding ->
        FileExplorerScreen(innerPadding, onFileSelected = onFileSelected)
    }
}


@Composable
fun FileExplorerScreen(
    innerPadding: PaddingValues,
    viewModel: FileSystemViewModel = viewModel(),
    onFileSelected: (String) -> Unit,
) {

    val mainColor = Color.White.copy(alpha = 0.7f)
    //val appContainer = (LocalContext.current.applicationContext as GliderApplication).appContainer
    val fileTransferClient =
        FileTransferConnectionManager.selectedFileTransferClient.collectAsState()
    val peripheralName = fileTransferClient.value?.peripheralName
    var path by remember { mutableStateOf("/") }

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
                        alertMessage = "Enter name for the new directory"
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
                        alertMessage = "Enter name for the new file"
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
                .padding(8.dp)
                .fillMaxWidth()
                .fillMaxHeight()

        ) {
            FileSystemScreen(
                path = path,
                onPathChange = { path = it },
                showOnlyDirectories = false,
                viewModel = viewModel
            ) { selectedFilePath ->
                // on file selected
                onFileSelected(selectedFilePath)
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
    onNewInputText: (String) -> Unit
) {
    val isDialogOpen = remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    // New Directory
    OutlinedButton(
        onClick = { isDialogOpen.value = true },
        colors = ButtonDefaults.textButtonColors(
            backgroundColor = Color.Transparent,
            contentColor = mainColor
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