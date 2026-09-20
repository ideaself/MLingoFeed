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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.res.stringResource
import com.mlingofeed.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssSubscriptionsScreen(
    onBack: () -> Unit,
    onNavigateToArticles: (Long, String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToUnread: () -> Unit,
    onNavigateToSaved: () -> Unit,
    onNavigateToRssSettings: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val vm: RssSubscriptionsViewModel = viewModel(factory = remember { AppViewModelFactory(app) })

    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val totalUnread by vm.totalUnread.collectAsStateWithLifecycle()
    val unreadCounts by vm.unreadCounts.collectAsStateWithLifecycle()
    val subsByFolder = remember(subscriptions) { subscriptions.groupBy { it.folderId } }
    var markAllReadFolder by remember { mutableStateOf<RssFolder?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rss_feeds)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                    IconButton(onClick = { vm.openAddDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = vm.isRefreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (subscriptions.isEmpty() && folders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.RssFeed, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.no_rss_feeds_yet), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.tap_to_add_your_first_feed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickFilterChip(
                                label = stringResource(R.string.unread_count, totalUnread),
                                icon = Icons.Default.RssFeed,
                                onClick = onNavigateToUnread,
                                modifier = Modifier.weight(1f)
                            )
                            QuickFilterChip(
                                label = stringResource(R.string.favorites),
                                icon = Icons.Default.Bookmark,
                                onClick = onNavigateToFavorites,
                                modifier = Modifier.weight(1f)
                            )
                            QuickFilterChip(
                                label = stringResource(R.string.saved),
                                icon = Icons.Default.Schedule,
                                onClick = onNavigateToSaved,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    folders.forEach { folder ->
                        val folderSubs = subsByFolder[folder.id].orEmpty()
                        val isExpanded = folder.id in vm.expandedFolders
                        val folderUnread = folderSubs.sumOf { unreadCounts[it.id] ?: 0 }

                        item {
                            FolderHeader(
                                folder = folder,
                                subCount = folderSubs.size,
                                unreadCount = folderUnread,
                                isExpanded = isExpanded,
                                onToggle = { vm.toggleFolder(folder.id) },
                                onMarkAllRead = { markAllReadFolder = folder }
                            )
                        }

                        if (isExpanded) {
                            items(folderSubs, key = { it.id }) { subscription ->
                                RssSubscriptionItem(
                                    subscription = subscription,
                                    unreadCount = unreadCounts[subscription.id] ?: 0,
                                    onClick = { onNavigateToArticles(subscription.id, subscription.title) },
                                    onDelete = { vm.requestDelete(subscription) },
                                    onEdit = { vm.requestEdit(subscription) }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }

                    val ungroupedSubs = subsByFolder[null].orEmpty()
                    if (ungroupedSubs.isNotEmpty()) {
                        if (folders.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.ungrouped),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                        }
                        items(ungroupedSubs, key = { it.id }) { subscription ->
                            RssSubscriptionItem(
                                subscription = subscription,
                                unreadCount = unreadCounts[subscription.id] ?: 0,
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
    }

    if (vm.showAddDialog) {
        var rssUrl by remember { mutableStateOf("") }
        var rssTitle by remember { mutableStateOf("") }
        var selectedFolderId by remember { mutableStateOf<Long?>(null) }

        AlertDialog(
            onDismissRequest = { vm.closeAddDialog() },
            title = { Text(stringResource(R.string.add_rss_feed)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = rssTitle,
                        onValueChange = { rssTitle = it },
                        label = { Text(stringResource(R.string.title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rssUrl,
                        onValueChange = { rssUrl = it },
                        label = { Text(stringResource(R.string.rss_or_site_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (folders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.folder), style = MaterialTheme.typography.labelSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedFolderId = null },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedFolderId == null,
                                onClick = { selectedFolderId = null }
                            )
                            Text(stringResource(R.string.none))
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
                }, enabled = rssUrl.isNotBlank()) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.closeAddDialog() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    markAllReadFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { markAllReadFolder = null },
            title = { Text(stringResource(R.string.mark_folder_as_read)) },
            text = { Text(stringResource(R.string.folder_mark_read_confirm, folder.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.markFolderAsRead(folder.id)
                    markAllReadFolder = null
                }) {
                    Text(stringResource(R.string.mark_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { markAllReadFolder = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    vm.showDeleteDialog?.let { sub ->
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            title = { Text(stringResource(R.string.delete_feed)) },
            text = { Text(stringResource(R.string.feed_delete_confirm, sub.title)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteSubscription(sub.id) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelDelete() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    vm.editingSub?.let { sub ->
        var editTitle by remember { mutableStateOf(sub.title) }
        var editUrl by remember { mutableStateOf(sub.url) }
        var editFolderId by remember { mutableStateOf(sub.folderId) }

        AlertDialog(
            onDismissRequest = { vm.cancelEdit() },
            title = { Text(stringResource(R.string.edit_feed)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text(stringResource(R.string.title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text(stringResource(R.string.rss_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (folders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.folder), style = MaterialTheme.typography.labelSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { editFolderId = null },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = editFolderId == null,
                                onClick = { editFolderId = null }
                            )
                            Text(stringResource(R.string.none))
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
                }, enabled = editTitle.isNotBlank() && editUrl.isNotBlank()) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelEdit() }) { Text(stringResource(R.string.cancel)) }
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
    onToggle: () -> Unit,
    onMarkAllRead: () -> Unit
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
                stringResource(R.string.feeds_count, subCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subCount > 0) {
                IconButton(onClick = onMarkAllRead, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.DoneAll,
                        contentDescription = stringResource(R.string.mark_folder_as_read_2),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
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
                Text(stringResource(R.string.new_count, unreadCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
