package com.adafruit.glider.ui

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.AppContainer
import com.adafruit.glider.ui.connected.ConnectedNavigationScreen
import com.adafruit.glider.ui.fileexplorer.FileEditScaffoldingScreen
import com.adafruit.glider.ui.fileexplorer.FileSystemViewModel
import com.adafruit.glider.ui.fileexplorer.SelectDirectoryScreen

// Config
val initialDestination = GliderDestinations.ConnectedBottomNavigation.route     // Debug
//val initialDestination =GliderDestinations.Scan.route,       // GliderDestinations.Startup

@Composable
fun GliderNavGraph(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = initialDestination
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        /*
        // Startup
        composable(GliderDestinations.Startup.route) {
            StartupScreen() {
                // on finish startup
                val destination = GliderDestinations.Scan
                navController.navigate(destination.route) {
                    // Don't go back to startup on pop
                    popUpTo(GliderDestinations.Startup.route) {
                        inclusive = true
                    }
                }
            }
        }

        // Scan
        composable(GliderDestinations.Scan.route) {
            val scanViewModel: ScanViewModel =
                viewModel(
                    factory = ScanViewModel.provideFactory(
                        connectionManager = appContainer.connectionManager,
                        isAutoConnectEnabled = true,

                        // TODO: move all management inside ConnectionManager and remove the following callbacks
                        onBlePeripheralBonded = {name, address ->  TODO() },
                        onWifiPeripheralGetPasswordForHostName = {  _, hostName -> TODO() },
                    )
                )

            ScanScreen(viewModel = scanViewModel) { fileTransferClient ->
                // on device selected -> go to connected

                navController.navigate(GliderDestinations.ConnectedBottomNavigation.route) {
                    // Don't go back to scan on pop
                    popUpTo(GliderDestinations.Startup.route) {
                        inclusive = true
                    }
                }
            }
        }*/

        // ConnectedBottomNavigation
        composable(GliderDestinations.ConnectedBottomNavigation.route) {

            ConnectedNavigationScreen(
                navController = navController,
                connectionManager = appContainer.connectionManager,
                bondedBlePeripherals = appContainer.bondedBlePeripherals,
                savedSettingsWifiPeripherals = appContainer.savedSettingsWifiPeripherals,
            )
        }

        // File Edit
        composable(GliderDestinations.FileEdit.route) { backStackEntry ->
            val pathArg = backStackEntry.arguments?.getString("path")
            requireNotNull(pathArg) { "path parameter not found" }
            val path = GliderDestinations.FileEdit.decodePathArg(pathArg)
            FileEditScaffoldingScreen(
                connectionManager = appContainer.connectionManager,
                path = path,
                navController = navController
            )
        }

        // Move File
        composable(GliderDestinations.MoveFile.route) { backStackEntry ->
            val pathArg = backStackEntry.arguments?.getString("path")
            requireNotNull(pathArg) { "path parameter not found" }
            val path = GliderDestinations.MoveFile.decodePathArg(pathArg)
            val viewModel = remember { FileSystemViewModel() }

            SelectDirectoryScreen(
                viewModel = viewModel,
                connectionManager = appContainer.connectionManager,
                fromPath = path,
                navController = navController,
                actionText = "Move",
            ) { selectedPath ->

                if (selectedPath != null) {
                    appContainer.connectionManager.currentFileTransferClient.value?.let { fileTransferClient ->

                        viewModel.moveFile(
                            fromPath = path,
                            toPath = selectedPath,
                            fileTransferClient = fileTransferClient
                        ) { result ->
                            result.fold(
                                onSuccess = {
                                    navController.popBackStack()
                                },
                                onFailure = {
                                    // Do nothing
                                }
                            )

                        }
                    } ?: run {
                        navController.popBackStack()
                    }
                } else {
                    navController.popBackStack()
                }
            }
        }
    }
}