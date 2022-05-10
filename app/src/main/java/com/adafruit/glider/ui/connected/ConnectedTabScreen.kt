package com.adafruit.glider.ui.connected

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.ScreenRoute
import com.adafruit.glider.ui.fileexplorer.FileExplorerScreen
import com.adafruit.glider.ui.theme.AccentMain
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.ui.theme.TopBarBackground

/**
 * Created by Antonio García (antonio@openroad.es)
 */

@Composable
fun ConnectedTabScreen(
    navController: NavHostController = rememberNavController()
) {
    val navControllerBottomBar = rememberNavController()
    //val scaffoldState = rememberScaffoldState()

    val backStackEntry = navControllerBottomBar.currentBackStackEntryAsState()
    val currentScreen = ConnectedTabScreenRoute.fromRoute(backStackEntry.value?.destination?.route)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentScreen.title()) }, backgroundColor = TopBarBackground
            )
        },
        bottomBar = { BottomNavigation(navController = navControllerBottomBar) },
        //scaffoldState = scaffoldState,
    ) { innerPadding ->
        ConnectedTabScreenBody(navController = navController, navControllerBottomBar = navControllerBottomBar, innerPadding = innerPadding)
    }
}

// region Body
@Composable
private fun ConnectedTabScreenBody(
    navController: NavController,
    navControllerBottomBar: NavHostController,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    //
    NavHost(
        navController = navControllerBottomBar,
        startDestination = ConnectedTabScreenRoute.FileExplorer.route,
        modifier = modifier
    ) {
        composable(ConnectedTabScreenRoute.Info.route) {
            InfoScreen(innerPadding)
        }

        composable(ConnectedTabScreenRoute.FileExplorer.route) {
            FileExplorerScreen(innerPadding) { selectedFilePath ->
                // on file selected
                navController.navigate(ScreenRoute.FileEdit.createRoute(selectedFilePath))
            }
        }
    }
}
// endregion

// region BottomNavigation
sealed class BottomNavItem(var title: String, var imageVector: ImageVector, var screenRoute: String) {
    object Info : BottomNavItem("Info", Icons.Outlined.Link, ConnectedTabScreenRoute.Info.route)
    object FileExplorer : BottomNavItem("Explorer",Icons.Outlined.FolderOpen, ConnectedTabScreenRoute.FileExplorer.route)
}

@Composable
fun BottomNavigation(navController: NavController) {

    val items = listOf(
        BottomNavItem.Info,
        BottomNavItem.FileExplorer,
    )

    BottomNavigation(
        backgroundColor = Color.Transparent,//colorResource(id = R.color.teal_200),
        contentColor = Color.Red,
        elevation = 0.dp
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { item ->
            BottomNavigationItem(
                icon = {
                    Icon(
                        imageVector = item.imageVector,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(text = item.title)
                },
                selectedContentColor = AccentMain,
                unselectedContentColor = Color.White.copy(0.4f),
                alwaysShowLabel = true,
                selected = currentRoute == item.screenRoute,
                onClick = {
                    navController.navigate(item.screenRoute) {
/*
                        navController.graph.startDestinationRoute?.let { screen_route ->
                            popUpTo(screen_route) {
                                saveState = true
                            }
                        }
                        launchSingleTop = true
                        restoreState = true*/
                    }
                }
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
        BackgroundGradientDefault {
            ConnectedTabScreen()
        }

    }
}


//endregion
