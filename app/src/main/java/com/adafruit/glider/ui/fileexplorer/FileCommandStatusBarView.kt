package com.adafruit.glider.ui.fileexplorer
/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adafruit.glider.ui.theme.AccentMain
import com.adafruit.glider.ui.theme.ControlsOutline
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.ui.theme.WarningBackground
import com.adafruit.glider.utils.capitalized
import io.openroad.filetransfer.filetransfer.TransmissionLog
import io.openroad.filetransfer.filetransfer.TransmissionProgress

@Composable
fun FileCommandStatusBarView(
    backgroundColor: Color = ControlsOutline,
    lastTransmit: TransmissionLog?,
    transmissionProgress: TransmissionProgress?
) {

    val transmitColor = if (lastTransmit?.isError == true) Color.White else Color.Black
    val transmitBackgroundColor = if (lastTransmit?.isError == true) WarningBackground else backgroundColor

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
                color = transmitColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Progress
        transmissionProgress?.let {
            val totalBytes = it.totalBytes?.toFloat()
            if (totalBytes != null) {
                val progress = it.transmittedBytes.toFloat() / (it.totalBytes?.toFloat() ?: 1f)
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = AccentMain,
                    trackColor = Color.LightGray//Color.Transparent
                )
            }
        }
    }
}

// region Previews
@Preview
@Composable
private fun FileCommandStatusBarViewLastTransmitPreview() {
    GliderTheme {
        val lastTransmit = TransmissionLog(TransmissionLog.TransmissionType.ListDirectory(12))
        FileCommandStatusBarView(lastTransmit = lastTransmit, transmissionProgress = null)
    }
}

@Preview
@Composable
private fun FileCommandStatusBarViewTransmissionProgressPreview() {
    GliderTheme {
        val lastTransmit = TransmissionLog(TransmissionLog.TransmissionType.Read(100))
        val transmissionProgress = TransmissionProgress("test", 34, 100)
        FileCommandStatusBarView(lastTransmit = lastTransmit, transmissionProgress = transmissionProgress)
    }
}

@Preview
@Composable
private fun FileCommandStatusBarViewTransmissionErrorPreview() {
    GliderTheme {
        val lastTransmit = TransmissionLog(TransmissionLog.TransmissionType.Error("Error found"))
        FileCommandStatusBarView(lastTransmit = lastTransmit, transmissionProgress = null)
    }
}
//endregion