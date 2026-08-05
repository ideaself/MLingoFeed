package com.mlingofeed.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

        private fun defaultDictionaries(): String {
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
        val json = prefs[DICTIONARIES] ?: defaultDictionaries()
        parseDictionaries(json)
    }

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

    suspend fun setDictionaries(dicts: List<DictionaryConfig>) {
        context.dataStore.edit { prefs ->
            prefs[DICTIONARIES] = dictionariesToJson(dicts)
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
            val json = prefs[READING_SESSIONS] ?: "[]"
            val array = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
            if (array.length() >= 50) {
                array.remove(0)
            }
            array.put("${System.currentTimeMillis()}:$durationSeconds")
            prefs[READING_SESSIONS] = array.toString()
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
    }

    suspend fun getAllSettings(): Map<String, String> {
        val prefs = context.dataStore.data.first()
        return mapOf(
            "dictionaries" to (prefs[DICTIONARIES] ?: defaultDictionaries()),
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

    suspend fun importSettings(settings: Map<String, String>) {
        context.dataStore.edit { prefs ->
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