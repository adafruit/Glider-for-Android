package com.adafruit.glider.ui

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import com.adafruit.glider.ui.theme.BackgroundGradientEnd
import com.adafruit.glider.ui.theme.BackgroundGradientStart

@Composable
fun BackgroundDefault(content: @Composable () -> Unit) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background,
            contentColor = contentColorFor(backgroundColor = MaterialTheme.colors.background)
        ) {
            content()
        }
}


@Composable
fun BackgroundGradientDefault(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BackgroundGradientStart,
                        BackgroundGradientEnd
                    )
                )
            )
    ) {
        content()
    }
}

@Preview(showSystemUi = true)
@Composable
fun Background() {
    BackgroundGradientDefault {}
}
