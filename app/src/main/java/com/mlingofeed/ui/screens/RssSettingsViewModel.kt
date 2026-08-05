package com.mlingofeed.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.RssRule
import com.mlingofeed.data.repository.OpmlParser
import com.mlingofeed.data.work.RssSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class RssSettingsViewModel(private val app: WebReaderApp) : ViewModel() {

    private val repository = app.rssRepository

    val folders = repository.allFolders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val rules = repository.allRules.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var showAddFolderDialog by mutableStateOf(false)
        private set
    var showAddRuleDialog by mutableStateOf(false)
        private set
    var editingRule by mutableStateOf<RssRule?>(null)
        private set
    var editingFolderId by mutableStateOf<Long?>(null)
        private set

    fun openAddFolderDialog() {
        showAddFolderDialog = true
    }

    fun closeAddFolderDialog() {
        showAddFolderDialog = false
    }

    fun addFolder(name: String) {
        viewModelScope.launch { repository.addFolder(name.trim()) }
        showAddFolderDialog = false
    }

    fun requestEditFolder(folderId: Long) {
        editingFolderId = folderId
    }

    fun cancelEditFolder() {
        editingFolderId = null
    }

    fun renameFolder(folderId: Long, name: String) {
        viewModelScope.launch { repository.renameFolder(folderId, name.trim()) }
        editingFolderId = null
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch { repository.deleteFolder(folderId) }
    }

    fun openAddRuleDialog() {
        showAddRuleDialog = true
    }

    fun closeAddRuleDialog() {
        showAddRuleDialog = false
    }

    fun addRule(name: String, keyword: String, action: String) {
        viewModelScope.launch { repository.addRule(name.trim(), keyword.trim(), action) }
        showAddRuleDialog = false
    }

    fun requestEditRule(rule: RssRule) {
        editingRule = rule
    }

    fun cancelEditRule() {
        editingRule = null
    }

    fun updateRule(rule: RssRule, name: String, keyword: String, action: String) {
        viewModelScope.launch {
            repository.deleteRule(rule.id)
            repository.addRule(name.trim(), keyword.trim(), action)
        }
        editingRule = null
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch { repository.deleteRule(ruleId) }
    }

    fun toggleRule(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch { repository.toggleRule(ruleId, enabled) }
    }

    fun scheduleSync(context: Context) {
        RssSyncWorker.schedule(context)
    }

    fun cancelSync(context: Context) {
        RssSyncWorker.cancel(context)
    }

    fun importOpml(uri: Uri) {
        viewModelScope.launch {
            val success = try {
                withContext(Dispatchers.IO) {
                    val inputStream = app.contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val content = reader.readText()
                    reader.close()

                    val opmlFolders = OpmlParser.parseOpml(content)
                    if (opmlFolders.isEmpty()) {
                        false
                    } else {
                        opmlFolders.forEach { opmlFolder ->
                            val folderId = repository.addFolder(opmlFolder.name)
                            opmlFolder.feeds.forEach { feed ->
                                repository.addSubscription(feed.title, feed.url, folderId)
                            }
                        }
                        true
                    }
                }
            } catch (_: Exception) {
                false
            }
            Toast.makeText(app, if (success) "OPML imported" else "Import failed: invalid file", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportOpml(uri: Uri, content: String) {
        viewModelScope.launch {
            try {
                app.contentResolver.openOutputStream(uri)?.use {
                    it.write(content.toByteArray())
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun buildOpml(): String {
        return withContext(Dispatchers.IO) {
            val folders = repository.allFolders.first()
            val subs = repository.allSubscriptions.first()
            OpmlParser.exportToOpml(folders, subs)
        }
    }
}
