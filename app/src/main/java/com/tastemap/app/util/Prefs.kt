package com.tastemap.app.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("tastemap_prefs")

/** 轻量偏好（DataStore，D9）。目前只有 F18 手绘底图开关。 */
@Singleton
class Prefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val handdrawnMapStyle: Flow<Boolean> = context.dataStore.data.map { it[KEY_HANDDRAWN] ?: false }

    suspend fun setHanddrawnMapStyle(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HANDDRAWN] = enabled }
    }

    companion object {
        val KEY_HANDDRAWN = booleanPreferencesKey("handdrawn_map_style")
    }
}
