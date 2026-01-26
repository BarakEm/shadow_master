package com.shadowmaster.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.shadowmaster.SharedContent
import com.shadowmaster.ui.driving.CaptureScreen
import com.shadowmaster.ui.home.HomeScreen
import com.shadowmaster.ui.library.LibraryScreen
import com.shadowmaster.ui.practice.PracticeScreen
import com.shadowmaster.ui.settings.SettingsScreen
import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Capture : Screen("capture")
    data object Settings : Screen("settings")
    data object Library : Screen("library")
    data object LibraryImport : Screen("library?importUrl={url}&importUri={uri}") {
        fun createRoute(url: String? = null, uri: String? = null): String {
            val encodedUrl = url?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
            val encodedUri = uri?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
            return "library?importUrl=$encodedUrl&importUri=$encodedUri"
        }
    }
    data object Practice : Screen("practice/{playlistId}") {
        fun createRoute(playlistId: String) = "practice/$playlistId"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route,
    sharedContent: SharedContent? = null
) {
    // Handle shared content by navigating to library with import params
    LaunchedEffect(sharedContent) {
        sharedContent?.let { content ->
            val route = when (content) {
                is SharedContent.Url -> Screen.LibraryImport.createRoute(url = content.url)
                is SharedContent.AudioFile -> Screen.LibraryImport.createRoute(uri = content.uri.toString())
            }
            navController.navigate(route) {
                popUpTo(Screen.Home.route) { inclusive = false }
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToLibrary = {
                    navController.navigate(Screen.Library.route)
                },
                onNavigateToCapture = {
                    navController.navigate(Screen.Capture.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Capture.route) {
            CaptureScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToLibrary = {
                    navController.navigate(Screen.Library.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
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

        composable(
            route = "library?importUrl={url}&importUri={uri}",
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                },
                navArgument("uri") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val importUrl = backStackEntry.arguments?.getString("url")?.takeIf { it.isNotEmpty() }
            val importUri = backStackEntry.arguments?.getString("uri")?.takeIf { it.isNotEmpty() }
            LibraryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onStartPractice = { playlistId ->
                    navController.navigate(Screen.Practice.createRoute(playlistId))
                },
                importUrl = importUrl,
                importUri = importUri
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
