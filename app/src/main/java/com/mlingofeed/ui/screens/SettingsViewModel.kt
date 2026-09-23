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
import com.mlingofeed.data.settings.AiProviderConfig
import com.mlingofeed.data.settings.DictionaryConfig
import com.mlingofeed.data.work.WordReviewWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mlingofeed.R

class SettingsViewModel(private val app: WebReaderApp) : ViewModel() {

    val dictionaries = app.settingsManager.dictionaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val fontSize = app.settingsManager.fontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100)
    val rssFontSize = app.settingsManager.rssFontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 17f)
    val themeMode = app.settingsManager.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")
    val themeColor = app.settingsManager.themeColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "dynamic")
    val readingTimeSeconds = app.settingsManager.readingTimeSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val readingSessions = app.settingsManager.readingSessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val wordReminderEnabled = app.settingsManager.wordReviewReminderEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val dailyGoalMinutes = app.settingsManager.dailyReadingGoalMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val readerLineHeight = app.settingsManager.readerLineHeight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.6f)
    val readerSerifFont = app.settingsManager.readerSerifFont.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val readerTtsSpeed = app.settingsManager.readerTtsSpeed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)
    val readerTtsVoice = app.settingsManager.readerTtsVoice.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "us")

    var targetLangInput by mutableStateOf("")
        private set

    val aiProviders = app.settingsManager.aiProviders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeAiProviderId = app.settingsManager.aiActiveProviderId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    // Draft of the provider currently being added/edited in the dialog (null id = adding).
    var showProviderDialog by mutableStateOf(false)
        private set
    var editingProviderId by mutableStateOf<String?>(null)
        private set
    var providerNameInput by mutableStateOf("")
        private set
    var providerUrlInput by mutableStateOf("")
        private set
    var providerKeyInput by mutableStateOf("")
        private set
    var providerModelInput by mutableStateOf("")
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
            targetLangInput = settings["translate_target_lang"] ?: "Chinese"
        }
    }

    fun onTargetLangInputChange(value: String) {
        targetLangInput = value
    }

    fun onProviderNameInputChange(value: String) {
        providerNameInput = value
    }

    fun onProviderUrlInputChange(value: String) {
        providerUrlInput = value
    }

    fun onProviderKeyInputChange(value: String) {
        providerKeyInput = value
    }

    fun onProviderModelInputChange(value: String) {
        providerModelInput = value
    }

    fun openAddProvider() {
        editingProviderId = null
        providerNameInput = ""
        providerUrlInput = ""
        providerKeyInput = ""
        providerModelInput = ""
        resetModelSuggestions()
        showProviderDialog = true
    }

    fun openEditProvider(provider: AiProviderConfig) {
        editingProviderId = provider.id
        providerNameInput = provider.name
        providerUrlInput = provider.apiBaseUrl
        providerKeyInput = provider.apiKey
        providerModelInput = provider.model
        resetModelSuggestions()
        showProviderDialog = true
    }

    fun dismissProviderDialog() {
        showProviderDialog = false
        editingProviderId = null
    }

    /** Saves the dialog draft; a newly added provider becomes the active one immediately. */
    fun saveProvider() {
        val name = providerNameInput.trim()
        val url = providerUrlInput.trim()
        if (name.isEmpty() || url.isEmpty()) return
        val editingId = editingProviderId
        val key = providerKeyInput.trim()
        val model = providerModelInput.trim()
        viewModelScope.launch {
            if (editingId == null) {
                val newProvider = AiProviderConfig(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    apiBaseUrl = url,
                    apiKey = key,
                    model = model
                )
                app.settingsManager.updateAiProviders { it + newProvider }
                app.settingsManager.setActiveAiProvider(newProvider.id)
            } else {
                app.settingsManager.updateAiProviders { providers ->
                    providers.map {
                        if (it.id == editingId) {
                            it.copy(name = name, apiBaseUrl = url, apiKey = key, model = model)
                        } else {
                            it
                        }
                    }
                }
            }
            dismissProviderDialog()
        }
    }

    fun deleteProvider(provider: AiProviderConfig) {
        viewModelScope.launch {
            app.settingsManager.updateAiProviders { providers ->
                providers.filterNot { it.id == provider.id }
            }
        }
    }

    fun selectAiProvider(id: String) {
        viewModelScope.launch { app.settingsManager.setActiveAiProvider(id) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            app.settingsManager.setTranslateTargetLang(targetLangInput)
            Toast.makeText(app, app.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetModelSuggestions() {
        modelList = emptyList()
        showModelDropdown = false
    }

    fun fetchModels() {
        if (providerKeyInput.isBlank()) {
            Toast.makeText(app, app.getString(R.string.please_enter_api_key_first), Toast.LENGTH_SHORT).show()
            return
        }
        isLoadingModels = true
        viewModelScope.launch {
            try {
                val models = app.chatRepository.fetchModels(providerUrlInput, providerKeyInput)
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
        providerModelInput = model
        showModelDropdown = false
    }

    fun toggleSection(section: String) {
        expandedSection = if (expandedSection == section) null else section
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { app.settingsManager.setThemeMode(mode) }
    }

    fun setThemeColor(color: String) {
        viewModelScope.launch { app.settingsManager.setThemeColor(color) }
    }

    fun setFontSize(size: Int) {
        viewModelScope.launch { app.settingsManager.setFontSize(size) }
    }

    fun setRssFontSize(size: Float) {
        viewModelScope.launch { app.settingsManager.setRssFontSize(size) }
    }

    fun setReaderLineHeight(value: Float) {
        viewModelScope.launch { app.settingsManager.setReaderLineHeight(value) }
    }

    fun setReaderSerifFont(enabled: Boolean) {
        viewModelScope.launch { app.settingsManager.setReaderSerifFont(enabled) }
    }

    fun setReaderTtsSpeed(speed: Float) {
        viewModelScope.launch { app.settingsManager.setReaderTtsSpeed(speed) }
    }

    fun setReaderTtsVoice(voice: String) {
        viewModelScope.launch { app.settingsManager.setReaderTtsVoice(voice) }
    }

    fun resetReadingTime() {
        viewModelScope.launch {
            app.settingsManager.resetReadingTime()
            Toast.makeText(app, app.getString(R.string.reading_time_reset), Toast.LENGTH_SHORT).show()
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

    fun setDailyGoalMinutes(minutes: Int) {
        viewModelScope.launch { app.settingsManager.setDailyReadingGoalMinutes(minutes) }
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
            Toast.makeText(app, if (ok) app.getString(R.string.export_successful) else app.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { ExportManager.importFromJson(app, uri) }
            if (data == null) {
                Toast.makeText(app, app.getString(R.string.import_failed_invalid_file), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(app, app.getString(R.string.import_successful), Toast.LENGTH_SHORT).show()
        }
        showImportConfirm = false
        pendingImportData = null
    }
}
