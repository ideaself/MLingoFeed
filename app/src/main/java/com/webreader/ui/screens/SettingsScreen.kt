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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val dictionaryUrl by app.settingsManager.dictionaryUrl.collectAsState(initial = "")
    val dictionaryName by app.settingsManager.dictionaryName.collectAsState(initial = "")
    val aiApiUrl by app.settingsManager.aiApiUrl.collectAsState(initial = "")
    val aiApiKey by app.settingsManager.aiApiKey.collectAsState(initial = "")
    val aiModel by app.settingsManager.aiModel.collectAsState(initial = "")
    val targetLang by app.settingsManager.translateTargetLang.collectAsState(initial = "")

    var dictUrlInput by remember(dictionaryUrl) { mutableStateOf(dictionaryUrl) }
    var dictNameInput by remember(dictionaryName) { mutableStateOf(dictionaryName) }
    var aiUrlInput by remember(aiApiUrl) { mutableStateOf(aiApiUrl) }
    var aiKeyInput by remember(aiApiKey) { mutableStateOf(aiApiKey) }
    var aiModelInput by remember(aiModel) { mutableStateOf(aiModel) }
    var targetLangInput by remember(targetLang) { mutableStateOf(targetLang) }

    var showPresetDropdown by remember { mutableStateOf(false) }

    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportData by remember { mutableStateOf<com.webreader.data.export.ExportData?>(null) }

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
            TopAppBar(title = { Text("Settings") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        app.settingsManager.setDictionaryUrl(dictUrlInput)
                        app.settingsManager.setDictionaryName(dictNameInput)
                        app.settingsManager.setAiApiUrl(aiUrlInput)
                        app.settingsManager.setAiApiKey(aiKeyInput)
                        app.settingsManager.setAiModel(aiModelInput)
                        app.settingsManager.setTranslateTargetLang(targetLangInput)
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
                    OutlinedTextField(
                        value = dictNameInput,
                        onValueChange = { dictNameInput = it },
                        label = { Text("Dictionary Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = showPresetDropdown,
                        onExpandedChange = { showPresetDropdown = !showPresetDropdown }
                    ) {
                        OutlinedTextField(
                            value = "Presets",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Quick Setup") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPresetDropdown) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = showPresetDropdown,
                            onDismissRequest = { showPresetDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Youdao") },
                                onClick = {
                                    dictUrlInput = "https://dict.youdao.com/jsonapi?jsonversion=2&client=mobile&dicts=%7B%22count%22%3A99%2C%22dicts%22%3A%5B%5B%22ec%22%2C%22ce%22%2C%22newcj%22%2C%22newjc%22%2C%22kc%22%2C%22ck%22%2C%22fc%22%2C%22cf%22%2C%22multle%22%2C%22jtj%22%2C%22pic_dict%22%2C%22tc%22%2C%22ce_new%22%2C%22ec_new%22%2C%22kbbig%22%2C%22simple%22%2C%22wordform%22%2C%22wikipedia_digest%22%2C%22ee%22%2C%22phrs%22%2C%22syno%22%2C%22collins%22%2C%22wordvideo%22%2C%22en2en%22%2C%22etym%22%2C%22uling%22%2C%22blng_sents_part%22%2C%22hh%22%2C%22rel_word%22%2C%22special%22%2C%22langs%22%2C%22web_trans%22%2C%22fanyi%22%2C%22sgthree%22%2C%22auth_dict%22%2C%22ned%22%2C%22quiz_dict%22%2C%22meikao%22%2C%22bcc%22%2C%22longman%22%2C%22oxford%22%2C%22pukao%22%2C%22webster%22%2C%22eepc%22%2C%22cet4%22%2C%22cet6%22%2C%22ee_exp%22%2C%22xc%22%2C%22ja2zh%22%2C%22jc2zh%22%2C%22jp2zh%22%2C%22kc2zh%22%5D%5D%7D&q="
                                    dictNameInput = "Youdao"
                                    showPresetDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Merriam-Webster") },
                                onClick = {
                                    dictUrlInput = "https://www.merriam-webster.com/dictionary/"
                                    dictNameInput = "Merriam-Webster"
                                    showPresetDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cambridge") },
                                onClick = {
                                    dictUrlInput = "https://dictionary.cambridge.org/dictionary/english/"
                                    dictNameInput = "Cambridge"
                                    showPresetDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Custom") },
                                onClick = {
                                    showPresetDropdown = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = dictUrlInput,
                        onValueChange = { dictUrlInput = it },
                        label = { Text("Dictionary API URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Use {word} as placeholder or append word directly") }
                    )
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
