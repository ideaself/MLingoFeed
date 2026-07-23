package com.webreader.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webreader.WebReaderApp
import com.webreader.data.export.ExportManager
import com.webreader.data.settings.DictionaryConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val dictionaries by app.settingsManager.dictionaries.collectAsState(initial = emptyList())
    val aiApiUrl by app.settingsManager.aiApiUrl.collectAsState(initial = "")
    val aiApiKey by app.settingsManager.aiApiKey.collectAsState(initial = "")
    val aiModel by app.settingsManager.aiModel.collectAsState(initial = "")
    val targetLang by app.settingsManager.translateTargetLang.collectAsState(initial = "")

    var aiUrlInput by remember(aiApiUrl) { mutableStateOf(aiApiUrl) }
    var aiKeyInput by remember(aiApiKey) { mutableStateOf(aiApiKey) }
    var aiModelInput by remember(aiModel) { mutableStateOf(aiModel) }
    var targetLangInput by remember(targetLang) { mutableStateOf(targetLang) }

    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportData by remember { mutableStateOf<com.webreader.data.export.ExportData?>(null) }

    var editingDict by remember { mutableStateOf<DictionaryConfig?>(null) }
    var showAddDict by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bookmarks = app.bookmarkRepository.allBookmarks.first()
            val settings = app.settingsManager.getAllSettings()
            val ok = ExportManager.exportToJson(context, uri, bookmarks, settings)
            Toast.makeText(context, if (ok) "Export successful" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val data = ExportManager.importFromJson(context, uri)
            if (data == null) {
                Toast.makeText(context, "Import failed: invalid file", Toast.LENGTH_SHORT).show()
                return@launch
            }
            pendingImportData = data
            showImportConfirm = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        app.settingsManager.setAiApiUrl(aiUrlInput)
                        app.settingsManager.setAiApiKey(aiKeyInput)
                        app.settingsManager.setAiModel(aiModelInput)
                        app.settingsManager.setTranslateTargetLang(targetLangInput)
                        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Dictionary Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    dictionaries.forEachIndexed { index, dict ->
                        DictionaryItem(
                            dictionary = dict,
                            onToggle = { enabled ->
                                scope.launch {
                                    val updated = dictionaries.toMutableList()
                                    updated[index] = dict.copy(isEnabled = enabled)
                                    app.settingsManager.setDictionaries(updated)
                                }
                            },
                            onEdit = { editingDict = dict },
                            onDelete = {
                                scope.launch {
                                    val updated = dictionaries.toMutableList()
                                    updated.removeAt(index)
                                    app.settingsManager.setDictionaries(updated)
                                }
                            },
                            onMoveUp = {
                                if (index > 0) {
                                    scope.launch {
                                        val updated = dictionaries.toMutableList()
                                        val temp = updated[index]
                                        updated[index] = updated[index - 1]
                                        updated[index - 1] = temp
                                        app.settingsManager.setDictionaries(updated)
                                    }
                                }
                            },
                            onMoveDown = {
                                if (index < dictionaries.size - 1) {
                                    scope.launch {
                                        val updated = dictionaries.toMutableList()
                                        val temp = updated[index]
                                        updated[index] = updated[index + 1]
                                        updated[index + 1] = temp
                                        app.settingsManager.setDictionaries(updated)
                                    }
                                }
                            },
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
                        TextButton(onClick = { showAddDict = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Dictionary")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "AI / Translation Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = aiUrlInput,
                        onValueChange = { aiUrlInput = it },
                        label = { Text("AI API URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("DeepSeek: https://api.deepseek.com/chat/completions") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = aiKeyInput,
                        onValueChange = { aiKeyInput = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(aiKeyInput))
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = aiModelInput,
                        onValueChange = { aiModelInput = it },
                        label = { Text("Model") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("DeepSeek: deepseek-chat") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = targetLangInput,
                        onValueChange = { targetLangInput = it },
                        label = { Text("Target Language") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("e.g., Chinese, Japanese, Spanish") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Data Management",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Web Reader",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Version 1.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "A web reading app with dictionary lookup, translation, and AI chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (editingDict != null) {
        EditDictionaryDialog(
            dictionary = editingDict!!,
            onConfirm = { updated ->
                scope.launch {
                    val index = dictionaries.indexOfFirst { it.id == updated.id }
                    if (index >= 0) {
                        val updatedList = dictionaries.toMutableList()
                        updatedList[index] = updated
                        app.settingsManager.setDictionaries(updatedList)
                    }
                }
                editingDict = null
            },
            onDismiss = { editingDict = null }
        )
    }

    if (showAddDict) {
        EditDictionaryDialog(
            dictionary = DictionaryConfig(
                id = java.util.UUID.randomUUID().toString(),
                name = "",
                urlTemplate = "",
                cssSelector = "",
                isEnabled = true
            ),
            onConfirm = { newDict ->
                scope.launch {
                    app.settingsManager.setDictionaries(dictionaries + newDict)
                }
                showAddDict = false
            },
            onDismiss = { showAddDict = false }
        )
    }

    if (showImportConfirm && pendingImportData != null) {
        val data = pendingImportData!!
        AlertDialog(
            onDismissRequest = {
                showImportConfirm = false
                pendingImportData = null
            },
            title = { Text("Import Data") },
            text = {
                Text("This will replace all current bookmarks and settings with the imported data.\n\n" +
                     "Bookmarks: ${data.bookmarks.size}\n" +
                     "Settings: ${data.settings.size} items")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val repo = app.bookmarkRepository
                        repo.allBookmarks.first().forEach { repo.delete(it) }
                        data.bookmarks.forEach { repo.insert(it) }
                        if (data.settings.isNotEmpty()) {
                            app.settingsManager.importSettings(data.settings)
                        }
                        Toast.makeText(context, "Import successful", Toast.LENGTH_SHORT).show()
                    }
                    showImportConfirm = false
                    pendingImportData = null
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    pendingImportData = null
                }) {
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