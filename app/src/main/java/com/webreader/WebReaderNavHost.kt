package com.webreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.webreader.ui.screens.HistoryScreen
import com.webreader.ui.screens.HomeScreen
import com.webreader.ui.screens.RssArticlesScreen
import com.webreader.ui.screens.ReaderScreen
import com.webreader.ui.screens.RssSubscriptionsScreen
import com.webreader.ui.screens.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    data object History : Screen("history")
    data object RssSubscriptions : Screen("rss")
    data object RssArticles : Screen("rss/{subscriptionId}/{title}") {
        fun createRoute(subscriptionId: Long, title: String): String {
            val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
            return "rss/$subscriptionId/$encodedTitle"
        }
    }
    data object Reader : Screen("reader/{url}") {
        fun createRoute(url: String): String {
            val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
            return "reader/$encoded"
        }
    }
}

@Composable
fun WebReaderNavHost(initialUrl: String? = null) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    var sharedUrlState by androidx.compose.runtime.mutableStateOf(initialUrl)

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToReader = { url ->
                    navController.navigate(Screen.Reader.createRoute(url))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToRss = {
                    navController.navigate(Screen.RssSubscriptions.route)
                },
                sharedUrl = sharedUrlState,
                onSharedUrlConsumed = { sharedUrlState = null }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onNavigateToReader = { url ->
                    navController.navigate(Screen.Reader.createRoute(url))
                }
            )
        }
        composable(Screen.RssSubscriptions.route) {
            RssSubscriptionsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToArticles = { id, title ->
                    navController.navigate(Screen.RssArticles.createRoute(id, title))
                }
            )
        }
        composable(
            route = Screen.RssArticles.route,
            arguments = listOf(
                navArgument("subscriptionId") { type = NavType.LongType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val subscriptionId = backStackEntry.arguments?.getLong("subscriptionId") ?: 0L
            val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", StandardCharsets.UTF_8.toString())
            RssArticlesScreen(
                subscriptionId = subscriptionId,
                subscriptionTitle = title,
                onBack = { navController.popBackStack() },
                onNavigateToReader = { url ->
                    navController.navigate(Screen.Reader.createRoute(url))
                }
            )
        }
        composable(
            route = Screen.Reader.route,
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            val url = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
            ReaderScreen(
                initialUrl = url,
                onBack = { navController.popBackStack() },
                onOpenUrl = { newUrl ->
                    navController.navigate(Screen.Reader.createRoute(newUrl))
                }
            )
        }
    }
}