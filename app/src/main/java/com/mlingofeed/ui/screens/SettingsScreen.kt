package com.mlingofeed.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Circle
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mlingofeed.AppViewModelFactory
import com.mlingofeed.BuildConfig
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.export.ExportManager
import com.mlingofeed.data.settings.DictionaryConfig
import com.mlingofeed.ui.theme.AccentPalettes
import androidx.compose.ui.res.stringResource
import com.mlingofeed.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}, onNavigateToReadingStats: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val vm: SettingsViewModel = viewModel(factory = remember { AppViewModelFactory(app) })
    val clipboardManager = LocalClipboardManager.current

    val dictionaries by vm.dictionaries.collectAsStateWithLifecycle()
    val fontSize by vm.fontSize.collectAsStateWithLifecycle()
    val rssFontSize by vm.rssFontSize.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val themeColor by vm.themeColor.collectAsStateWithLifecycle()
    val readingTimeSeconds by vm.readingTimeSeconds.collectAsStateWithLifecycle()
    val readingSessions by vm.readingSessions.collectAsStateWithLifecycle()
    val dailyGoalMinutes by vm.dailyGoalMinutes.collectAsStateWithLifecycle()

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

    val wordReminderEnabled by vm.wordReminderEnabled.collectAsStateWithLifecycle()
    val readerLineHeight by vm.readerLineHeight.collectAsStateWithLifecycle()
    val readerSerifFont by vm.readerSerifFont.collectAsStateWithLifecycle()
    val readerTtsSpeed by vm.readerTtsSpeed.collectAsStateWithLifecycle()
    val readerTtsVoice by vm.readerTtsVoice.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun setWordReminderEnabled(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.setWordReminderEnabled(enabled)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.saveSettings() }
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
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
                title = stringResource(R.string.theme),
                imageVector = Icons.Default.FormatPaint,
                expanded = vm.expandedSection == "theme",
                onToggle = { vm.toggleSection("theme") }
            ) {
                Column(modifier = Modifier.selectableGroup()) {
                    ThemeRadioOption(stringResource(R.string.follow_system), themeMode == "system") {
                        vm.setThemeMode("system")
                    }
                    ThemeRadioOption(stringResource(R.string.light), themeMode == "light") {
                        vm.setThemeMode("light")
                    }
                    ThemeRadioOption(stringResource(R.string.dark), themeMode == "dark") {
                        vm.setThemeMode("dark")
                    }
                    ThemeRadioOption(stringResource(R.string.eye_care), themeMode == "eyecare") {
                        vm.setThemeMode("eyecare")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    stringResource(R.string.accent_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val accentOptions = listOf(
                        Triple("dynamic", stringResource(R.string.dynamic), null),
                        Triple("blue", stringResource(R.string.blue), AccentPalettes["blue"]?.light),
                        Triple("green", stringResource(R.string.green), AccentPalettes["green"]?.light),
                        Triple("purple", stringResource(R.string.purple), AccentPalettes["purple"]?.light),
                        Triple("orange", stringResource(R.string.orange), AccentPalettes["orange"]?.light),
                        Triple("red", stringResource(R.string.red), AccentPalettes["red"]?.light)
                    )
                    accentOptions.forEach { (key, label, color) ->
                        FilterChip(
                            selected = themeColor == key,
                            onClick = { vm.setThemeColor(key) },
                            label = { Text(label) },
                            leadingIcon = color?.let { tint ->
                                {
                                    Icon(
                                        Icons.Default.Circle,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(
                title = stringResource(R.string.study_reminders),
                imageVector = Icons.Default.Notifications,
                expanded = vm.expandedSection == "reminders",
                onToggle = { vm.toggleSection("reminders") },
                summary = if (wordReminderEnabled) stringResource(R.string.state_on) else stringResource(R.string.state_off)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setWordReminderEnabled(!wordReminderEnabled) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.daily_word_review_reminder), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.notify_me_when_words_are_due_for_review),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = wordReminderEnabled,
                        onCheckedChange = { setWordReminderEnabled(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(
                title = stringResource(R.string.reading_time),
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

                StatRow(stringResource(R.string.total), formatReadingTime(readingTimeSeconds))
                StatRow("Sessions", "$totalSessions")
                StatRow("Today", formatReadingTime(todaySeconds))
                StatRow(stringResource(R.string.average), formatReadingTime(avgDuration))
                StatRow("Longest", formatReadingTime(longestSession))

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.daily_goal_setting),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0, 15, 30, 60, 90).forEach { minutes ->
                        FilterChip(
                            selected = dailyGoalMinutes == minutes,
                            onClick = { vm.setDailyGoalMinutes(minutes) },
                            label = {
                                Text(
                                    text = if (minutes == 0) stringResource(R.string.state_off) else stringResource(R.string.minutes_short, minutes)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    TextButton(onClick = onNavigateToReadingStats) {
                        Text(stringResource(R.string.view_details))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.resetReadingTime() }) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(
                title = stringResource(R.string.font_size),
                imageVector = Icons.Default.FormatSize,
                expanded = vm.expandedSection == "font_size",
                onToggle = { vm.toggleSection("font_size") },
                summary = stringResource(R.string.font_summary, fontSize, rssFontSize.toInt())
            ) {
                // Keep slider drags local and commit once when the drag ends, instead of writing
                // to DataStore on every frame.
                var fontSizeSlider by remember(fontSize) { mutableFloatStateOf(fontSize.toFloat()) }
                Text(stringResource(R.string.web_reader_font), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("A", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(24.dp))
                    Slider(
                        value = fontSizeSlider,
                        onValueChange = { fontSizeSlider = it },
                        onValueChangeFinished = { vm.setFontSize(fontSizeSlider.toInt()) },
                        valueRange = 60f..180f,
                        steps = 5,
                        modifier = Modifier.weight(1f)
                    )
                    Text("A", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(32.dp))
                }
                Text(
                    text = "${fontSizeSlider.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                var rssFontSizeSlider by remember(rssFontSize) { mutableFloatStateOf(rssFontSize) }
                Text(stringResource(R.string.rss_article_font), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("A", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(24.dp))
                    Slider(
                        value = rssFontSizeSlider,
                        onValueChange = { rssFontSizeSlider = it },
                        onValueChangeFinished = { vm.setRssFontSize(rssFontSizeSlider) },
                        valueRange = 13f..24f,
                        steps = 10,
                        modifier = Modifier.weight(1f)
                    )
                    Text("A", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(32.dp))
                }
                Text(
                    text = "${rssFontSizeSlider.toInt()}sp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                var lineHeightSlider by remember(readerLineHeight) { mutableFloatStateOf(readerLineHeight) }
                Text(stringResource(R.string.page_line_height), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1.2", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(28.dp))
                    Slider(
                        value = lineHeightSlider,
                        onValueChange = { lineHeightSlider = it },
                        onValueChangeFinished = { vm.setReaderLineHeight(lineHeightSlider) },
                        valueRange = 1.2f..2.2f,
                        steps = 4,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "%.1f".format(lineHeightSlider),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(28.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.serif_font), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.use_a_serif_typeface_on_web_pages),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = readerSerifFont,
                        onCheckedChange = { vm.setReaderSerifFont(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                var ttsSpeedSlider by remember(readerTtsSpeed) { mutableFloatStateOf(readerTtsSpeed) }
                Text(
                    text = stringResource(R.string.tts_speed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("0.6x", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(34.dp))
                    Slider(
                        value = ttsSpeedSlider,
                        onValueChange = { ttsSpeedSlider = it },
                        onValueChangeFinished = { vm.setReaderTtsSpeed(ttsSpeedSlider) },
                        valueRange = 0.6f..1.6f,
                        steps = 9,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "%.1fx".format(ttsSpeedSlider),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(40.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tts_voice),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = readerTtsVoice == "us",
                        onClick = { vm.setReaderTtsVoice("us") },
                        label = { Text("US") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = readerTtsVoice == "uk",
                        onClick = { vm.setReaderTtsVoice("uk") },
                        label = { Text("UK") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(
                title = stringResource(R.string.dictionaries),
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                expanded = vm.expandedSection == "dictionaries",
                onToggle = { vm.toggleSection("dictionaries") },
                summary = stringResource(R.string.dict_summary, dictionaries.count { it.isEnabled }, dictionaries.size)
            ) {
                dictionaries.forEachIndexed { index, dict ->
                    DictionaryItem(
                        dictionary = dict,
                        onToggle = { enabled -> vm.toggleDictionary(dict, enabled) },
                        onEdit = { vm.requestEditDictionary(dict) },
                        onDelete = { vm.deleteDictionary(dict) },
                        onMoveUp = { vm.moveDictionary(dict, -1) },
                        onMoveDown = { vm.moveDictionary(dict, 1) },
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
                        Text(stringResource(R.string.from_preset))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { vm.openAddDict() }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.add_dictionary))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(
                title = stringResource(R.string.ai_translation),
                imageVector = Icons.Default.Language,
                expanded = vm.expandedSection == "ai",
                onToggle = { vm.toggleSection("ai") }
            ) {
                OutlinedTextField(
                    value = vm.aiUrlInput,
                    onValueChange = { vm.onAiUrlInputChange(it) },
                    label = { Text(stringResource(R.string.ai_api_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.deepseek_https_api_deepseek_com_chat_completions)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.aiKeyInput,
                    onValueChange = { vm.onAiKeyInputChange(it) },
                    label = { Text(stringResource(R.string.api_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(vm.aiKeyInput)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy))
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.aiModelInput,
                    onValueChange = { vm.onAiModelInputChange(it) },
                    label = { Text(stringResource(R.string.model)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = vm.showModelDropdown && vm.modelList.isNotEmpty(),
                    supportingText = { Text(stringResource(R.string.click_to_auto_fetch_models)) },
                    trailingIcon = {
                        if (vm.isLoadingModels) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { vm.fetchModels() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.fetch_models))
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
                    label = { Text(stringResource(R.string.target_language)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.e_g_chinese_japanese_spanish)) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(
                title = stringResource(R.string.data_management),
                imageVector = Icons.Default.Storage,
                expanded = vm.expandedSection == "data",
                onToggle = { vm.toggleSection("data") }
            ) {
                Text(
                    text = stringResource(R.string.export_bookmarks_and_settings_as_a_backup_file),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { exportLauncher.launch("MLingoFeed-backup.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.label_import))
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
                    Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.a_web_reading_app_with_dictionary_lookup_transla),
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
        val existingIds = remember(dictionaries) { dictionaries.map { it.id } }
        PresetDictionaryDialog(
            existingIds = existingIds,
            onAdd = { preset -> vm.addPreset(preset) },
            onDismiss = { vm.dismissPresetDicts() }
        )
    }

    if (vm.showImportConfirm && vm.pendingImportData != null) {
        val data = vm.pendingImportData!!
        AlertDialog(
            onDismissRequest = { vm.dismissImportConfirm() },
            title = { Text(stringResource(R.string.import_data)) },
            text = {
                Text(stringResource(R.string.this_will_replace_all_current_bookmarks_and_sett) +
                     stringResource(R.string.import_preview_bookmarks, data.bookmarks.size) +
                     stringResource(R.string.import_preview_subs, data.subscriptions.size) +
                     stringResource(R.string.import_preview_settings, data.settings.size))
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmImport() }) {
                    Text(stringResource(R.string.label_import))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissImportConfirm() }) {
                    Text(stringResource(R.string.cancel))
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
                    text = dictionary.name.ifEmpty { stringResource(R.string.unnamed) },
                    style = MaterialTheme.typography.titleSmall
                )
                if (!dictionary.isEnabled) {
                    Text(
                        text = stringResource(R.string.disabled),
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
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.edit))
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
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
                    contentDescription = if (expanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
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
    var name by remember(dictionary) { mutableStateOf(dictionary.name) }
    var urlTemplate by remember(dictionary) { mutableStateOf(dictionary.urlTemplate) }
    var cssSelector by remember(dictionary) { mutableStateOf(dictionary.cssSelector) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (dictionary.name.isEmpty()) stringResource(R.string.add_dictionary) else stringResource(R.string.edit_dictionary)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.e_g_youdao_cambridge)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = urlTemplate,
                    onValueChange = { urlTemplate = it },
                    label = { Text(stringResource(R.string.url_template)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.use_word_as_placeholder_e_g_https_dict_youdao_co)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = cssSelector,
                    onValueChange = { cssSelector = it },
                    label = { Text(stringResource(R.string.css_selector_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.e_g_trans_container_content)) }
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
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
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
    val presets = remember {
        listOf(
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
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_dictionaries)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.select_dictionaries_to_add),
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
                            Text(stringResource(R.string.added), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        } else {
                            TextButton(onClick = { onAdd(preset) }) {
                                Text(stringResource(R.string.add))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}