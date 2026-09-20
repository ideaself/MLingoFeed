package com.mlingofeed.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AlertDialog

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mlingofeed.AppViewModelFactory
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.Bookmark
import kotlinx.coroutines.flow.distinctUntilChanged
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.res.stringResource
import com.mlingofeed.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToReader: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    onNavigateToRss: () -> Unit = {},
    onNavigateToWordBook: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val vm: HomeViewModel = viewModel(factory = remember { AppViewModelFactory(app) })

    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val categories: List<String> by vm.categories.collectAsStateWithLifecycle()
    LaunchedEffect(bookmarks) {
        vm.syncOrdered(bookmarks)
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState,
        onMove = { from, to ->
            val fromId = from.key as? Long
            val toId = to.key as? Long
            if (fromId != null && toId != null) {
                vm.moveBookmark(fromId, toId)
            }
        }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("MLingoFeed") },
            actions = {
                if (vm.hasReordered) {
                    TextButton(onClick = { vm.saveOrder() }) {
                        Text(stringResource(R.string.save))
                    }
                }
                IconButton(onClick = { vm.openAddDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_bookmark))
                }
                IconButton(onClick = onNavigateToRss) {
                    Icon(Icons.Default.RssFeed, contentDescription = stringResource(R.string.rss))
                }
                IconButton(onClick = onNavigateToWordBook) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = stringResource(R.string.word_book))
                }
                IconButton(onClick = onNavigateToHistory) {
                    Icon(Icons.Default.History, contentDescription = stringResource(R.string.history))
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
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
                            selected = vm.selectedCategory.isEmpty(),
                            onClick = { vm.clearCategory() },
                            label = { Text(stringResource(R.string.all)) }
                        )
                    }
                    items(categories, key = { it }) { category: String ->
                        FilterChip(
                            selected = vm.selectedCategory == category,
                            onClick = { vm.selectCategory(category) },
                            label = { Text(category) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val filteredBookmarks by remember {
                derivedStateOf {
                    vm.orderedBookmarks.filter {
                        vm.selectedCategory.isEmpty() || it.category == vm.selectedCategory
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filteredBookmarks, key = { _, item -> item.id }) { _, bookmark ->
                    var dismissThresholdMet by remember { mutableStateOf(false) }
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart && dismissThresholdMet) {
                                vm.requestDelete(bookmark)
                            }
                            false
                        }
                    )
                    LaunchedEffect(dismissState) {
                        snapshotFlow { dismissState.progress > 0.3f }
                            .distinctUntilChanged()
                            .collect { dismissThresholdMet = it }
                    }

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
                                    contentDescription = stringResource(R.string.delete),
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
                                    val faviconRequest = remember(bookmark.url) {
                                        val host = Uri.parse(bookmark.url).host ?: bookmark.url
                                        ImageRequest.Builder(context)
                                            .data("https://www.google.com/s2/favicons?domain=$host&sz=64")
                                            .crossfade(true)
                                            .build()
                                    }
                                    AsyncImage(
                                        model = faviconRequest,
                                        contentDescription = stringResource(R.string.favicon),
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
                                                text = { Text(stringResource(R.string.share)) },
                                                onClick = {
                                                    showMenu = false
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_SUBJECT, bookmark.title)
                                                        putExtra(Intent.EXTRA_TEXT, bookmark.url)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_via)))
                                                }
                                            )
                                            if (categories.isNotEmpty()) {
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.move_to_category)) },
                                                    onClick = {
                                                        showMenu = false
                                                        vm.requestCategoryChange(bookmark)
                                                    }
                                                )
                                            }
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showMenu = false
                                                    vm.requestDelete(bookmark)
                                                }
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = stringResource(R.string.drag),
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

    vm.bookmarkToDelete?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            title = { Text(stringResource(R.string.delete_bookmark)) },
            text = { Text(stringResource(R.string.bookmark_delete_confirm, bookmark.title)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteBookmark(bookmark) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelDelete() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    vm.bookmarkToCategoryChange?.let { bookmark ->
        var selectedCat by remember { mutableStateOf(bookmark.category) }
        var newCategoryInput by remember { mutableStateOf("") }
        var showNewCategoryField by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { vm.cancelCategoryChange() },
            title = { Text(stringResource(R.string.change_category)) },
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
                            Text(stringResource(R.string.none))
                        }
                    } else {
                        Text(stringResource(R.string.no_categories_yet_create_one_below))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (showNewCategoryField) {
                        OutlinedTextField(
                            value = newCategoryInput,
                            onValueChange = { newCategoryInput = it },
                            label = { Text(stringResource(R.string.new_category)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        TextButton(onClick = { showNewCategoryField = true }) {
                            Text(stringResource(R.string.new_category_2))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cat = if (showNewCategoryField && newCategoryInput.isNotBlank()) newCategoryInput.trim() else selectedCat
                    vm.changeCategory(bookmark, cat)
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelCategoryChange() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (vm.showAddDialog) {
        AddBookmarkDialog(
            onSave = { url, title, category ->
                vm.addBookmark(url, title, category)
            },
            onDismiss = { vm.dismissAddDialog() }
        )
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
        title = { Text(stringResource(R.string.add_bookmark)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title_auto_fetched_if_empty)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.category_optional)) },
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
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}