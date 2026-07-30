package com.webreader.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webreader.WebReaderApp
import com.webreader.data.database.RssSubscription
import com.webreader.data.repository.RssParser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssSubscriptionsScreen(
    onBack: () -> Unit,
    onNavigateToArticles: (Long, String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val scope = rememberCoroutineScope()

    val subscriptions by app.rssRepository.allSubscriptions.collectAsState(initial = emptyList())
    var isRefreshing by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<RssSubscription?>(null) }
    var editingSub by remember { mutableStateOf<RssSubscription?>(null) }
    LaunchedEffect(Unit) {
        app.rssRepository.cleanupDuplicates()
        app.rssRepository.initDefaultSubscriptions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RSS Feeds") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            app.rssRepository.deleteAllSubscriptions()
                        }
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Reset")
                    }
                    IconButton(onClick = {
                        if (!isRefreshing) {
                            isRefreshing = true
                            scope.launch {
                                subscriptions.forEach { sub ->
                                    val articles = RssParser.parse(sub.id, sub.url)
                                    app.rssRepository.insertArticles(articles)
                                }
                                app.rssRepository.cleanupOldArticles()
                                isRefreshing = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (subscriptions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(100.dp))
                Icon(Icons.Default.RssFeed, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading RSS feeds...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
            ) {
                items(subscriptions) { subscription ->
                    val unreadCount by app.rssRepository.getUnreadCount(subscription.id).collectAsState(initial = 0)
                    RssSubscriptionItem(
                        subscription = subscription,
                        unreadCount = unreadCount,
                        onClick = { onNavigateToArticles(subscription.id, subscription.title) },
                        onDelete = { showDeleteDialog = subscription },
                        onEdit = { editingSub = subscription }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddDialog) {
        var rssUrl by remember { mutableStateOf("") }
        var rssTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add RSS Feed") },
            text = {
                Column {
                    OutlinedTextField(value = rssTitle, onValueChange = { rssTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = rssUrl, onValueChange = { rssUrl = it }, label = { Text("RSS URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (rssUrl.isNotBlank()) {
                        scope.launch {
                            val title = rssTitle.ifBlank { rssUrl }
                            val id = app.rssRepository.addSubscription(title, rssUrl)
                            val articles = RssParser.parse(id, rssUrl)
                            app.rssRepository.insertArticles(articles)
                        }
                        showAddDialog = false
                    }
                }, enabled = rssUrl.isNotBlank()) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    showDeleteDialog?.let { sub ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Feed") },
            text = { Text("Delete \"${sub.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.rssRepository.deleteSubscription(sub.id) }
                    showDeleteDialog = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }

    editingSub?.let { sub ->
        var editTitle by remember { mutableStateOf(sub.title) }
        var editUrl by remember { mutableStateOf(sub.url) }
        AlertDialog(
            onDismissRequest = { editingSub = null },
            title = { Text("Edit Feed") },
            text = {
                Column {
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = editUrl, onValueChange = { editUrl = it }, label = { Text("RSS URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editTitle.isNotBlank() && editUrl.isNotBlank()) {
                        scope.launch {
                            app.rssRepository.updateSubscription(sub.id, editTitle.trim(), editUrl.trim(), sub.category)
                            val articles = RssParser.parse(sub.id, editUrl.trim())
                            app.rssRepository.insertArticles(articles)
                        }
                        editingSub = null
                    }
                }, enabled = editTitle.isNotBlank() && editUrl.isNotBlank()) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingSub = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RssSubscriptionItem(
    subscription: RssSubscription,
    unreadCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.RssFeed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(subscription.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subscription.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (unreadCount > 0) {
                Text("$unreadCount new", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
