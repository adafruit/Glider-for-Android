package com.adafruit.glider.ui

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.R
import com.adafruit.glider.ui.scan.ScanScreen
import com.adafruit.glider.ui.startup.StartupScreen
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.utils.observeAsState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.openroad.ble.getNeededPermissions
import io.openroad.ble.isLocationEnabled

// region Lifecycle
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Change status bar color in Compose: https://stackoverflow.com/questions/68980068/jetpack-compose-status-bar-color-not-updated-in-dark-theme
            SideEffect {
                this.window.statusBarColor =
                    ContextCompat.getColor(this, R.color.status_bar_background)
            }

            GliderApp()
        }
    }
}
// endregion

@Preview
@Composable
fun GliderApp() {
    val navController = rememberNavController()
    //val backstackEntry = navController.currentBackStackEntryAsState()
    //val currentScreen = GliderScreen.fromRoute(backstackEntry.value?.destination?.route)

    GliderTheme {
        GliderNavHost(
            navController = navController,
            //modifier = Modifier.padding(innerPadding)
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GliderNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val backstackEntry = navController.currentBackStackEntryAsState()
    val currentScreen = GliderScreen.fromRoute(backstackEntry.value?.destination?.route)

    // Check if location is enabled on Android lower than 12
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val lifeCycleState = LocalLifecycleOwner.current.lifecycle.observeAsState()
        if (lifeCycleState.value == Lifecycle.Event.ON_RESUME) {
            if (!isLocationEnabled(LocalContext.current)) {
                LaunchedEffect(lifeCycleState) {
                    navController.navigate(GliderScreen.BluetoothStatus.name)
                }
            }
        }
    }

    // Check Bluetooth-related permissions state
    val bluetoothPermissionState = rememberMultiplePermissionsState(getNeededPermissions())
    val isInitialPermissionsCheckInProgress =
        !bluetoothPermissionState.allPermissionsGranted && currentScreen == GliderScreen.Startup && !bluetoothPermissionState.shouldShowRationale


    if (!bluetoothPermissionState.allPermissionsGranted) {
        LaunchedEffect(bluetoothPermissionState) {
            if (isInitialPermissionsCheckInProgress) {
                // First time that permissions are needed at startup
                bluetoothPermissionState.launchMultiplePermissionRequest()
            } else {
                // Not at startup or not first time permissions are needed => Go to BluetoothStatus screen
                navController.navigate(GliderScreen.BluetoothStatus.name)
            }
        }
    }

    // NavHost
    NavHost(
        navController = navController,
        startDestination = GliderScreen.Startup.name,
        modifier = modifier
    ) {

        composable(GliderScreen.Startup.name) {
            StartupScreen(isInitialPermissionsCheckInProgress) {
                // on finish startup
                navController.navigate(GliderScreen.Scan.name) {
                    // Don't go back to startup on pop
                    popUpTo(GliderScreen.Startup.name) {
                        inclusive = true
                    }
                }
            }
        }
        composable(GliderScreen.BluetoothStatus.name) {
            // TODO
        }
        composable(GliderScreen.Scan.name) {
            ScanScreen()

        }
        composable(GliderScreen.ConnectedTab.name) {
            // TODO
        }
    }
}
