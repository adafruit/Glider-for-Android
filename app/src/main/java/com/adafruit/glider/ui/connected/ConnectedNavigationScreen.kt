package com.adafruit.glider.ui.connected

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.ui.GliderDestinations
import com.adafruit.glider.ui.components.BackgroundGradientFillMaxSize
import com.adafruit.glider.ui.components.GliderSnackbarHost
import com.adafruit.glider.ui.fileexplorer.FileExplorerScreen
import com.adafruit.glider.ui.theme.GliderTheme
import io.openroad.filetransfer.ble.peripheral.BondedBlePeripherals
import io.openroad.filetransfer.ble.scanner.BlePeripheralScannerFake
import io.openroad.filetransfer.ble.utils.LogManager
import io.openroad.filetransfer.filetransfer.ConnectionManager
import io.openroad.filetransfer.wifi.peripheral.SavedSettingsWifiPeripherals
import io.openroad.filetransfer.wifi.scanner.WifiPeripheralScannerFake

val initialDestination = ConnectedNavigationDestinations.Peripherals.route     // Debug
//val initialDestination = ConnectedNavigationDestinations.FileExplorer.route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedNavigationScreen(
    navController: NavHostController = rememberNavController(),
    connectionManager: ConnectionManager,
    bondedBlePeripherals: BondedBlePeripherals,
    savedSettingsWifiPeripherals: SavedSettingsWifiPeripherals,
) {
    val navControllerBottomBar = rememberNavController()
    val backStackEntry = navControllerBottomBar.currentBackStackEntryAsState()
    val currentScreen =
        ConnectedNavigationDestinations.fromRoute(backStackEntry.value?.destination?.route)
    val snackBarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { GliderSnackbarHost(snackBarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(currentScreen.title()) },
                actions = {
                    when (currentScreen) {
                        ConnectedNavigationDestinations.Peripherals -> {
                            /*
                            IconButton(onClick = { isInfoDialogOpen.value = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "Info"
                                )
                            }*/
                        }
                        ConnectedNavigationDestinations.FileExplorer -> {}
                        ConnectedNavigationDestinations.Log -> {
                            IconButton(onClick = { LogManager.clear() }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete"
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigation(
                navController = navControllerBottomBar, items = listOf(
                    BottomNavItem.Info,
                    BottomNavItem.FileExplorer,
                    BottomNavItem.Log,
                )
            )
        },
        //scaffoldState = scaffoldState,
    ) { innerPadding ->
        BackgroundGradientFillMaxSize {
            ConnectedNavGraph(
                connectionManager = connectionManager,
                bondedBlePeripherals = bondedBlePeripherals,
                savedSettingsWifiPeripherals = savedSettingsWifiPeripherals,
                navController = navController,
                navControllerBottomBar = navControllerBottomBar,
                snackBarHostState = snackBarHostState,
                innerPadding = innerPadding
            )
        }
    }
}

// region Body
@Composable
private fun ConnectedNavGraph(
    modifier: Modifier = Modifier,
    connectionManager: ConnectionManager,
    bondedBlePeripherals: BondedBlePeripherals,
    savedSettingsWifiPeripherals: SavedSettingsWifiPeripherals,
    navController: NavController,
    navControllerBottomBar: NavHostController,
    snackBarHostState: SnackbarHostState = remember { SnackbarHostState() },
    innerPadding: PaddingValues,
) {
    NavHost(
        navController = navControllerBottomBar,
        startDestination = initialDestination,
        modifier = modifier
    ) {

        // Peripherals
        composable(ConnectedNavigationDestinations.Peripherals.route) {
            val peripheralsViewModel: PeripheralsViewModel =
                viewModel(
                    factory = PeripheralsViewModel.provideFactory(
                        connectionManager = connectionManager,
                        bondedBlePeripherals = bondedBlePeripherals,
                        savedSettingsWifiPeripherals = savedSettingsWifiPeripherals
                    )
                )

            PeripheralsScreen(
                modifier = Modifier.padding(innerPadding),
                viewModel = peripheralsViewModel,
                snackBarHostState = snackBarHostState,
            )
        }

        // File Explorer
        composable(ConnectedNavigationDestinations.FileExplorer.route) {
            FileExplorerScreen(
                modifier = Modifier.padding(innerPadding),
                connectionManager = connectionManager,
                snackBarHostState = snackBarHostState,
                onShowSelectDirectory = { path ->
                    navController.navigate(GliderDestinations.MoveFile.createRoute(path))
                },
                onFileSelected = { selectedFilePath ->
                    navController.navigate(GliderDestinations.FileEdit.createRoute(selectedFilePath))
                })
        }

        // Log
        composable(ConnectedNavigationDestinations.Log.route) {
            LogScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

// endregion


// region BottomNavigation
sealed class BottomNavItem(
    var title: String,
    var imageVector: ImageVector,
    var screenRoute: String
) {
    object Info : BottomNavItem(
        "Peripherals", Icons.Outlined.Link,
        ConnectedNavigationDestinations.Peripherals.route
    )

    object FileExplorer : BottomNavItem(
        "Explorer",
        Icons.Outlined.FolderOpen,
        ConnectedNavigationDestinations.FileExplorer.route
    )

    object Log : BottomNavItem(
        "Log", Icons.Outlined.Code,
        ConnectedNavigationDestinations.Log.route
    )
}

@Composable
private fun BottomNavigation(navController: NavController, items: List<BottomNavItem>) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.screenRoute,
                onClick = {
                    navController.navigate(item.screenRoute) {}
                },
                icon = {
                    Icon(
                        imageVector = item.imageVector,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(text = item.title)
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(

                )

            )
        }
    }
}
// endregion

// region Previews
@Preview(showSystemUi = true)
@SuppressLint("InlinedApi")
@RequiresPermission(allOf = [BLUETOOTH_SCAN, BLUETOOTH_CONNECT])
@Composable
private fun ConnectedTabScreenPreview() {
    GliderTheme {
        val connectionManager = ConnectionManager(
            LocalContext.current,
            BlePeripheralScannerFake(),
            WifiPeripheralScannerFake()
        )
        BackgroundGradientFillMaxSize {
            ConnectedNavigationScreen(
                connectionManager = connectionManager,
                bondedBlePeripherals = BondedBlePeripherals(LocalContext.current),
                savedSettingsWifiPeripherals = SavedSettingsWifiPeripherals(LocalContext.current)
            )
        }
    }
}
//endregion
