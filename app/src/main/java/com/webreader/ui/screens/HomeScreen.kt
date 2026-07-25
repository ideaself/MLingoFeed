package com.webreader.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.webreader.data.database.Bookmark
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToReader: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val bookmarks by app.bookmarkRepository.allBookmarks.collectAsState(initial = emptyList())

    val orderedBookmarks = remember(bookmarks) { mutableStateListOf<Bookmark>() }
    LaunchedEffect(bookmarks) {
        orderedBookmarks.clear()
        orderedBookmarks.addAll(bookmarks)
    }

    var hasReordered by remember { mutableStateOf(false) }
    var bookmarkToDelete by remember { mutableStateOf<Bookmark?>(null) }

    val lazyListState = remember { androidx.compose.foundation.lazy.LazyListState() }
    val reorderableState = rememberReorderableLazyListState(
        lazyListState,
        onMove = { from, to ->
            orderedBookmarks.apply {
                add(to.index, removeAt(from.index))
            }
            hasReordered = true
        }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Web Reader") },
            actions = {
                if (hasReordered) {
                    TextButton(onClick = {
                        scope.launch {
                            app.bookmarkRepository.updatePositions(orderedBookmarks.toList())
                            hasReordered = false
                        }
                    }) {
                        Text("Save")
                    }
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Enter URL to read") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        var url = urlInput.trim()
                        if (url.isNotEmpty() && !url.startsWith("http")) {
                            url = "https://$url"
                        }
                        if (url.isNotEmpty()) {
                            onNavigateToReader(url)
                            urlInput = ""
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Go")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bookmarks",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Bookmark, contentDescription = "Add Bookmark")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(orderedBookmarks, key = { _, item -> item.id }) { index, bookmark ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                bookmarkToDelete = bookmark
                                false
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {
                            val color by animateColorAsState(
                                when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surface
                                    else -> MaterialTheme.colorScheme.errorContainer
                                },
                                label = "dismissColor"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(end = 16.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    ) {
                        ReorderableItem(reorderableState, key = bookmark.id) { isDragging ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .longPressDraggableHandle()
                                    .clickable { onNavigateToReader(bookmark.url) }
                                    .then(
                                        if (isDragging) {
                                            Modifier.background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (isDragging) 8.dp else 2.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data("https://www.google.com/s2/favicons?domain=${bookmark.url}&sz=64")
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Favicon",
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bookmark.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = bookmark.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = "Drag",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    bookmarkToDelete?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { bookmarkToDelete = null },
            title = { Text("Delete Bookmark") },
            text = { Text("Are you sure you want to delete \"${bookmark.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.bookmarkRepository.delete(bookmark)
                    }
                    bookmarkToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookmarkToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        AddBookmarkDialog(
            url = urlInput,
            title = titleInput,
            onUrlChange = { urlInput = it },
            onTitleChange = { titleInput = it },
            onConfirm = {
                var url = urlInput.trim()
                val title = titleInput.trim().ifEmpty { url }
                if (url.isNotEmpty() && !url.startsWith("http")) {
                    url = "https://$url"
                }
                if (url.isNotEmpty()) {
                    scope.launch {
                        app.bookmarkRepository.insert(
                            Bookmark(
                                title = title,
                                url = url,
                                position = orderedBookmarks.size
                            )
                        )
                    }
                }
                urlInput = ""
                titleInput = ""
                showAddDialog = false
            },
            onDismiss = {
                urlInput = ""
                titleInput = ""
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddBookmarkDialog(
    url: String,
    title: String,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Bookmark") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}