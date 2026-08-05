package com.mlingofeed.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.export.ExportData
import com.mlingofeed.data.export.ExportManager
import com.mlingofeed.data.settings.DictionaryConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: WebReaderApp) : ViewModel() {

    val dictionaries = app.settingsManager.dictionaries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val fontSize = app.settingsManager.fontSize.stateIn(viewModelScope, SharingStarted.Eagerly, 100)
    val rssFontSize = app.settingsManager.rssFontSize.stateIn(viewModelScope, SharingStarted.Eagerly, 17f)
    val themeMode = app.settingsManager.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val readingTimeSeconds = app.settingsManager.readingTimeSeconds.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val readingSessions = app.settingsManager.readingSessions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var aiUrlInput by mutableStateOf("")
        private set
    var aiKeyInput by mutableStateOf("")
        private set
    var aiModelInput by mutableStateOf("")
        private set
    var targetLangInput by mutableStateOf("")
        private set

    var modelList by mutableStateOf<List<String>>(emptyList())
        private set
    var isLoadingModels by mutableStateOf(false)
        private set
    var showModelDropdown by mutableStateOf(false)
        private set

    var showImportConfirm by mutableStateOf(false)
        private set
    var pendingImportData by mutableStateOf<ExportData?>(null)
        private set

    var editingDict by mutableStateOf<DictionaryConfig?>(null)
        private set
    var showAddDict by mutableStateOf(false)
        private set
    var showPresetDicts by mutableStateOf(false)
        private set

    var expandedSection by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            aiUrlInput = app.settingsManager.aiApiUrl.first()
            aiKeyInput = app.settingsManager.aiApiKey.first()
            aiModelInput = app.settingsManager.aiModel.first()
            targetLangInput = app.settingsManager.translateTargetLang.first()
        }
    }

    fun onAiUrlInputChange(value: String) {
        aiUrlInput = value
    }

    fun onAiKeyInputChange(value: String) {
        aiKeyInput = value
    }

    fun onAiModelInputChange(value: String) {
        aiModelInput = value
    }

    fun onTargetLangInputChange(value: String) {
        targetLangInput = value
    }

    fun saveSettings() {
        viewModelScope.launch {
            app.settingsManager.setAiApiUrl(aiUrlInput)
            app.settingsManager.setAiApiKey(aiKeyInput)
            app.settingsManager.setAiModel(aiModelInput)
            app.settingsManager.setTranslateTargetLang(targetLangInput)
            Toast.makeText(app, "Settings saved", Toast.LENGTH_SHORT).show()
        }
    }

    fun fetchModels() {
        if (aiKeyInput.isBlank()) {
            Toast.makeText(app, "Please enter API Key first", Toast.LENGTH_SHORT).show()
            return
        }
        isLoadingModels = true
        viewModelScope.launch {
            try {
                val models = app.chatRepository.fetchModels(aiUrlInput, aiKeyInput)
                modelList = models
                showModelDropdown = models.isNotEmpty()
            } catch (e: Exception) {
                Toast.makeText(app, e.message, Toast.LENGTH_SHORT).show()
            }
            isLoadingModels = false
        }
    }

    fun selectModel(model: String) {
        aiModelInput = model
        showModelDropdown = false
    }

    fun toggleSection(section: String) {
        expandedSection = if (expandedSection == section) null else section
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { app.settingsManager.setThemeMode(mode) }
    }

    fun setFontSize(size: Int) {
        viewModelScope.launch { app.settingsManager.setFontSize(size) }
    }

    fun setRssFontSize(size: Float) {
        viewModelScope.launch { app.settingsManager.setRssFontSize(size) }
    }

    fun resetReadingTime() {
        viewModelScope.launch {
            app.settingsManager.resetReadingTime()
            Toast.makeText(app, "Reading time reset", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleDictionary(index: Int, enabled: Boolean) {
        viewModelScope.launch {
            val updated = dictionaries.value.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(isEnabled = enabled)
                app.settingsManager.setDictionaries(updated)
            }
        }
    }

    fun deleteDictionary(index: Int) {
        viewModelScope.launch {
            val updated = dictionaries.value.toMutableList()
            if (index in updated.indices) {
                updated.removeAt(index)
                app.settingsManager.setDictionaries(updated)
            }
        }
    }

    fun moveDictionary(index: Int, direction: Int) {
        viewModelScope.launch {
            val updated = dictionaries.value.toMutableList()
            val target = index + direction
            if (target in updated.indices) {
                val temp = updated[index]
                updated[index] = updated[target]
                updated[target] = temp
                app.settingsManager.setDictionaries(updated)
            }
        }
    }

    fun requestEditDictionary(dictionary: DictionaryConfig) {
        editingDict = dictionary
    }

    fun dismissEditDictionary() {
        editingDict = null
    }

    fun saveDictionary(updated: DictionaryConfig) {
        viewModelScope.launch {
            val index = dictionaries.value.indexOfFirst { it.id == updated.id }
            if (index >= 0) {
                val updatedList = dictionaries.value.toMutableList()
                updatedList[index] = updated
                app.settingsManager.setDictionaries(updatedList)
            }
        }
        editingDict = null
    }

    fun openAddDict() {
        showAddDict = true
    }

    fun dismissAddDict() {
        showAddDict = false
    }

    fun addDictionary(dict: DictionaryConfig) {
        viewModelScope.launch {
            app.settingsManager.setDictionaries(dictionaries.value + dict)
        }
        showAddDict = false
    }

    fun openPresetDicts() {
        showPresetDicts = true
    }

    fun dismissPresetDicts() {
        showPresetDicts = false
    }

    fun addPreset(preset: DictionaryConfig) {
        viewModelScope.launch {
            app.settingsManager.setDictionaries(dictionaries.value + preset)
        }
    }

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            val bookmarks = app.bookmarkRepository.allBookmarks.first()
            val subscriptions = app.rssRepository.allSubscriptions.first()
            val settings = app.settingsManager.getAllSettings()
            val ok = ExportManager.exportToJson(app, uri, bookmarks, settings, subscriptions)
            Toast.makeText(app, if (ok) "Export successful" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            val data = ExportManager.importFromJson(app, uri)
            if (data == null) {
                Toast.makeText(app, "Import failed: invalid file", Toast.LENGTH_SHORT).show()
                return@launch
            }
            pendingImportData = data
            showImportConfirm = true
        }
    }

    fun dismissImportConfirm() {
        showImportConfirm = false
        pendingImportData = null
    }

    fun confirmImport() {
        val data = pendingImportData ?: return
        viewModelScope.launch {
            val repo = app.bookmarkRepository
            repo.allBookmarks.first().forEach { repo.delete(it) }
            data.bookmarks.forEach { repo.insert(it) }
            if (data.subscriptions.isNotEmpty()) {
                val rss = app.rssRepository
                val existingUrls = rss.allSubscriptions.first().map { it.url }
                data.subscriptions
                    .filter { it.url !in existingUrls && it.title.isNotBlank() }
                    .forEach { rss.addSubscription(it.title, it.url) }
            }
            if (data.settings.isNotEmpty()) {
                app.settingsManager.importSettings(data.settings)
            }
            Toast.makeText(app, "Import successful", Toast.LENGTH_SHORT).show()
        }
        showImportConfirm = false
        pendingImportData = null
    }
}
