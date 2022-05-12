package com.adafruit.glider.ui.connected

import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.utils.LogManager

/**
 * Created by Antonio García (antonio@openroad.es)
 */

@Composable
fun LogScreen() {
    val entries by LogManager.entries.collectAsState()

    LazyColumn() {
        items(entries) { entry ->
            Row(horizontalArrangement = spacedBy(8.dp)) {
                Text(
                    entry.timeString(),

                    style = MaterialTheme.typography.caption,
                    color = Color.Gray,
                    modifier = Modifier.alignByBaseline()

                )
                Text(entry.text, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

// region Previews
@Preview(showSystemUi = true)
@Composable
private fun LogScreenPreview() {
    GliderTheme {
        BackgroundGradientDefault {
            LogScreen()
        }
    }
}

//endregion