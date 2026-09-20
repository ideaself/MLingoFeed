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
import com.mlingofeed.data.work.WordReviewWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val app: WebReaderApp) : ViewModel() {

    val dictionaries = app.settingsManager.dictionaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val fontSize = app.settingsManager.fontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100)
    val rssFontSize = app.settingsManager.rssFontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 17f)
    val themeMode = app.settingsManager.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")
    val readingTimeSeconds = app.settingsManager.readingTimeSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val readingSessions = app.settingsManager.readingSessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val wordReminderEnabled = app.settingsManager.wordReviewReminderEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
            val settings = app.settingsManager.getAllSettings()
            aiUrlInput = settings["ai_api_url"] ?: ""
            aiKeyInput = settings["ai_api_key"] ?: ""
            aiModelInput = settings["ai_model"] ?: ""
            targetLangInput = settings["translate_target_lang"] ?: "Chinese"
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
            } catch (e: CancellationException) {
                throw e
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

    fun toggleDictionary(dictionary: DictionaryConfig, enabled: Boolean) {
        viewModelScope.launch {
            app.settingsManager.updateDictionaries { dicts ->
                dicts.map { if (it.id == dictionary.id) it.copy(isEnabled = enabled) else it }
            }
        }
    }

    fun deleteDictionary(dictionary: DictionaryConfig) {
        viewModelScope.launch {
            app.settingsManager.updateDictionaries { dicts ->
                dicts.filterNot { it.id == dictionary.id }
            }
        }
    }

    fun moveDictionary(dictionary: DictionaryConfig, direction: Int) {
        viewModelScope.launch {
            app.settingsManager.updateDictionaries { dicts ->
                val index = dicts.indexOfFirst { it.id == dictionary.id }
                val target = index + direction
                if (index < 0 || target !in dicts.indices) {
                    dicts
                } else {
                    dicts.toMutableList().apply {
                        val temp = this[index]
                        this[index] = this[target]
                        this[target] = temp
                    }
                }
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
            app.settingsManager.updateDictionaries { dicts ->
                val index = dicts.indexOfFirst { it.id == updated.id }
                if (index >= 0) {
                    dicts.toMutableList().also { it[index] = updated }
                } else {
                    dicts
                }
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
            app.settingsManager.updateDictionaries { dicts ->
                if (dicts.any { it.id == dict.id }) dicts else dicts + dict
            }
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
            app.settingsManager.updateDictionaries { dicts ->
                if (dicts.any { it.id == preset.id }) dicts else dicts + preset
            }
        }
    }

    fun setWordReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            app.settingsManager.setWordReviewReminderEnabled(enabled)
            if (enabled) {
                WordReviewWorker.schedule(app)
            } else {
                WordReviewWorker.cancel(app)
            }
        }
    }

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val bookmarks = app.bookmarkRepository.allBookmarks.first()
                val subscriptions = app.rssRepository.allSubscriptions.first()
                val settings = app.settingsManager.getExportableSettings()
                ExportManager.exportToJson(app, uri, bookmarks, settings, subscriptions)
            }
            Toast.makeText(app, if (ok) "Export successful" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { ExportManager.importFromJson(app, uri) }
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
            repo.replaceAll(data.bookmarks)
            if (data.subscriptions.isNotEmpty()) {
                val rss = app.rssRepository
                val existingUrls = rss.allSubscriptions.first().map { it.url }.toSet()
                val newSubscriptions = data.subscriptions
                    .filter { it.url !in existingUrls && it.title.isNotBlank() }
                    .map { it.title to it.url }
                rss.addSubscriptions(newSubscriptions)
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
