package com.adafruit.glider.ui.connected

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adafruit.glider.ui.components.BackgroundGradientFillMaxSize
import com.adafruit.glider.ui.theme.GliderTheme
import io.openroad.filetransfer.ble.utils.LogManager
import java.util.logging.Level

@Composable
fun LogScreen(
    modifier: Modifier = Modifier
) {
    val entries by LogManager.entries.collectAsState()

    LogScreenBody(modifier = modifier, entries = entries)
}

@Composable
private fun LogScreenBody(
    modifier: Modifier = Modifier,
    entries: List<LogManager.Entry>
) {
    LazyColumn(modifier = modifier) {

        //entries.forEach { entry ->
        itemsIndexed(entries) { _, entry ->

            Row(horizontalArrangement = spacedBy(8.dp)) {
                // Time
                Text(
                    entry.timeString(),
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray,
                    modifier = Modifier.alignByBaseline()
                )

                // Text
                val color = when (entry.level) {
                    Level.FINE -> Color.LightGray
                    else -> Color.White
                }
                val size = when (entry.level) {
                    Level.FINE -> 12.sp
                    else -> TextUnit.Unspecified
                }

                Text(
                    entry.text,
                    modifier = Modifier.alignByBaseline(),
                    color = color,
                    fontSize = size
                )

                // Bottom content padding
                Spacer(Modifier.height(20.0.dp))
            }

        }
    }
}

// region Previews
@Preview(showSystemUi = true)
@Composable
private fun LogScreenPreview() {

    val entry = LogManager.Entry(
        LogManager.Entry.Category.FileTransferProtocol, Level.INFO, "Info message"
    )
    val entry2 = LogManager.Entry(
        LogManager.Entry.Category.FileTransferProtocol, Level.FINE, "Fine message"
    )

    val entries = listOf(entry, entry2)

    GliderTheme {
        BackgroundGradientFillMaxSize {
            LogScreenBody(entries = entries)
        }
    }
}

//endregion