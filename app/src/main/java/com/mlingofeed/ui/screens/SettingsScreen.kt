package com.mlingofeed.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mlingofeed.AppViewModelFactory
import com.mlingofeed.BuildConfig
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.export.ExportManager
import com.mlingofeed.data.settings.DictionaryConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}, onNavigateToReadingStats: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val vm: SettingsViewModel = viewModel(factory = remember { AppViewModelFactory(app) })
    val clipboardManager = LocalClipboardManager.current

    val dictionaries by vm.dictionaries.collectAsState()
    val fontSize by vm.fontSize.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val readingTimeSeconds by vm.readingTimeSeconds.collectAsState()
    val readingSessions by vm.readingSessions.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.exportData(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importData(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.saveSettings() }
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(
                title = "Theme",
                imageVector = Icons.Default.FormatPaint,
                expanded = vm.expandedSection == "theme",
                onToggle = { vm.toggleSection("theme") }
            ) {
                Column(modifier = Modifier.selectableGroup()) {
                    ThemeRadioOption("Follow System", themeMode == "system") {
                        vm.setThemeMode("system")
                    }
                    ThemeRadioOption("Light", themeMode == "light") {
                        vm.setThemeMode("light")
                    }
                    ThemeRadioOption("Dark", themeMode == "dark") {
                        vm.setThemeMode("dark")
                    }
                    ThemeRadioOption("Eye Care", themeMode == "eyecare") {
                        vm.setThemeMode("eyecare")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(
                title = "Reading Time",
                imageVector = Icons.Default.Schedule,
                expanded = vm.expandedSection == "reading_time",
                onToggle = { vm.toggleSection("reading_time") },
                summary = formatReadingTime(readingTimeSeconds)
            ) {
                val totalSessions = readingSessions.size
                val avgDuration = if (totalSessions > 0) readingTimeSeconds / totalSessions else 0L
                val longestSession = readingSessions.maxOfOrNull { it.second } ?: 0L
                val todaySeconds = readingSessions.filter {
                    isToday(it.first)
                }.sumOf { it.second }

                StatRow("Total", formatReadingTime(readingTimeSeconds))
                StatRow("Sessions", "$totalSessions")
                StatRow("Today", formatReadingTime(todaySeconds))
                StatRow("Average", formatReadingTime(avgDuration))
                StatRow("Longest", formatReadingTime(longestSession))

                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    TextButton(onClick = onNavigateToReadingStats) {
                        Text("View Details")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.resetReadingTime() }) {
                        Text("Reset")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(
                title = "Font Size",
                imageVector = Icons.Default.FormatSize,
                expanded = vm.expandedSection == "font_size",
                onToggle = { vm.toggleSection("font_size") },
                summary = "${fontSize}%"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("A", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(24.dp))
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = { vm.setFontSize(it.toInt()) },
                        valueRange = 60f..180f,
                        steps = 5,
                        modifier = Modifier.weight(1f)
                    )
                    Text("A", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(32.dp))
                }
                Text(
                    text = "${fontSize}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(
                title = "Dictionaries",
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                expanded = vm.expandedSection == "dictionaries",
                onToggle = { vm.toggleSection("dictionaries") },
                summary = "${dictionaries.count { it.isEnabled }} / ${dictionaries.size} enabled"
            ) {
                dictionaries.forEachIndexed { index, dict ->
                    DictionaryItem(
                        dictionary = dict,
                        onToggle = { enabled -> vm.toggleDictionary(index, enabled) },
                        onEdit = { vm.requestEditDictionary(dict) },
                        onDelete = { vm.deleteDictionary(index) },
                        onMoveUp = { vm.moveDictionary(index, -1) },
                        onMoveDown = { vm.moveDictionary(index, 1) },
                        canMoveUp = index > 0,
                        canMoveDown = index < dictionaries.size - 1
                    )
                    if (index < dictionaries.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { vm.openPresetDicts() }) {
                        Icon(Icons.Default.Star, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("From Preset")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { vm.openAddDict() }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Dictionary")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(
                title = "AI / Translation",
                imageVector = Icons.Default.Language,
                expanded = vm.expandedSection == "ai",
                onToggle = { vm.toggleSection("ai") }
            ) {
                OutlinedTextField(
                    value = vm.aiUrlInput,
                    onValueChange = { vm.onAiUrlInputChange(it) },
                    label = { Text("AI API URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("DeepSeek: https://api.deepseek.com/chat/completions") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.aiKeyInput,
                    onValueChange = { vm.onAiKeyInputChange(it) },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(vm.aiKeyInput)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.aiModelInput,
                    onValueChange = { vm.onAiModelInputChange(it) },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = vm.showModelDropdown && vm.modelList.isNotEmpty(),
                    supportingText = { Text("Click ↻ to auto-fetch models") },
                    trailingIcon = {
                        if (vm.isLoadingModels) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { vm.fetchModels() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Fetch Models")
                            }
                        }
                    }
                )
                if (vm.showModelDropdown && vm.modelList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            vm.modelList.take(10).forEach { model ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.selectModel(model) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.targetLangInput,
                    onValueChange = { vm.onTargetLangInputChange(it) },
                    label = { Text("Target Language") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("e.g., Chinese, Japanese, Spanish") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(
                title = "Data Management",
                imageVector = Icons.Default.Storage,
                expanded = vm.expandedSection == "data",
                onToggle = { vm.toggleSection("data") }
            ) {
                Text(
                    text = "Export bookmarks and settings as a backup file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { exportLauncher.launch("web-reader-backup.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MLingoFeed", style = MaterialTheme.typography.titleSmall)
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "A web reading app with dictionary lookup, translation, and AI chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (vm.editingDict != null) {
        EditDictionaryDialog(
            dictionary = vm.editingDict!!,
            onConfirm = { updated -> vm.saveDictionary(updated) },
            onDismiss = { vm.dismissEditDictionary() }
        )
    }

    if (vm.showAddDict) {
        EditDictionaryDialog(
            dictionary = DictionaryConfig(
                id = java.util.UUID.randomUUID().toString(),
                name = "",
                urlTemplate = "",
                cssSelector = "",
                isEnabled = true
            ),
            onConfirm = { newDict -> vm.addDictionary(newDict) },
            onDismiss = { vm.dismissAddDict() }
        )
    }

    if (vm.showPresetDicts) {
        PresetDictionaryDialog(
            existingIds = dictionaries.map { it.id },
            onAdd = { preset -> vm.addPreset(preset) },
            onDismiss = { vm.dismissPresetDicts() }
        )
    }

    if (vm.showImportConfirm && vm.pendingImportData != null) {
        val data = vm.pendingImportData!!
        AlertDialog(
            onDismissRequest = { vm.dismissImportConfirm() },
            title = { Text("Import Data") },
            text = {
                Text("This will replace all current bookmarks and settings with the imported data.\n\n" +
                     "Bookmarks: ${data.bookmarks.size}\n" +
                     "RSS Subscriptions: ${data.subscriptions.size}\n" +
                     "Settings: ${data.settings.size} items")
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmImport() }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissImportConfirm() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DictionaryItem(
    dictionary: DictionaryConfig,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dictionary.name.ifEmpty { "Unnamed" },
                    style = MaterialTheme.typography.titleSmall
                )
                if (!dictionary.isEnabled) {
                    Text(
                        text = " (disabled)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = dictionary.urlTemplate.take(50) + if (dictionary.urlTemplate.length > 50) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = dictionary.isEnabled,
            onCheckedChange = onToggle
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Add, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    summary: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 4.dp else 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    if (summary != null && !expanded) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ThemeRadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatReadingTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format("%dh %dm %ds", hours, minutes, seconds)
        minutes > 0 -> String.format("%dm %ds", minutes, seconds)
        else -> String.format("%ds", seconds)
    }
}

private fun isToday(timestampMillis: Long): Boolean {
    val now = System.currentTimeMillis()
    val dayMillis = 24L * 60 * 60 * 1000
    return (now - timestampMillis) < dayMillis && (now - timestampMillis) >= 0
}

@Composable
private fun EditDictionaryDialog(
    dictionary: DictionaryConfig,
    onConfirm: (DictionaryConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(dictionary.name) }
    var urlTemplate by remember { mutableStateOf(dictionary.urlTemplate) }
    var cssSelector by remember { mutableStateOf(dictionary.cssSelector) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (dictionary.name.isEmpty()) "Add Dictionary" else "Edit Dictionary") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g., Youdao, Cambridge") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = urlTemplate,
                    onValueChange = { urlTemplate = it },
                    label = { Text("URL Template") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Use {word} as placeholder, e.g., https://dict.youdao.com/result?word={word}&lang=en") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = cssSelector,
                    onValueChange = { cssSelector = it },
                    label = { Text("CSS Selector (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("e.g., .trans-container, #content") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && urlTemplate.isNotBlank()) {
                        onConfirm(dictionary.copy(
                            name = name.trim(),
                            urlTemplate = urlTemplate.trim(),
                            cssSelector = cssSelector.trim()
                        ))
                    }
                },
                enabled = name.isNotBlank() && urlTemplate.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PresetDictionaryDialog(
    existingIds: List<String>,
    onAdd: (DictionaryConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(
        DictionaryConfig(
            id = "oxford",
            name = "Oxford Learner's Dictionaries",
            urlTemplate = "https://www.oxfordlearnersdictionaries.com/definition/english/{word}",
            cssSelector = ".webtop, .responsive_entry_center_wrap",
            isEnabled = true
        ),
        DictionaryConfig(
            id = "longman",
            name = "Longman Dictionary",
            urlTemplate = "https://www.ldoceonline.com/dictionary/{word}",
            cssSelector = ".dictentry, .Head",
            isEnabled = true
        ),
        DictionaryConfig(
            id = "collins",
            name = "Collins Dictionary",
            urlTemplate = "https://www.collinsdictionary.com/dictionary/english/{word}",
            cssSelector = ".content, .dictentry",
            isEnabled = true
        ),
        DictionaryConfig(
            id = "cambridge",
            name = "Cambridge Dictionary",
            urlTemplate = "https://dictionary.cambridge.org/dictionary/english/{word}",
            cssSelector = ".entry-body__el, .hw",
            isEnabled = true
        ),
        DictionaryConfig(
            id = "merriam",
            name = "Merriam-Webster",
            urlTemplate = "https://www.merriam-webster.com/dictionary/{word}",
            cssSelector = "#dictionary-entry-1, .word-syllables",
            isEnabled = true
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preset Dictionaries") },
        text = {
            Column {
                Text(
                    "Select dictionaries to add:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                presets.forEach { preset ->
                    val alreadyAdded = existingIds.contains(preset.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (alreadyAdded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = preset.urlTemplate.take(50) + "...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (alreadyAdded) {
                            Text("Added", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        } else {
                            TextButton(onClick = { onAdd(preset) }) {
                                Text("Add")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}