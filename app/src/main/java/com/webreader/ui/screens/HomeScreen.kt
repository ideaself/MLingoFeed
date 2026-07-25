package com.webreader.ui.screens

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webreader.WebReaderApp
import com.webreader.data.database.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToReader: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {}
) {
    sharedUrl?.let { url ->
        LaunchedEffect(url) {
            onNavigateToReader(url)
            onSharedUrlConsumed()
        }
    }
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }

    val bookmarks by app.bookmarkRepository.allBookmarks.collectAsState(initial = emptyList())
    val categories: List<String> by app.bookmarkRepository.getCategories().collectAsState(initial = emptyList())

    val orderedBookmarks = remember(bookmarks) { mutableStateListOf<Bookmark>() }
    LaunchedEffect(bookmarks) {
        orderedBookmarks.clear()
        orderedBookmarks.addAll(bookmarks)
    }

    var hasReordered by remember { mutableStateOf(false) }
    var bookmarkToDelete by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkToCategoryChange by remember { mutableStateOf<Bookmark?>(null) }

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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory.isEmpty(),
                            onClick = { selectedCategory = "" },
                            label = { Text("All") }
                        )
                    }
                    items(categories) { category: String ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = if (selectedCategory == category) "" else category },
                            label = { Text(category) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val filteredBookmarks = orderedBookmarks.filter {
                selectedCategory.isEmpty() || it.category == selectedCategory
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bookmarks (${filteredBookmarks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                androidx.compose.material3.FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Bookmark")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filteredBookmarks, key = { _, item -> item.id }) { index, bookmark ->
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
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (isDragging) 8.dp else 1.dp
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
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
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box {
                                        var showMenu by remember { mutableStateOf(false) }
                                        IconButton(onClick = { showMenu = true }) {
                                            Text(
                                                "⋮",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        androidx.compose.material3.DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text("Share") },
                                                onClick = {
                                                    showMenu = false
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_SUBJECT, bookmark.title)
                                                        putExtra(Intent.EXTRA_TEXT, bookmark.url)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                                                }
                                            )
                                            if (categories.isNotEmpty()) {
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text("Move to category") },
                                                    onClick = {
                                                        showMenu = false
                                                        bookmarkToCategoryChange = bookmark
                                                    }
                                                )
                                            }
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showMenu = false
                                                    bookmarkToDelete = bookmark
                                                }
                                            )
                                        }
                                    }
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

    bookmarkToCategoryChange?.let { bookmark ->
        var selectedCat by remember { mutableStateOf(bookmark.category) }
        var newCategoryInput by remember { mutableStateOf("") }
        var showNewCategoryField by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { bookmarkToCategoryChange = null },
            title = { Text("Change Category") },
            text = {
                Column {
                    if (categories.isNotEmpty()) {
                        categories.forEach { category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCat = category }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedCat == category, onClick = { selectedCat = category })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(category)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCat = "" }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedCat == "", onClick = { selectedCat = "" })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("None")
                        }
                    } else {
                        Text("No categories yet. Create one below.")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (showNewCategoryField) {
                        OutlinedTextField(
                            value = newCategoryInput,
                            onValueChange = { newCategoryInput = it },
                            label = { Text("New category") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        TextButton(onClick = { showNewCategoryField = true }) {
                            Text("+ New category")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cat = if (showNewCategoryField && newCategoryInput.isNotBlank()) newCategoryInput.trim() else selectedCat
                    scope.launch {
                        app.bookmarkRepository.update(bookmark.copy(category = cat))
                    }
                    bookmarkToCategoryChange = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { bookmarkToCategoryChange = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        AddBookmarkDialog(
            onSave = { url, title, category ->
                scope.launch {
                    val finalTitle = title.ifBlank { url }
                    val resolvedTitle = if (finalTitle.isBlank()) fetchPageTitle(url) else finalTitle
                    app.bookmarkRepository.insert(
                        Bookmark(
                            title = resolvedTitle,
                            url = url,
                            position = orderedBookmarks.size,
                            category = category
                        )
                    )
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

private suspend fun fetchPageTitle(pageUrl: String): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return@withContext pageUrl
        val doc = Jsoup.parse(body)
        doc.title()?.ifBlank { null } ?: pageUrl
    } catch (_: Exception) {
        pageUrl
    }
}

@Composable
fun AddBookmarkDialog(
    onSave: (url: String, title: String, category: String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Bookmark") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (auto-fetched if empty)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    var finalUrl = url.trim()
                    if (finalUrl.isNotEmpty() && !finalUrl.startsWith("http")) {
                        finalUrl = "https://$finalUrl"
                    }
                    if (finalUrl.isNotEmpty()) {
                        onSave(finalUrl, title.trim(), category.trim())
                        onDismiss()
                    }
                },
                enabled = url.isNotBlank()
            ) {
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