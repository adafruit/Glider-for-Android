package com.adafruit.glider.ui.fileexplorer

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.ui.components.BackgroundGradientFillMaxSize
import com.adafruit.glider.ui.theme.GliderTheme
import io.openroad.filetransfer.ble.scanner.BlePeripheralScannerFake
import io.openroad.filetransfer.filetransfer.ConnectionManager
import io.openroad.filetransfer.utils.filenameFromPath
import io.openroad.filetransfer.wifi.scanner.WifiPeripheralScannerFake

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FileEditScaffoldingScreen(
    connectionManager: ConnectionManager,
    path: String,
    navController: NavHostController = rememberNavController()
) {
    val title = filenameFromPath(path)

    Scaffold(
        contentWindowInsets = if (WindowInsets.isImeVisible) WindowInsets.ime else WindowInsets.navigationBars,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
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
            FileEditScreen(
                modifier = Modifier.padding(innerPadding),
                connectionManager = connectionManager,
                path = path
            )
        }
    }
}

@Composable
fun FileEditScreen(
    modifier: Modifier = Modifier,
    connectionManager: ConnectionManager,
    path: String,
    viewModel: FileEditViewModel = viewModel(),
) {
    // on Appear
    val fileTransferClient = connectionManager.currentFileTransferClient.collectAsState()

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
    val isLoading by connectionManager.isReconnectingToBondedPeripherals.collectAsState()
    val isInteractionDisabled = isTransmitting || isLoading
    val mainColor = Color.White.copy(alpha = 0.7f)
    var editedText by remember { mutableStateOf("") }
    val hasChanged = text != editedText

    // Update editText when text is updated
    LaunchedEffect(text) {
        text?.let { editedText = it }
    }

    Column(
        modifier
            .fillMaxHeight()
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1.0f)
                .clip(RoundedCornerShape(4.dp))
        ) {

            TextField(
                value = editedText,
                onValueChange = {
                    editedText = it
                },
                modifier = Modifier
                    .fillMaxSize(),
                label = null,
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    cursorColor = Color.Black,
                    focusedIndicatorColor = Color.Transparent, //hide the indicator
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            // Status bar
            val lastTransmit by viewModel.lastTransmit.collectAsState()
            val transmissionProgress by viewModel.transmissionProgress.collectAsState()

            Box(Modifier.align(Alignment.BottomStart)) {
                FileCommandStatusBarView(
                    backgroundColor = Color.Gray.copy(0.8f),
                    lastTransmit = lastTransmit,
                    transmissionProgress = transmissionProgress,
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
                    containerColor = Color.Transparent,
                    contentColor = mainColor,
                    disabledContentColor = Color.Gray,
                ),
                border = BorderStroke(1.dp, mainColor),
            ) {
                Icon(
                    if (hasChanged) Icons.Outlined.Save else Icons.Outlined.TaskAlt,
                    modifier = Modifier.padding(end = 2.dp),
                    contentDescription = "Save"
                )

                Text(if (hasChanged) "Save" else "Saved")
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = { editedText = "" },
                enabled = !isInteractionDisabled,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = mainColor,
                    disabledContentColor = Color.Gray,
                ),
                border = BorderStroke(1.dp, mainColor),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    modifier = Modifier.padding(end = 2.dp),
                    contentDescription = "Clear"
                )

                Text("Clear")
            }
        }
    }
}

// region Previews
@Preview(showSystemUi = true)
@Composable
private fun FileEditScreenPreview() {
    val connectionManager = ConnectionManager(
        LocalContext.current,
        BlePeripheralScannerFake(),
        WifiPeripheralScannerFake()
    )

    GliderTheme {
        FileEditScaffoldingScreen(connectionManager = connectionManager, path = "file.txt")
    }
}
//endregion