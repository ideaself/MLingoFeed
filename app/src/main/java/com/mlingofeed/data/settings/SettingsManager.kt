package com.mlingofeed.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class DictionaryConfig(
    val id: String,
    val name: String,
    val urlTemplate: String,
    val cssSelector: String,
    val isEnabled: Boolean = true
)

class SettingsManager(private val context: Context) {

    companion object {
        val DICTIONARIES = stringPreferencesKey("dictionaries")
        val AI_API_URL = stringPreferencesKey("ai_api_url")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val TRANSLATE_TARGET_LANG = stringPreferencesKey("translate_target_lang")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val RSS_FONT_SIZE = stringPreferencesKey("rss_font_size")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READING_TIME_SECONDS = stringPreferencesKey("reading_time_seconds")
        val READING_SESSIONS = stringPreferencesKey("reading_sessions")
        val WORD_REVIEW_REMINDER = stringPreferencesKey("word_review_reminder")
        val RSS_SYNC_INTERVAL_HOURS = stringPreferencesKey("rss_sync_interval_hours")
        val READER_DESKTOP_MODE = stringPreferencesKey("reader_desktop_mode")
        val READER_BLOCK_IMAGES = stringPreferencesKey("reader_block_images")
        val READER_LINE_HEIGHT = stringPreferencesKey("reader_line_height")
        val READER_SERIF_FONT = stringPreferencesKey("reader_serif_font")
        val READER_HIGHLIGHT_WORDS = stringPreferencesKey("reader_highlight_words")
        val READER_TABS = stringPreferencesKey("reader_tabs")
        val READER_SELECTED_TAB = stringPreferencesKey("reader_selected_tab")

        private const val MAX_READING_SESSIONS = 500
        private const val SESSION_RETENTION_DAYS = 60L

        private val defaultDictionariesJson: String by lazy { createDefaultDictionaries() }

        private fun createDefaultDictionaries(): String {
            val list = JSONArray()
            val youdao = JSONObject().apply {
                put("id", "youdao")
                put("name", "Youdao")
                put("urlTemplate", "https://dict.youdao.com/jsonapi?jsonversion=2&client=mobile&dicts=%7B%22count%22%3A99%2C%22dicts%22%3A%5B%5B%22ec%22%2C%22ce%22%2C%22newcj%22%2C%22newjc%22%2C%22kc%22%2C%22ck%22%2C%22fc%22%2C%22cf%22%2C%22multle%22%2C%22jtj%22%2C%22pic_dict%22%2C%22tc%22%2C%22ce_new%22%2C%22ec_new%22%2C%22kbbig%22%2C%22simple%22%2C%22wordform%22%2C%22wikipedia_digest%22%2C%22ee%22%2C%22phrs%22%2C%22syno%22%2C%22collins%22%2C%22wordvideo%22%2C%22en2en%22%2C%22etym%22%2C%22uling%22%2C%22blng_sents_part%22%2C%22hh%22%2C%22rel_word%22%2C%22special%22%2C%22langs%22%2C%22web_trans%22%2C%22fanyi%22%2C%22sgthree%22%2C%22auth_dict%22%2C%22ned%22%2C%22quiz_dict%22%2C%22meikao%22%2C%22bcc%22%2C%22longman%22%2C%22oxford%22%2C%22pukao%22%2C%22webster%22%2C%22eepc%22%2C%22cet4%22%2C%22cet6%22%2C%22ee_exp%22%2C%22xc%22%2C%22ja2zh%22%2C%22jc2zh%22%2C%22jp2zh%22%2C%22kc2zh%22%5D%5D%7D&q={word}")
                put("cssSelector", "")
                put("isEnabled", true)
            }
            list.put(youdao)
            return list.toString()
        }
    }

    val dictionaries: Flow<List<DictionaryConfig>> = context.dataStore.data.map { prefs ->
        val json = prefs[DICTIONARIES] ?: defaultDictionariesJson
        parseDictionaries(json)
    }.flowOn(Dispatchers.Default)

    private fun parseDictionaries(json: String): List<DictionaryConfig> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                DictionaryConfig(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    name = obj.optString("name", "Dictionary"),
                    urlTemplate = obj.optString("urlTemplate", ""),
                    cssSelector = obj.optString("cssSelector", ""),
                    isEnabled = obj.optBoolean("isEnabled", true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun dictionariesToJson(dicts: List<DictionaryConfig>): String {
        val array = JSONArray()
        dicts.forEach { dict ->
            val obj = JSONObject().apply {
                put("id", dict.id)
                put("name", dict.name)
                put("urlTemplate", dict.urlTemplate)
                put("cssSelector", dict.cssSelector)
                put("isEnabled", dict.isEnabled)
            }
            array.put(obj)
        }
        return array.toString()
    }

    val aiApiUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AI_API_URL] ?: "https://api.deepseek.com/chat/completions"
    }

    val aiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AI_API_KEY] ?: ""
    }

    val aiModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AI_MODEL] ?: "deepseek-v4-flash"
    }

    val translateTargetLang: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[TRANSLATE_TARGET_LANG] ?: "Chinese"
    }

    val fontSize: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[FONT_SIZE]?.toIntOrNull() ?: 100
    }

    val rssFontSize: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[RSS_FONT_SIZE]?.toFloatOrNull() ?: 17f
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: "system"
    }

    val readingTimeSeconds: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[READING_TIME_SECONDS]?.toLongOrNull() ?: 0L
    }

    val wordReviewReminderEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[WORD_REVIEW_REMINDER] == "true"
    }

    val rssSyncIntervalHours: Flow<Long> = context.dataStore.data.map { prefs ->
        (prefs[RSS_SYNC_INTERVAL_HOURS]?.toLongOrNull() ?: 1L).coerceIn(1L, 24L)
    }

    val readerDesktopMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_DESKTOP_MODE] == "true"
    }

    val readerBlockImages: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_BLOCK_IMAGES] == "true"
    }

    val readerHighlightWords: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_HIGHLIGHT_WORDS] == "true"
    }

    val readerLineHeight: Flow<Float> = context.dataStore.data.map { prefs ->
        (prefs[READER_LINE_HEIGHT]?.toFloatOrNull() ?: 1.6f).coerceIn(1.2f, 2.2f)
    }

    val readerSerifFont: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_SERIF_FONT] == "true"
    }

    /**
     * Applies [transform] to the currently persisted dictionaries inside the same DataStore
     * transaction, so concurrent edits cannot overwrite each other with a stale list.
     */
    suspend fun updateDictionaries(transform: (List<DictionaryConfig>) -> List<DictionaryConfig>) {
        context.dataStore.edit { prefs ->
            val current = parseDictionaries(prefs[DICTIONARIES] ?: defaultDictionariesJson)
            prefs[DICTIONARIES] = dictionariesToJson(transform(current))
        }
    }

    suspend fun setAiApiUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[AI_API_URL] = url }
    }

    suspend fun setAiApiKey(key: String) {
        context.dataStore.edit { prefs -> prefs[AI_API_KEY] = key }
    }

    suspend fun setAiModel(model: String) {
        context.dataStore.edit { prefs -> prefs[AI_MODEL] = model }
    }

    suspend fun setTranslateTargetLang(lang: String) {
        context.dataStore.edit { prefs -> prefs[TRANSLATE_TARGET_LANG] = lang }
    }

    suspend fun setFontSize(size: Int) {
        context.dataStore.edit { prefs -> prefs[FONT_SIZE] = size.toString() }
    }

    suspend fun setRssFontSize(size: Float) {
        context.dataStore.edit { prefs -> prefs[RSS_FONT_SIZE] = size.toString() }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[THEME_MODE] = mode }
    }

    suspend fun setWordReviewReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[WORD_REVIEW_REMINDER] = enabled.toString() }
    }

    suspend fun setRssSyncIntervalHours(hours: Long) {
        context.dataStore.edit { prefs -> prefs[RSS_SYNC_INTERVAL_HOURS] = hours.toString() }
    }

    suspend fun setReaderDesktopMode(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[READER_DESKTOP_MODE] = enabled.toString() }
    }

    suspend fun setReaderBlockImages(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[READER_BLOCK_IMAGES] = enabled.toString() }
    }

    suspend fun setReaderHighlightWords(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[READER_HIGHLIGHT_WORDS] = enabled.toString() }
    }

    suspend fun setReaderLineHeight(lineHeight: Float) {
        context.dataStore.edit { prefs -> prefs[READER_LINE_HEIGHT] = lineHeight.toString() }
    }

    suspend fun setReaderSerifFont(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[READER_SERIF_FONT] = enabled.toString() }
    }

    suspend fun setReaderTabs(tabsJson: String, selectedIndex: Int) {
        context.dataStore.edit { prefs ->
            prefs[READER_TABS] = tabsJson
            prefs[READER_SELECTED_TAB] = selectedIndex.toString()
        }
    }

    suspend fun getReaderTabs(): Pair<String, Int> {
        val prefs = context.dataStore.data.first()
        return (prefs[READER_TABS] ?: "[]") to (prefs[READER_SELECTED_TAB]?.toIntOrNull() ?: 0)
    }

    suspend fun addReadingTime(seconds: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[READING_TIME_SECONDS]?.toLongOrNull() ?: 0L
            prefs[READING_TIME_SECONDS] = (current + seconds).toString()
        }
    }

    suspend fun resetReadingTime() {
        context.dataStore.edit { prefs ->
            prefs[READING_TIME_SECONDS] = "0"
            prefs[READING_SESSIONS] = "[]"
        }
    }

    suspend fun addReadingSession(durationSeconds: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[READING_TIME_SECONDS]?.toLongOrNull() ?: 0L
            prefs[READING_TIME_SECONDS] = (current + durationSeconds).toString()

            val cutoff = System.currentTimeMillis() - SESSION_RETENTION_DAYS * 24 * 60 * 60 * 1000
            val sessions = mutableListOf<String>()
            try {
                val array = JSONArray(prefs[READING_SESSIONS] ?: "[]")
                for (i in 0 until array.length()) {
                    val entry = array.optString(i)
                    val timestamp = entry.substringBefore(':').toLongOrNull() ?: continue
                    if (timestamp >= cutoff) sessions.add(entry)
                }
            } catch (_: Exception) {
                // Corrupt history: start over rather than fail the write.
            }
            while (sessions.size >= MAX_READING_SESSIONS) {
                sessions.removeAt(0)
            }
            sessions.add("${System.currentTimeMillis()}:$durationSeconds")
            prefs[READING_SESSIONS] = JSONArray(sessions).toString()
        }
    }

    val readingSessions: Flow<List<Pair<Long, Long>>> = context.dataStore.data.map { prefs ->
        val json = prefs[READING_SESSIONS] ?: "[]"
        try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val parts = array.getString(i).split(":")
                Pair(parts[0].toLong(), parts[1].toLong())
            }
        } catch (_: Exception) {
            emptyList()
        }
    }.flowOn(Dispatchers.Default)

    suspend fun getAllSettings(): Map<String, String> {
        val prefs = context.dataStore.data.first()
        return mapOf(
            "dictionaries" to (prefs[DICTIONARIES] ?: defaultDictionariesJson),
            "ai_api_url" to (prefs[AI_API_URL] ?: "https://api.deepseek.com/chat/completions"),
            "ai_api_key" to (prefs[AI_API_KEY] ?: ""),
            "ai_model" to (prefs[AI_MODEL] ?: "deepseek-v4-flash"),
            "translate_target_lang" to (prefs[TRANSLATE_TARGET_LANG] ?: "Chinese"),
            "font_size" to (prefs[FONT_SIZE]?.toString() ?: "100"),
            "rss_font_size" to (prefs[RSS_FONT_SIZE]?.toString() ?: "17"),
            "theme_mode" to (prefs[THEME_MODE] ?: "system"),
            "reading_time_seconds" to (prefs[READING_TIME_SECONDS]?.toString() ?: "0")
        )
    }

    /** Settings that are safe to write to an export file: the AI key is deliberately excluded. */
    suspend fun getExportableSettings(): Map<String, String> =
        getAllSettings().filterKeys { it != "ai_api_key" }

    suspend fun importSettings(settings: Map<String, String>) {        context.dataStore.edit { prefs ->
            settings["dictionaries"]?.let { prefs[DICTIONARIES] = it }
            settings["ai_api_url"]?.let { prefs[AI_API_URL] = it }
            settings["ai_api_key"]?.let { prefs[AI_API_KEY] = it }
            settings["ai_model"]?.let { prefs[AI_MODEL] = it }
            settings["translate_target_lang"]?.let { prefs[TRANSLATE_TARGET_LANG] = it }
            settings["font_size"]?.let { prefs[FONT_SIZE] = it }
            settings["rss_font_size"]?.let { prefs[RSS_FONT_SIZE] = it }
            settings["theme_mode"]?.let { prefs[THEME_MODE] = it }
            settings["reading_time_seconds"]?.let { prefs[READING_TIME_SECONDS] = it }
        }
    }
}