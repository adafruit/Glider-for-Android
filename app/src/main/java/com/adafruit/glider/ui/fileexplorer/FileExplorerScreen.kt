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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.GliderApplication
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.ui.theme.TopBarBackground

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
    val appContainer =
        (LocalContext.current.applicationContext as GliderApplication).appContainer
    val peripheralName = appContainer.fileTransferClient?.peripheralName
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
                    Text(
                        path,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .border(BorderStroke(1.dp, mainColor), RoundedCornerShape(4.dp))
                            .weight(1.0f)
                            .padding(9.dp),
                    )

                    OutlinedButton(
                        onClick = { /*TODO*/ },
                        colors = ButtonDefaults.textButtonColors(
                            backgroundColor = Color.Transparent,
                            contentColor = mainColor
                        ),
                        border = BorderStroke(1.dp, mainColor),
                    ) {
                        Icon(
                            Icons.Outlined.CreateNewFolder,
                            contentDescription = "New Folder"
                        )
                    }

                    OutlinedButton(
                        onClick = { /*TODO*/ },
                        colors = ButtonDefaults.textButtonColors(
                            backgroundColor = Color.Transparent,
                            contentColor = mainColor
                        ),
                        border = BorderStroke(1.dp, mainColor),
                    ) {
                        Icon(
                            Icons.Outlined.NoteAdd,
                            contentDescription = "New Folder"
                        )
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