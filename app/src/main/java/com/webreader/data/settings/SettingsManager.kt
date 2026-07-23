package com.webreader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val DICTIONARY_URL = stringPreferencesKey("dictionary_url")
        val DICTIONARY_NAME = stringPreferencesKey("dictionary_name")
        val AI_API_URL = stringPreferencesKey("ai_api_url")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val TRANSLATE_TARGET_LANG = stringPreferencesKey("translate_target_lang")
    }

    val dictionaryUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DICTIONARY_URL] ?: "https://dict.youdao.com/jsonapi?jsonversion=2&client=mobile&dicts=%7B%22count%22%3A99%2C%22dicts%22%3A%5B%5B%22ec%22%2C%22ce%22%2C%22newcj%22%2C%22newjc%22%2C%22kc%22%2C%22ck%22%2C%22fc%22%2C%22cf%22%2C%22multle%22%2C%22jtj%22%2C%22pic_dict%22%2C%22tc%22%2C%22ce_new%22%2C%22ec_new%22%2C%22kbbig%22%2C%22simple%22%2C%22wordform%22%2C%22wikipedia_digest%22%2C%22ee%22%2C%22phrs%22%2C%22syno%22%2C%22collins%22%2C%22wordvideo%22%2C%22en2en%22%2C%22etym%22%2C%22uling%22%2C%22blng_sents_part%22%2C%22hh%22%2C%22rel_word%22%2C%22special%22%2C%22langs%22%2C%22web_trans%22%2C%22fanyi%22%2C%22sgthree%22%2C%22auth_dict%22%2C%22ned%22%2C%22quiz_dict%22%2C%22meikao%22%2C%22bcc%22%2C%22longman%22%2C%22oxford%22%2C%22pukao%22%2C%22webster%22%2C%22eepc%22%2C%22cet4%22%2C%22cet6%22%2C%22ee_exp%22%2C%22xc%22%2C%22ja2zh%22%2C%22jc2zh%22%2C%22jp2zh%22%2C%22kc2zh%22%5D%5D%7D&q="
    }

    val dictionaryName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DICTIONARY_NAME] ?: "Youdao"
    }

    val aiApiUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AI_API_URL] ?: "https://api.deepseek.com/chat/completions"
    }

    val aiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AI_API_KEY] ?: ""
    }

    val aiModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AI_MODEL] ?: "deepseek-chat"
    }

    val translateTargetLang: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[TRANSLATE_TARGET_LANG] ?: "Chinese"
    }

    suspend fun setDictionaryUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[DICTIONARY_URL] = url }
    }

    suspend fun setDictionaryName(name: String) {
        context.dataStore.edit { prefs -> prefs[DICTIONARY_NAME] = name }
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
}
