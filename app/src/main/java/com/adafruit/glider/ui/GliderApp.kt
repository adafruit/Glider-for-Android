package com.adafruit.glider.ui

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.AppContainer

@Composable
fun GliderApp(
    appContainer: AppContainer,
) {
    // Show standard NavGraph
    val navController = rememberNavController()
    GliderNavGraph(
        appContainer = appContainer,
        navController = navController,
    )
}
