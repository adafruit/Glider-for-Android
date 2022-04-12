package com.adafruit.glider.ui.fileexplorer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.GliderApplication
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.ui.theme.TopBarBackground
import io.openroad.ble.applicationContext
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
        (applicationContext as GliderApplication).appContainer.fileTransferClient

    LaunchedEffect(Unit) {
        fileTransferClient?.let { fileTransferClient ->
            viewModel.setup(
                filePath = path,
                fileTransferClient = fileTransferClient
            )
        }
    }

    //
    val text by viewModel.text.collectAsState()

    Column(
        Modifier
            .fillMaxHeight()
            .padding(innerPadding)
            .padding(20.dp)
    ) {
        TextField(
            value = text ?: "",
            onValueChange = {
                viewModel.setText(it)
            },
            label = null,
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.White,
                textColor = Color.Black,
                cursorColor = Color.Black,
                focusedIndicatorColor = Color.Transparent, //hide the indicator
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        /*
        Row(modifier = Modifier.fillMaxWidth()) {
            
        }*/
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