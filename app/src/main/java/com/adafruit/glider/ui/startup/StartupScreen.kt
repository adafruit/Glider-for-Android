package com.adafruit.glider.ui.startup

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adafruit.glider.R
import com.adafruit.glider.ui.components.BackgroundGradientFillMaxSize
import com.adafruit.glider.ui.theme.GliderTheme

@Composable
fun StartupScreen() {

    // UI
    BackgroundGradientFillMaxSize(contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.glider_logo),
                contentDescription = "Glider logo",
            )

            /*
            Text(
                "Restoring Connection...",
                Modifier.alpha(if (isRestoringConnection) 1.0f else 0.0f)
            )*/

            CircularProgressIndicator(color = Color.White)
        }
    }
}

// region Previews
@Preview(showSystemUi = true)
@Composable
private fun StartupPreview() {
    GliderTheme {
        StartupScreen()
    }
}
//endregion
