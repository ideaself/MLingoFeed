package com.webreader.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.webreader.WebReaderApp
import com.webreader.data.database.Bookmark
import kotlinx.coroutines.launch

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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Web Reader") },
            actions = {
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

            val listState = rememberLazyListState()
            var draggedIndex by remember { mutableIntStateOf(-1) }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = bookmarks,
                    key = { _, bookmark -> bookmark.id }
                ) { index, bookmark ->
                    var isDragging by remember { mutableStateOf(false) }
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 8.dp else 2.dp,
                        label = "elevation"
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .clickable { onNavigateToReader(bookmark.url) }
                            .pointerInput(Unit) {
                                var cumulativeDrag = 0f
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        isDragging = true
                                        draggedIndex = index
                                        cumulativeDrag = 0f
                                    },
                                    onDragEnd = {
                                        if (draggedIndex != index && draggedIndex >= 0) {
                                            val newList = bookmarks.toMutableList()
                                            val item = newList.removeAt(draggedIndex)
                                            newList.add(index, item)
                                            scope.launch {
                                                app.bookmarkRepository.updatePositions(newList)
                                            }
                                        }
                                        isDragging = false
                                        draggedIndex = -1
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        draggedIndex = -1
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        cumulativeDrag += dragAmount.y
                                        val targetIndex = (index + (cumulativeDrag / 80).toInt())
                                            .coerceIn(0, bookmarks.size - 1)
                                        if (targetIndex != index && targetIndex != draggedIndex) {
                                            draggedIndex = targetIndex
                                        }
                                    }
                                )
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "Drag",
                                modifier = Modifier.padding(end = 8.dp)
                            )
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
                            IconButton(onClick = {
                                scope.launch {
                                    app.bookmarkRepository.delete(bookmark)
                                }
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
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
                                position = bookmarks.size
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