package com.mlingofeed.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mlingofeed.AppViewModelFactory
import com.mlingofeed.WebReaderApp
import androidx.compose.ui.res.stringResource
import com.mlingofeed.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssUnreadScreen(
    onBack: () -> Unit,
    onNavigateToArticle: (Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val vm: RssUnreadViewModel = viewModel(factory = remember { AppViewModelFactory(app) })
    val unreadArticles by vm.unreadArticles.collectAsStateWithLifecycle()
    var showMarkAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.unread_count, unreadArticles.size)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (unreadArticles.isNotEmpty()) {
                        IconButton(onClick = { showMarkAllConfirm = true }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.mark_all_read))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (unreadArticles.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                Spacer(modifier = Modifier.height(100.dp))
                Text(
                    stringResource(R.string.all_caught_up_nno_unread_articles),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
            ) {
                items(unreadArticles, key = { it.id }) { article ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onNavigateToArticle(article.id) },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                article.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (article.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    article.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showMarkAllConfirm) {
        AlertDialog(
            onDismissRequest = { showMarkAllConfirm = false },
            title = { Text(stringResource(R.string.mark_all_as_read)) },
            text = { Text(stringResource(R.string.unread_mark_all_confirm, unreadArticles.size)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.markAllAsRead()
                    showMarkAllConfirm = false
                }) {
                    Text(stringResource(R.string.mark_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
