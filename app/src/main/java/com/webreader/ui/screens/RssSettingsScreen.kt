package com.webreader.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import kotlinx.coroutines.Dispatchers as KotlinCoroutinesDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.webreader.data.database.RssRule
import com.webreader.data.repository.OpmlParser
import com.webreader.data.work.RssSyncWorker
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.webreader.WebReaderApp
    val scope = rememberCoroutineScope()

    val folders by app.rssRepository.allFolders.collectAsState(initial = emptyList())
    val rules by app.rssRepository.allRules.collectAsState(initial = emptyList())

    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RssRule?>(null) }
    var editingFolderId by remember { mutableStateOf<Long?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importOpml(context, app, uri)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RSS Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            item {
                Text("Data Management", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        importLauncher.launch(intent)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.size(12.dp))
                        Text("Import OPML", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { exportOpml(context, app) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.size(12.dp))
                        Text("Export OPML", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { RssSyncWorker.cancel(context) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.size(12.dp))
                        Text("Disable background sync", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { RssSyncWorker.schedule(context) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.size(12.dp))
                        Text("Enable background sync (1 hour)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Folders", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showAddFolderDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add folder")
                    }
                }
            }

            items(folders) { folder ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(folder.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editingFolderId = folder.id }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { scope.launch { app.rssRepository.deleteFolder(folder.id) } }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Automation Rules", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showAddRuleDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add rule")
                    }
                }
            }

            if (rules.isEmpty()) {
                item {
                    Text(
                        "No rules yet. Create rules to automatically tag or mark articles as read.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(rules) { rule ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "When keyword \"${rule.keyword}\" → ${rule.action.replace("_", " ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = { scope.launch { app.rssRepository.toggleRule(rule.id, it) } }
                        )
                        IconButton(onClick = { editingRule = rule }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { scope.launch { app.rssRepository.deleteRule(rule.id) } }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showAddFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            title = { Text("Add Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) {
                        scope.launch { app.rssRepository.addFolder(folderName.trim()) }
                        showAddFolderDialog = false
                    }
                }, enabled = folderName.isNotBlank()) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    editingFolderId?.let { folderId ->
        val folder = folders.find { it.id == folderId }
        var folderName by remember { mutableStateOf(folder?.name ?: "") }
        AlertDialog(
            onDismissRequest = { editingFolderId = null },
            title = { Text("Edit Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) {
                        scope.launch { app.rssRepository.renameFolder(folderId, folderName.trim()) }
                        editingFolderId = null
                    }
                }, enabled = folderName.isNotBlank()) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingFolderId = null }) { Text("Cancel") }
            }
        )
    }

    if (showAddRuleDialog) {
        var ruleName by remember { mutableStateOf("") }
        var keyword by remember { mutableStateOf("") }
        var action by remember { mutableStateOf("mark_read") }

        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text("Add Rule") },
            text = {
                Column {
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text("Rule Name") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("Keyword") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Action", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.clickable { action = "mark_read" }.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = action == "mark_read",
                            onClick = { action = "mark_read" }
                        )
                        Text("Auto mark as read")
                    }
                    Row(
                        modifier = Modifier.clickable { action = "favorite" }.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = action == "favorite",
                            onClick = { action = "favorite" }
                        )
                        Text("Auto favorite")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (ruleName.isNotBlank() && keyword.isNotBlank()) {
                        scope.launch {
                            app.rssRepository.addRule(ruleName.trim(), keyword.trim(), action)
                        }
                        showAddRuleDialog = false
                    }
                }, enabled = ruleName.isNotBlank() && keyword.isNotBlank()) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) { Text("Cancel") }
            }
        )
    }

    editingRule?.let { rule ->
        var ruleName by remember { mutableStateOf(rule.name) }
        var keyword by remember { mutableStateOf(rule.keyword) }
        var action by remember { mutableStateOf(rule.action) }

        AlertDialog(
            onDismissRequest = { editingRule = null },
            title = { Text("Edit Rule") },
            text = {
                Column {
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text("Rule Name") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("Keyword") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Action", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.clickable { action = "mark_read" }.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = action == "mark_read",
                            onClick = { action = "mark_read" }
                        )
                        Text("Auto mark as read")
                    }
                    Row(
                        modifier = Modifier.clickable { action = "favorite" }.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = action == "favorite",
                            onClick = { action = "favorite" }
                        )
                        Text("Auto favorite")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (ruleName.isNotBlank() && keyword.isNotBlank()) {
                        scope.launch {
                            app.rssRepository.deleteRule(rule.id)
                            app.rssRepository.addRule(ruleName.trim(), keyword.trim(), action)
                        }
                        editingRule = null
                    }
                }, enabled = ruleName.isNotBlank() && keyword.isNotBlank()) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingRule = null }) { Text("Cancel") }
            }
        )
    }
}

private fun importOpml(context: Context, app: com.webreader.WebReaderApp, uri: Uri) {
    GlobalScope.launch {
        withContext(KotlinCoroutinesDispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                reader.close()

                val opmlFolders = OpmlParser.parseOpml(content)
                opmlFolders.forEach { opmlFolder ->
                    val folderId = app.rssRepository.addFolder(opmlFolder.name)
                    opmlFolder.feeds.forEach { feed ->
                        app.rssRepository.addSubscription(feed.title, feed.url, folderId)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}

private fun exportOpml(context: Context, app: com.webreader.WebReaderApp) {
    GlobalScope.launch {
        withContext(KotlinCoroutinesDispatchers.IO) {
            val folders = app.rssRepository.allFolders.first()
            val subs = app.rssRepository.allSubscriptions.first()
            val opmlContent = OpmlParser.exportToOpml(folders, subs)

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/x-opml"
                putExtra(Intent.EXTRA_TITLE, "webreader_subscriptions.opml")
            }
            context.startActivity(intent)
        }
    }
}
