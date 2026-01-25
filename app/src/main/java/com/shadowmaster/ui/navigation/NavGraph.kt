package com.shadowmaster.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.shadowmaster.ui.driving.DrivingScreen
import com.shadowmaster.ui.home.HomeScreen
import com.shadowmaster.ui.library.LibraryScreen
import com.shadowmaster.ui.practice.PracticeScreen
import com.shadowmaster.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Driving : Screen("driving")
    data object Settings : Screen("settings")
    data object Library : Screen("library")
    data object Practice : Screen("practice/{playlistId}") {
        fun createRoute(playlistId: String) = "practice/$playlistId"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToLibrary = {
                    navController.navigate(Screen.Library.route)
                },
                onNavigateToLiveShadow = {
                    navController.navigate(Screen.Driving.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Driving.route) {
            DrivingScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onStartPractice = { playlistId ->
                    navController.navigate(Screen.Practice.createRoute(playlistId))
                }
            )
        }

        composable(
            route = Screen.Practice.route,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.StringType }
            )
        ) {
            PracticeScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
