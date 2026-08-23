package com.tastemap.app

import com.tastemap.app.data.backup.BackupData
import com.tastemap.app.data.backup.BackupRecord
import com.tastemap.app.data.backup.BackupRecordTaste
import com.tastemap.app.data.backup.BackupShop
import com.tastemap.app.data.backup.BackupTaste
import com.tastemap.app.data.backup.BackupWishlistTaste
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** .tastemap 备份格式 v2（data.json）序列化回归测试：改字段前先让它红 */
class BackupJsonTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `backup data v2 round trips`() {
        val data = BackupData(
            tastes = listOf(BackupTaste(1, "辣", "#D9482B", true, 1)),
            shops = listOf(
                BackupShop(
                    id = 1, name = "测试面馆", latitude = 30.59276, longitude = 114.30525,
                    address = "测试路1号", amapPoiId = "B0FFI123", stickerPath = null,
                    createdAt = 1_766_000_000_000,
                ),
            ),
            records = listOf(
                BackupRecord(
                    id = 1, shopId = 1, dishName = "热干面", rating = 5, comment = "好吃",
                    recipe = "", tastedAt = 1_766_000_000_000, isOriginalPhoto = true,
                    photos = """["photos/a.jpg","photos/b.jpg"]""",
                ),
            ),
            recordTastes = listOf(BackupRecordTaste(1, 1)),
            wishlist = emptyList(),
            wishlistTastes = listOf(BackupWishlistTaste(1, 1)),
            schedules = emptyList(),
        )
        val text = json.encodeToString(BackupData.serializer(), data)
        val back = json.decodeFromString(BackupData.serializer(), text)
        assertEquals(data, back)
    }

    @Test
    fun `v1 backup without v2 fields still imports`() {
        // v1 格式：没有 amapPoiId/photos/wishlistTastes 等字段，导入时取默认值
        val v1Text = """
            {"tastes":[{"id":1,"name":"辣","colorHex":"#D9482B","isPreset":true,"sortOrder":1}],
             "shops":[{"id":1,"name":"老店","latitude":30.0,"longitude":114.0,"address":"","createdAt":123}],
             "records":[{"id":1,"shopId":1,"dishName":"","rating":4,"comment":"","recipe":"","tastedAt":123,"isOriginalPhoto":true}],
             "recordTastes":[],"wishlist":[],"schedules":[]}
        """.trimIndent()
        val back = json.decodeFromString(BackupData.serializer(), v1Text)
        assertEquals(1, back.shops.size)
        assertEquals(null, back.shops[0].amapPoiId)
        assertEquals("[]", back.records[0].photos)
        assertEquals(0, back.wishlistTastes.size)
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
