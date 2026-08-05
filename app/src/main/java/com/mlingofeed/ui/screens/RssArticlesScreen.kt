package com.mlingofeed.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mlingofeed.AppViewModelFactory
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.RssArticle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RssArticlesScreen(
    subscriptionId: Long,
    subscriptionTitle: String,
    onBack: () -> Unit,
    onNavigateToArticle: (Long) -> Unit,
    onNavigateToReader: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val vm: RssArticlesViewModel = viewModel(factory = remember { AppViewModelFactory(app) })

    val articles by app.rssRepository.getArticles(subscriptionId).collectAsState(initial = emptyList())
    val subscriptions by vm.subscriptions.collectAsState()
    val currentSub = subscriptions.find { it.id == subscriptionId }

    val filteredArticles = when (vm.filterMode) {
        ArticleFilterMode.ALL -> articles
        ArticleFilterMode.UNREAD -> articles.filter { !it.isRead }
        ArticleFilterMode.FAVORITES -> articles.filter { it.isFavorite }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(subscriptionTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { vm.toggleFilterMenu() }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                        DropdownMenu(
                            expanded = vm.showFilterMenu,
                            onDismissRequest = { vm.dismissFilterMenu() }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All") },
                                onClick = { vm.updateFilterMode(ArticleFilterMode.ALL) }
                            )
                            DropdownMenuItem(
                                text = { Text("Unread") },
                                onClick = { vm.updateFilterMode(ArticleFilterMode.UNREAD) }
                            )
                            DropdownMenuItem(
                                text = { Text("Favorites") },
                                onClick = { vm.updateFilterMode(ArticleFilterMode.FAVORITES) }
                            )
                        }
                    }
                    IconButton(onClick = { vm.markAllAsRead(subscriptionId) }) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Mark all read")
                    }
                    IconButton(onClick = {
                        currentSub?.let { vm.refresh(subscriptionId) }
                    }, enabled = currentSub != null) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (articles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No articles yet.\nTap refresh to fetch.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilterChip(
                            selected = vm.filterMode == ArticleFilterMode.ALL,
                            onClick = { vm.updateFilterMode(ArticleFilterMode.ALL) },
                            label = { Text("All (${articles.size})") }
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        FilterChip(
                            selected = vm.filterMode == ArticleFilterMode.UNREAD,
                            onClick = { vm.updateFilterMode(ArticleFilterMode.UNREAD) },
                            label = { Text("Unread (${articles.count { !it.isRead }})") }
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        FilterChip(
                            selected = vm.filterMode == ArticleFilterMode.FAVORITES,
                            onClick = { vm.updateFilterMode(ArticleFilterMode.FAVORITES) },
                            label = { Text("★ (${articles.count { it.isFavorite }})") }
                        )
                    }
                }

                items(filteredArticles) { article ->
                    RssArticleItem(
                        article = article,
                        onClick = { onNavigateToArticle(article.id) },
                        onLongClick = { vm.toggleReadStatus(article.id) },
                        onToggleFavorite = { vm.toggleFavorite(article.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RssArticleItem(
    article: RssArticle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (article.isRead) 0.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (article.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        article.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (article.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (article.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (article.isFavorite) "Unfavorite" else "Favorite",
                        modifier = Modifier.size(18.dp),
                        tint = if (article.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (article.isRead) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Read",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            if (article.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    article.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
