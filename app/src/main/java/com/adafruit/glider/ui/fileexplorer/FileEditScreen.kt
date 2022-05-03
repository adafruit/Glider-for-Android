package com.adafruit.glider.ui.fileexplorer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.ui.theme.TopBarBackground
import io.openroad.ble.filetransfer.FileTransferConnectionManager
import io.openroad.ble.utils.filenameFromPath

/**
 * Created by Antonio García (antonio@openroad.es)
 */

@Composable
fun FileEditScaffoldingScreen(
    path: String,
    navController: NavHostController = rememberNavController()
) {
    val title = filenameFromPath(path)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                backgroundColor = TopBarBackground,
                navigationIcon = if (navController.previousBackStackEntry != null) {
                    {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                } else {
                    null
                }
            )
        },
    ) { innerPadding ->
        FileEditScreen(path, innerPadding)
    }
}

@Composable
fun FileEditScreen(
    path: String,
    innerPadding: PaddingValues,
    viewModel: FileEditViewModel = viewModel(),
) {
    // on Appear
    val fileTransferClient =
        //(applicationContext as GliderApplication).appContainer.fileTransferClient
        FileTransferConnectionManager.selectedFileTransferClient.collectAsState()

    LaunchedEffect(Unit) {
        fileTransferClient.value?.let { fileTransferClient ->
            viewModel.setup(
                filePath = path,
                fileTransferClient = fileTransferClient
            )
        }
    }

    //
    val text by viewModel.text.collectAsState()

    val isTransmitting by viewModel.isTransmitting.collectAsState()
    val isLoading by FileTransferConnectionManager.isSelectedPeripheralReconnecting.collectAsState()
    val isInteractionDisabled = isTransmitting || isLoading
    val mainColor = Color.White.copy(alpha = 0.7f)
    var editedText by remember { mutableStateOf("") }
    val hasChanged = text != editedText

    // Update editText when text is updated
    LaunchedEffect(text) {
        text?.let { editedText = it }
    }

    Column(
        Modifier
            .fillMaxHeight()
            .padding(innerPadding)
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1.0f)
                .clip(RoundedCornerShape(4.dp))
        ) {
            TextField(
                value = editedText ?: "",
                onValueChange = {
                    editedText = it
                },
                modifier = Modifier.fillMaxSize(),
                label = null,
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = Color.White,
                    textColor = Color.Black,
                    cursorColor = Color.Black,
                    focusedIndicatorColor = Color.Transparent, //hide the indicator
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            // Status bar
            Box(Modifier.align(Alignment.BottomStart)) {
                FileCommandStatusBarView(
                    viewModel = viewModel,
                    backgroundColor = Color.Gray.copy(0.8f)
                )
            }

            // Wait View
            Box(Modifier.align(Alignment.Center)) {
                if (isInteractionDisabled) {
                    CircularProgressIndicator(color = Color.Gray)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {

            OutlinedButton(
                onClick = {
                    fileTransferClient.value?.let { fileTransferClient ->
                        viewModel.writeFile(
                            path,
                            editedText.toByteArray(Charsets.UTF_8),
                            fileTransferClient
                        )
                    }

                },
                enabled = !isInteractionDisabled,
                colors = ButtonDefaults.textButtonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = mainColor,
                    disabledContentColor = Color.Gray,
                ),
                border = BorderStroke(1.dp, mainColor),
            ) {
                Icon(
                    if (hasChanged) Icons.Outlined.Save else Icons.Outlined.TaskAlt,
                    contentDescription = "Save"
                )

                Text(if (hasChanged) "Save" else "Saved")
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = { editedText = "" },
                enabled = !isInteractionDisabled,
                colors = ButtonDefaults.textButtonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = mainColor,
                    disabledContentColor = Color.Gray,
                ),
                border = BorderStroke(1.dp, mainColor),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Clear"
                )
            }


        }
    }

}


// region Previews
@Preview(showSystemUi = true)
@Composable
private fun FileEditScreenPreview() {
    GliderTheme {

        BackgroundGradientDefault {
            FileEditScaffoldingScreen("file.txt")
        }

    }
}
//endregion