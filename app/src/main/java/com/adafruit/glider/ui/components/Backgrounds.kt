package com.adafruit.glider.ui.components

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import com.adafruit.glider.ui.theme.BackgroundGradientEnd
import com.adafruit.glider.ui.theme.BackgroundGradientStart

/*
@Composable
fun BackgroundFillMaxSize(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = contentColorFor(backgroundColor = MaterialTheme.colorScheme.background)
    ) {
        content()
    }
}*/

@Composable
fun BackgroundGradientFillMaxSize(
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit
) {
    BackgroundGradient(contentAlignment = contentAlignment, modifier = Modifier.fillMaxSize()) {
        content()
    }
}

@Composable
fun BackgroundGradient(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit
) {

    Surface(modifier = modifier) {
        Box(
            modifier = Modifier.background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            BackgroundGradientStart, BackgroundGradientEnd
                        )
                    )
                ),
            contentAlignment = contentAlignment,
        ) {
            content()
        }
    }

}

@Preview(showSystemUi = true)
@Composable
fun BackgroundPreview() {
    BackgroundGradientFillMaxSize {}
}
