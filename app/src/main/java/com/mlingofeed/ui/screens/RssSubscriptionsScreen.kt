package com.mlingofeed.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mlingofeed.AppViewModelFactory
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.RssFolder
import com.mlingofeed.data.database.RssSubscription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssSubscriptionsScreen(
    onBack: () -> Unit,
    onNavigateToArticles: (Long, String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToUnread: () -> Unit,
    onNavigateToRssSettings: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val vm: RssSubscriptionsViewModel = viewModel(factory = remember { AppViewModelFactory(app) })

    val subscriptions by vm.subscriptions.collectAsState()
    val folders by vm.folders.collectAsState()
    val totalUnread by vm.totalUnread.collectAsState()

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
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { vm.openAddDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (vm.isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (subscriptions.isEmpty() && folders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.RssFeed, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading RSS feeds...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickFilterChip(
                            label = "Unread ($totalUnread)",
                            icon = Icons.Default.RssFeed,
                            onClick = onNavigateToUnread,
                            modifier = Modifier.weight(1f)
                        )
                        QuickFilterChip(
                            label = "Favorites",
                            icon = Icons.Default.Bookmark,
                            onClick = onNavigateToFavorites,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                folders.forEach { folder ->
                    val folderSubs = subscriptions.filter { it.folderId == folder.id }
                    val isExpanded = folder.id in vm.expandedFolders
                    val folderUnread = 0

                    item {
                        FolderHeader(
                            folder = folder,
                            subCount = folderSubs.size,
                            unreadCount = folderUnread,
                            isExpanded = isExpanded,
                            onToggle = { vm.toggleFolder(folder.id) }
                        )
                    }

                    if (isExpanded) {
                        items(folderSubs) { subscription ->
                            val unreadCount by app.rssRepository.getUnreadCount(subscription.id).collectAsState(initial = 0)
                            RssSubscriptionItem(
                                subscription = subscription,
                                unreadCount = unreadCount,
                                onClick = { onNavigateToArticles(subscription.id, subscription.title) },
                                onDelete = { vm.requestDelete(subscription) },
                                onEdit = { vm.requestEdit(subscription) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                val ungroupedSubs = subscriptions.filter { it.folderId == null }
                if (ungroupedSubs.isNotEmpty()) {
                    if (folders.isNotEmpty()) {
                        item {
                            Text(
                                "Ungrouped",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                    }
                    items(ungroupedSubs) { subscription ->
                        val unreadCount by app.rssRepository.getUnreadCount(subscription.id).collectAsState(initial = 0)
                        RssSubscriptionItem(
                            subscription = subscription,
                            unreadCount = unreadCount,
                            onClick = { onNavigateToArticles(subscription.id, subscription.title) },
                            onDelete = { vm.requestDelete(subscription) },
                            onEdit = { vm.requestEdit(subscription) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (vm.showAddDialog) {
        var rssUrl by remember { mutableStateOf("") }
        var rssTitle by remember { mutableStateOf("") }
        var selectedFolderId by remember { mutableStateOf<Long?>(null) }

        AlertDialog(
            onDismissRequest = { vm.closeAddDialog() },
            title = { Text("Add RSS Feed") },
            text = {
                Column {
                    OutlinedTextField(
                        value = rssTitle,
                        onValueChange = { rssTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rssUrl,
                        onValueChange = { rssUrl = it },
                        label = { Text("RSS URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (folders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Folder", style = MaterialTheme.typography.labelSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedFolderId = null },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedFolderId == null,
                                onClick = { selectedFolderId = null }
                            )
                            Text("None")
                        }
                        folders.forEach { folder ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { selectedFolderId = folder.id },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = selectedFolderId == folder.id,
                                    onClick = { selectedFolderId = folder.id }
                                )
                                Text(folder.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (rssUrl.isNotBlank()) {
                        vm.addSubscription(rssTitle, rssUrl, selectedFolderId)
                        vm.closeAddDialog()
                    }
                }, enabled = rssUrl.isNotBlank()) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { vm.closeAddDialog() }) { Text("Cancel") }
            }
        )
    }

    vm.showDeleteDialog?.let { sub ->
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            title = { Text("Delete Feed") },
            text = { Text("Delete \"${sub.title}\"?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteSubscription(sub.id) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelDelete() }) { Text("Cancel") }
            }
        )
    }

    vm.editingSub?.let { sub ->
        var editTitle by remember { mutableStateOf(sub.title) }
        var editUrl by remember { mutableStateOf(sub.url) }
        var editFolderId by remember { mutableStateOf(sub.folderId) }

        AlertDialog(
            onDismissRequest = { vm.cancelEdit() },
            title = { Text("Edit Feed") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text("RSS URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (folders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Folder", style = MaterialTheme.typography.labelSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { editFolderId = null },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = editFolderId == null,
                                onClick = { editFolderId = null }
                            )
                            Text("None")
                        }
                        folders.forEach { folder ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { editFolderId = folder.id },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = editFolderId == folder.id,
                                    onClick = { editFolderId = folder.id }
                                )
                                Text(folder.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editTitle.isNotBlank() && editUrl.isNotBlank()) {
                        vm.updateSubscription(sub, editTitle, editUrl, editFolderId)
                    }
                }, enabled = editTitle.isNotBlank() && editUrl.isNotBlank()) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelEdit() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FolderHeader(
    folder: RssFolder,
    subCount: Int,
    unreadCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onToggle),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                folder.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$subCount feeds",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickFilterChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
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
