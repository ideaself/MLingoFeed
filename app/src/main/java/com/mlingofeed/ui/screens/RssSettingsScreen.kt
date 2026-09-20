package com.mlingofeed.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mlingofeed.AppViewModelFactory
import com.mlingofeed.data.database.RssRule
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.mlingofeed.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.mlingofeed.WebReaderApp
    val vm: RssSettingsViewModel = viewModel(factory = remember { AppViewModelFactory(app) })
    val scope = rememberCoroutineScope()

    val folders by vm.folders.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val syncEnabled = vm.syncEnabled
    val syncIntervalHours by vm.syncIntervalHours.collectAsStateWithLifecycle()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                vm.importOpml(uri)
            }
        }
    }

    var pendingOpmlContent by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-opml")
    ) { uri ->
        if (uri != null) {
            val content = pendingOpmlContent
            if (content != null) {
                vm.exportOpml(uri, content)
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rss_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            item {
                Text(stringResource(R.string.data_management), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
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
                        Text(stringResource(R.string.import_opml), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            pendingOpmlContent = vm.buildOpml()
                            exportLauncher.launch("MLingoFeed_subscriptions.opml")
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(stringResource(R.string.export_opml), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (syncEnabled) {
                            vm.cancelSync(context)
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            vm.scheduleSync(context)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = if (syncEnabled) stringResource(R.string.disable_background_sync) else stringResource(R.string.sync_enable_with_interval, syncIntervalHours),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (syncEnabled) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (syncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(if (syncEnabled) 8.dp else 16.dp))
            }

            if (syncEnabled) {
                item {
                    Column {
                        Text(
                            stringResource(R.string.sync_interval),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1L, 3L, 6L, 12L, 24L).forEach { hours ->
                                FilterChip(
                                    selected = syncIntervalHours == hours,
                                    onClick = { vm.setSyncInterval(context, hours) },
                                    label = { Text(stringResource(R.string.sync_hours_short, hours)) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.folders), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.openAddFolderDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_folder))
                    }
                }
            }

            items(folders, key = { "folder-${it.id}" }) { folder ->
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
                        IconButton(onClick = { vm.requestEditFolder(folder.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { vm.deleteFolder(folder.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
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
                    Text(stringResource(R.string.automation_rules), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.openAddRuleDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_rule))
                    }
                }
            }

            if (rules.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_rules_yet_create_rules_to_automatically_tag_o),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(rules, key = { "rule-${it.id}" }) { rule ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.rule_description, rule.keyword, rule.action.replace("_", " ")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = { vm.toggleRule(rule.id, it) }
                        )
                        IconButton(onClick = { vm.requestEditRule(rule) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { vm.deleteRule(rule.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (vm.showAddFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.closeAddFolderDialog() },
            title = { Text(stringResource(R.string.add_folder_2)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) {
                        vm.addFolder(folderName)
                    }
                }, enabled = folderName.isNotBlank()) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.closeAddFolderDialog() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    vm.editingFolderId?.let { folderId ->
        val folder = folders.find { it.id == folderId }
        var folderName by remember { mutableStateOf(folder?.name ?: "") }
        AlertDialog(
            onDismissRequest = { vm.cancelEditFolder() },
            title = { Text(stringResource(R.string.edit_folder)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) {
                        vm.renameFolder(folderId, folderName)
                    }
                }, enabled = folderName.isNotBlank()) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelEditFolder() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (vm.showAddRuleDialog) {
        var ruleName by remember { mutableStateOf("") }
        var keyword by remember { mutableStateOf("") }
        var action by remember { mutableStateOf("mark_read") }

        AlertDialog(
            onDismissRequest = { vm.closeAddRuleDialog() },
            title = { Text(stringResource(R.string.add_rule_2)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text(stringResource(R.string.rule_name)) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text(stringResource(R.string.keyword)) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.action), style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.clickable { action = "mark_read" }.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = action == "mark_read",
                            onClick = { action = "mark_read" }
                        )
                        Text(stringResource(R.string.auto_mark_as_read))
                    }
                    Row(
                        modifier = Modifier.clickable { action = "favorite" }.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = action == "favorite",
                            onClick = { action = "favorite" }
                        )
                        Text(stringResource(R.string.auto_favorite))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (ruleName.isNotBlank() && keyword.isNotBlank()) {
                        vm.addRule(ruleName, keyword, action)
                    }
                }, enabled = ruleName.isNotBlank() && keyword.isNotBlank()) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.closeAddRuleDialog() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    vm.editingRule?.let { rule ->
        var ruleName by remember { mutableStateOf(rule.name) }
        var keyword by remember { mutableStateOf(rule.keyword) }
        var action by remember { mutableStateOf(rule.action) }

        AlertDialog(
            onDismissRequest = { vm.cancelEditRule() },
            title = { Text(stringResource(R.string.edit_rule)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text(stringResource(R.string.rule_name)) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text(stringResource(R.string.keyword)) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.action), style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.clickable { action = "mark_read" }.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = action == "mark_read",
                            onClick = { action = "mark_read" }
                        )
                        Text(stringResource(R.string.auto_mark_as_read))
                    }
                    Row(
                        modifier = Modifier.clickable { action = "favorite" }.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = action == "favorite",
                            onClick = { action = "favorite" }
                        )
                        Text(stringResource(R.string.auto_favorite))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (ruleName.isNotBlank() && keyword.isNotBlank()) {
                        vm.updateRule(rule, ruleName, keyword, action)
                    }
                }, enabled = ruleName.isNotBlank() && keyword.isNotBlank()) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelEditRule() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
