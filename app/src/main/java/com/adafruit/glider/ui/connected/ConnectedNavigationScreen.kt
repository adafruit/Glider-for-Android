package com.adafruit.glider.ui.connected

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.ui.GliderDestinations
import com.adafruit.glider.ui.components.BackgroundGradientFillMaxSize
import com.adafruit.glider.ui.fileexplorer.FileExplorerScreen
import com.adafruit.glider.ui.theme.GliderTheme
import io.openroad.filetransfer.ConnectionManager
import io.openroad.wifi.scanner.WifiPeripheralScannerFake

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedNavigationScreen(
    navController: NavHostController = rememberNavController(),
    connectionManager: ConnectionManager,
) {
    val navControllerBottomBar = rememberNavController()
    val backStackEntry = navControllerBottomBar.currentBackStackEntryAsState()
    val currentScreen =
        ConnectedNavigationDestinations.fromRoute(backStackEntry.value?.destination?.route)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(currentScreen.title()) }
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
                navController = navController,
                navControllerBottomBar = navControllerBottomBar,
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
    navController: NavController,
    navControllerBottomBar: NavHostController,
    innerPadding: PaddingValues,
) {
    NavHost(
        navController = navControllerBottomBar,
        startDestination = ConnectedNavigationDestinations.FileExplorer.route,
        modifier = modifier
    ) {

        // Info
        composable(ConnectedNavigationDestinations.Info.route) {
            InfoScreen(
                modifier = Modifier.padding(innerPadding),
                connectionManager = connectionManager
            )
        }

        composable(ConnectedNavigationDestinations.FileExplorer.route) { backStackEntry ->
/*
            // SelectedPath can be set by SelectDirectoryScreen when moving a file
            // Note: replace livedata with stateFlow: https://stackoverflow.com/questions/65120398/trying-to-expose-savedstatehandle-getlivedata-as-mutablestateflow-but-the-ui
            val selectedPath: String? by backStackEntry
                .savedStateHandle
                .getLiveData<String?>("selected_path")
                .observeAsState()*/


            FileExplorerScreen(
                modifier = Modifier.padding(innerPadding),
                connectionManager = connectionManager,
                //selectedPath = selectedPath,
                onShowSelectDirectory = { path ->
                    navController.navigate(GliderDestinations.MoveFile.createRoute(path))
                },
                onFileSelected = { selectedFilePath ->
                    navController.navigate(GliderDestinations.FileEdit.createRoute(selectedFilePath))
                })
        }

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
        "Info", Icons.Outlined.Link,
        ConnectedNavigationDestinations.Info.route
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
@Composable
private fun ConnectedTabScreenPreview() {
    GliderTheme {
        val connectionManager = ConnectionManager(WifiPeripheralScannerFake())

        BackgroundGradientFillMaxSize {
            ConnectedNavigationScreen(connectionManager = connectionManager)
        }
    }
}
//endregion
