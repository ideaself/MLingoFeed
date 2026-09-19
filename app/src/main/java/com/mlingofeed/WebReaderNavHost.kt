package com.mlingofeed

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mlingofeed.ui.screens.HistoryScreen
import com.mlingofeed.ui.screens.HomeScreen
import com.mlingofeed.ui.screens.ReaderScreen
import com.mlingofeed.ui.screens.ReadingStatsScreen
import com.mlingofeed.ui.screens.RssArticleDetailScreen
import com.mlingofeed.ui.screens.RssArticlesScreen
import com.mlingofeed.ui.screens.RssFavoritesScreen
import com.mlingofeed.ui.screens.RssSearchScreen
import com.mlingofeed.ui.screens.RssSettingsScreen
import com.mlingofeed.ui.screens.RssSubscriptionsScreen
import com.mlingofeed.ui.screens.RssUnreadScreen
import com.mlingofeed.ui.screens.SettingsScreen
import com.mlingofeed.ui.screens.WordBookScreen
import com.mlingofeed.ui.screens.WordQuizScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    data object History : Screen("history")
    data object WordBook : Screen("wordbook")
    data object WordQuiz : Screen("wordquiz")
    data object ReadingStats : Screen("readingstats")
    data object RssSubscriptions : Screen("rss")
    data object RssArticles : Screen("rss/{subscriptionId}/{title}") {
        fun createRoute(subscriptionId: Long, title: String): String {
            // Navigation decodes path arguments itself, so encode exactly once (and with
            // Uri.encode, whose output Uri.decode can reverse for every character).
            return "rss/$subscriptionId/${Uri.encode(title)}"
        }
    }
    data object RssArticleDetail : Screen("rss/article/{articleId}") {
        fun createRoute(articleId: Long): String = "rss/article/$articleId"
    }
    data object RssSearch : Screen("rss/search")
    data object RssFavorites : Screen("rss/favorites")
    data object RssUnread : Screen("rss/unread")
    data object RssSettings : Screen("rss/settings")
    data object Reader : Screen("reader/{url}") {
        fun createRoute(url: String): String = "reader/${Uri.encode(url)}"
    }
}

@Composable
fun WebReaderNavHost(
    sharedUrl: MutableState<String?>,
    pendingDestination: MutableState<String?>
) {
    val navController = rememberNavController()

    val pendingUrl = sharedUrl.value
    LaunchedEffect(pendingUrl) {
        if (pendingUrl != null) {
            navController.navigate(Screen.Reader.createRoute(pendingUrl)) {
                launchSingleTop = true
            }
            sharedUrl.value = null
        }
    }

    val destination = pendingDestination.value
    LaunchedEffect(destination) {
        if (destination == MainActivity.DESTINATION_RSS) {
            navController.navigate(Screen.RssSubscriptions.route) {
                launchSingleTop = true
            }
            pendingDestination.value = null
        }
    }

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
                onNavigateToWordBook = {
                    navController.navigate(Screen.WordBook.route)
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToReadingStats = {
                    navController.navigate(Screen.ReadingStats.route)
                }
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
        composable(Screen.WordBook.route) {
            WordBookScreen(
                onBack = { navController.popBackStack() },
                onNavigateToQuiz = {
                    navController.navigate(Screen.WordQuiz.route)
                }
            )
        }
        composable(Screen.WordQuiz.route) {
            WordQuizScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ReadingStats.route) {
            ReadingStatsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.RssSubscriptions.route) {
            RssSubscriptionsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToArticles = { id, title ->
                    navController.navigate(Screen.RssArticles.createRoute(id, title))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.RssSearch.route)
                },
                onNavigateToFavorites = {
                    navController.navigate(Screen.RssFavorites.route)
                },
                onNavigateToUnread = {
                    navController.navigate(Screen.RssUnread.route)
                },
                onNavigateToRssSettings = {
                    navController.navigate(Screen.RssSettings.route)
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
            val title = backStackEntry.arguments?.getString("title") ?: ""
            RssArticlesScreen(
                subscriptionId = subscriptionId,
                subscriptionTitle = title,
                onBack = { navController.popBackStack() },
                onNavigateToArticle = { articleId ->
                    navController.navigate(Screen.RssArticleDetail.createRoute(articleId))
                },
                onNavigateToReader = { url ->
                    navController.navigate(Screen.Reader.createRoute(url))
                }
            )
        }
        composable(
            route = Screen.RssArticleDetail.route,
            arguments = listOf(navArgument("articleId") { type = NavType.LongType })
        ) { backStackEntry ->
            val articleId = backStackEntry.arguments?.getLong("articleId") ?: 0L
            RssArticleDetailScreen(
                articleId = articleId,
                onBack = { navController.popBackStack() },
                onOpenExternal = { url ->
                    navController.navigate(Screen.Reader.createRoute(url))
                }
            )
        }
        composable(Screen.RssSearch.route) {
            RssSearchScreen(
                onBack = { navController.popBackStack() },
                onNavigateToArticle = { articleId ->
                    navController.navigate(Screen.RssArticleDetail.createRoute(articleId))
                }
            )
        }
        composable(Screen.RssFavorites.route) {
            RssFavoritesScreen(
                onBack = { navController.popBackStack() },
                onNavigateToArticle = { articleId ->
                    navController.navigate(Screen.RssArticleDetail.createRoute(articleId))
                }
            )
        }
        composable(Screen.RssUnread.route) {
            RssUnreadScreen(
                onBack = { navController.popBackStack() },
                onNavigateToArticle = { articleId ->
                    navController.navigate(Screen.RssArticleDetail.createRoute(articleId))
                }
            )
        }
        composable(Screen.RssSettings.route) {
            RssSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.Reader.route,
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            ReaderScreen(
                initialUrl = url,
                onBack = { navController.popBackStack() },
                onGoHome = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}
