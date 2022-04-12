package com.adafruit.glider.ui.connected

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adafruit.glider.R
import com.adafruit.glider.ui.BackgroundGradientDefault
import com.adafruit.glider.ui.fileexplorer.FileExplorerScreen
import com.adafruit.glider.ui.theme.AccentMain
import com.adafruit.glider.ui.theme.GliderTheme
import com.adafruit.glider.ui.theme.TopBarBackground

/**
 * Created by Antonio García (antonio@openroad.es)
 */

@Composable
fun ConnectedTabScreen(
) {
    val navController = rememberNavController()
    //val scaffoldState = rememberScaffoldState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connected") }, backgroundColor = TopBarBackground
            )
        },
        bottomBar = { BottomNavigation(navController = navController) },
        //scaffoldState = scaffoldState,
    ) { innerPadding ->
        ConnectedTabScreenBody(navController = navController, innerPadding = innerPadding)
    }
}

// region Body
@Composable
private fun ConnectedTabScreenBody(
    navController: NavHostController,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    //
    NavHost(
        navController = navController,
        startDestination = ConnectedTabScreenRoute.FileExplorer.name,
        modifier = modifier
    ) {
        composable(ConnectedTabScreenRoute.FileExplorer.name) {
            FileExplorerScreen(innerPadding) { selectedFilePath ->
                // on file selected
                // TODO
            }
        }
    }
}
// endregion

// region BottomNavigation
sealed class BottomNavItem(var title: String, var icon: Int, var screenRoute: String) {
    object FileExplorer : BottomNavItem("Explorer", R.drawable.ic_baseline_folder_24, "home")
}

@Composable
fun BottomNavigation(navController: NavController) {

    val items = listOf(
        BottomNavItem.FileExplorer,
    )

    BottomNavigation(
        //modifier = Modifier.shadow(elevation = 0.dp),
        backgroundColor = Color.Transparent,//colorResource(id = R.color.teal_200),
        contentColor = Color.Red
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { item ->
            BottomNavigationItem(
                icon = { Icon(painterResource(id = item.icon), contentDescription = item.title) },
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
