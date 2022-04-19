package com.adafruit.glider.ui.fileexplorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adafruit.glider.ui.theme.AccentMain
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.utils.capitalized

/**
 * Created by Antonio García (antonio@openroad.es)
 */

@Composable
fun FileCommandStatusBarView(
    backgroundColor: Color = Color.Gray,
    viewModel: FileCommandsViewModel = viewModel(),
) {
    val lastTransmit by viewModel.lastTransmit.collectAsState()
    val transmissionProgress by viewModel.transmissionProgress.collectAsState()

    val transmitBackgroundColor = if (lastTransmit?.isError == true) Color.Red else backgroundColor
    Column(
        Modifier
            .fillMaxWidth()
            .background(transmitBackgroundColor)

    ) {
        // Status log
        lastTransmit?.let {
            Text(
                it.description.capitalized(),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.caption
            )
        }

        // Progress
        transmissionProgress?.let {
            val totalBytes = it.totalBytes?.toFloat()
            if (totalBytes != null) {
                val progress = it.transmittedBytes.toFloat() / (it.totalBytes?.toFloat() ?: 1f)
                LinearProgressIndicator(
                    progress,
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = AccentMain,
                    backgroundColor = Color.Transparent
                )
            }
        }
    }
}

// region Previews
@Preview
@Composable
private fun FileCommandStatusBarViewPreview() {
    GliderTheme {
        FileCommandStatusBarView()
    }
}
//endregion