package com.routine.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(private val context: Context) {

    companion object {
        val KEY_API_KEY = stringPreferencesKey("claude_api_key")
        val KEY_HOME_LOCATION = stringPreferencesKey("home_location_label")
    }

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { it[KEY_API_KEY] ?: "" }

    suspend fun getApiKey(): String = apiKeyFlow.first()

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[KEY_API_KEY] = key }
    }

    suspend fun saveHomeLocation(label: String) {
        context.dataStore.edit { it[KEY_HOME_LOCATION] = label }
    }

    val homeLocationFlow: Flow<String> = context.dataStore.data.map { it[KEY_HOME_LOCATION] ?: "Home" }
}
