package com.tastemap.app.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("tastemap_prefs")

/** 轻量偏好（DataStore，D9）：F18 手绘底图开关、全局字号无级缩放 */
@Singleton
class Prefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val handdrawnMapStyle: Flow<Boolean> = context.dataStore.data.map { it[KEY_HANDDRAWN] ?: false }

    suspend fun setHanddrawnMapStyle(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HANDDRAWN] = enabled }
    }

    /** 全局字号缩放系数（1f = 系统默认；0.85–1.35 无级调节） */
    val fontScale: Flow<Float> = context.dataStore.data.map { it[KEY_FONT_SCALE] ?: 1f }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { it[KEY_FONT_SCALE] = scale }
    }

    companion object {
        val KEY_HANDDRAWN = booleanPreferencesKey("handdrawn_map_style")
        val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
    }
}
