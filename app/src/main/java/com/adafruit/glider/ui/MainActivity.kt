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
import com.adafruit.glider.GliderApplication
import com.adafruit.glider.R
import com.adafruit.glider.ui.fileexplorer.FileEditScaffoldingScreen
import com.adafruit.glider.ui.fileexplorer.FileExplorerScaffoldingScreen
import com.adafruit.glider.ui.scan.ScanScreen
import com.adafruit.glider.ui.startup.StartupScreen
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.utils.observeAsState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.openroad.ble.BleManager
import io.openroad.ble.applicationContext
import io.openroad.ble.filetransfer.FileTransferConnectionManager

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
    val backStackEntry = navController.currentBackStackEntryAsState()
    val currentScreen = ScreenRoute.fromRoute(backStackEntry.value?.destination?.route)

    // Check if location is enabled on Android lower than 12
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val lifeCycleState = LocalLifecycleOwner.current.lifecycle.observeAsState()
        if (lifeCycleState.value == Lifecycle.Event.ON_RESUME) {
            if (!BleManager.isLocationEnabled(LocalContext.current)) {
                LaunchedEffect(lifeCycleState) {
                    navController.navigate(ScreenRoute.BluetoothStatus.route)
                }
            }
        }
    }

    // Check Bluetooth-related permissions state
    val bluetoothPermissionState = rememberMultiplePermissionsState(BleManager.getNeededPermissions())
    val isInitialPermissionsCheckInProgress =
        !bluetoothPermissionState.allPermissionsGranted && currentScreen == ScreenRoute.Startup && !bluetoothPermissionState.shouldShowRationale

    if (!bluetoothPermissionState.allPermissionsGranted) {
        LaunchedEffect(bluetoothPermissionState) {
            if (isInitialPermissionsCheckInProgress) {
                // First time that permissions are needed at startup
                bluetoothPermissionState.launchMultiplePermissionRequest()
            } else {
                // Not at startup or not first time permissions are needed => Go to BluetoothStatus screen
                navController.navigate(ScreenRoute.BluetoothStatus.route)
            }
        }
    }

    // NavHost
    NavHost(
        navController = navController,
        startDestination = ScreenRoute.Startup.route,
        modifier = modifier
    ) {

        composable(ScreenRoute.Startup.route) {
            StartupScreen(isInitialPermissionsCheckInProgress) {
                // on finish startup
                val reconnectedFileTransferClient = FileTransferConnectionManager.selectedFileTransferClient.value
                val screenRoute = if (reconnectedFileTransferClient != null) {
                    ScreenRoute.ConnectedTab
                } else {
                    ScreenRoute.Scan
                }

                navController.navigate(screenRoute.route) {
                    // Don't go back to startup on pop
                    popUpTo(ScreenRoute.Startup.route) {
                        inclusive = true
                    }
                }
            }
        }

        composable(ScreenRoute.BluetoothStatus.route) {
            // TODO
        }

        composable(ScreenRoute.Scan.route) {
            ScanScreen { fileTransferPeripheral ->
                // on finish scan -> go to connected
                /*
                val appContainer = (applicationContext as GliderApplication).appContainer
                appContainer.fileTransferClient = fileTransferClient        // TODO: clean this. it should be in appContainer
                */
                FileTransferConnectionManager.setSelectedPeripheral(fileTransferPeripheral)

                navController.navigate(ScreenRoute.ConnectedTab.route) {
                    // Don't go back to scan on pop
                    popUpTo(ScreenRoute.Startup.route) {
                        inclusive = true
                    }
                }
            }
        }

        composable(ScreenRoute.ConnectedTab.route) {
            //ConnectedTabScreen()
            FileExplorerScaffoldingScreen() { selectedFilePath ->
                // on file selected
                navController.navigate(ScreenRoute.FileEdit.createRoute(selectedFilePath))

            }
        }

        composable(ScreenRoute.FileEdit.route) { backStackEntry ->
            val pathArg = backStackEntry.arguments?.getString("path")
            requireNotNull(pathArg) { "path parameter not found" }
            val path = ScreenRoute.FileEdit.decodePathArg(pathArg)
            FileEditScaffoldingScreen(path, navController)
        }
    }
}
