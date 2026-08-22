package com.smellmap.app

import com.smellmap.app.data.backup.BackupData
import com.smellmap.app.data.backup.BackupRecord
import com.smellmap.app.data.backup.BackupRecordTaste
import com.smellmap.app.data.backup.BackupShop
import com.smellmap.app.data.backup.BackupTaste
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** .tastemap 备份格式（data.json）序列化回归测试：改字段前先让它红 */
class BackupJsonTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `backup data round trips`() {
        val data = BackupData(
            tastes = listOf(BackupTaste(1, "辣", "#D9482B", true, 1)),
            shops = listOf(BackupShop(1, "测试面馆", 30.59276, 114.30525, "测试路1号", 1_766_000_000_000)),
            records = listOf(BackupRecord(1, 1, "热干面", 5, "好吃", "", 1_766_000_000_000, true)),
            recordTastes = listOf(BackupRecordTaste(1, 1)),
            wishlist = emptyList(),
            schedules = emptyList(),
        )
        val text = json.encodeToString(BackupData.serializer(), data)
        val back = json.decodeFromString(BackupData.serializer(), text)
        assertEquals(data, back)
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val text = """
            {"tastes":[],"shops":[],"records":[],"recordTastes":[],"wishlist":[],"schedules":[],"futureField":123}
        """.trimIndent()
        val back = json.decodeFromString(BackupData.serializer(), text)
        assertEquals(0, back.shops.size)
    }
}
