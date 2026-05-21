package dev.esemi.zmvoice.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "zmvoice_settings")

data class Settings(
    val zenmoneyToken: String,
    val anthropicToken: String,
    val defaultAccountId: String,
    val zenDiffJson: String,
    val zenDiffFetchedAt: Long,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val zen = stringPreferencesKey("zenmoney_token")
        val anth = stringPreferencesKey("anthropic_token")
        val acc = stringPreferencesKey("default_account_id")
        val diff = stringPreferencesKey("zen_diff_json")
        val diffAt = longPreferencesKey("zen_diff_fetched_at")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun setZenmoneyToken(value: String) = context.dataStore.edit { it[Keys.zen] = value }
    suspend fun setAnthropicToken(value: String) = context.dataStore.edit { it[Keys.anth] = value }
    suspend fun setDefaultAccount(id: String) = context.dataStore.edit { it[Keys.acc] = id }
    suspend fun setZenDiff(json: String, fetchedAt: Long) = context.dataStore.edit {
        it[Keys.diff] = json
        it[Keys.diffAt] = fetchedAt
    }

    private fun Preferences.toSettings() = Settings(
        zenmoneyToken = this[Keys.zen].orEmpty(),
        anthropicToken = this[Keys.anth].orEmpty(),
        defaultAccountId = this[Keys.acc].orEmpty(),
        zenDiffJson = this[Keys.diff].orEmpty(),
        zenDiffFetchedAt = this[Keys.diffAt] ?: 0L,
    )
}
