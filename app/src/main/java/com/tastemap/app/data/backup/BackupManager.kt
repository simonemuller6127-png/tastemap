package com.tastemap.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.tastemap.app.data.db.AppDatabase
import com.tastemap.app.data.db.MealRecord
import com.tastemap.app.data.db.RecordTasteCrossRef
import com.tastemap.app.data.db.ScheduleItem
import com.tastemap.app.data.db.Shop
import com.tastemap.app.data.db.TasteTag
import com.tastemap.app.data.db.WishlistItem
import com.tastemap.app.data.db.WishlistTasteCrossRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val tastes: Int, val shops: Int, val records: Int)

/**
 * .tastemap 备份（F06 / SPEC D4）：
 * zip 结构 = manifest.json（版本）+ data.json（全量数据）+ photos/（M1 起写照片文件）。
 * 导入为整库替换，保持 id 不变，保证记录-口味关联不断。
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true   // 向前兼容：新版本读旧备份
        encodeDefaults = true
    }

    suspend fun exportTo(uri: Uri): Unit = withContext(Dispatchers.IO) {
        val data = BackupData(
            tastes = db.tasteTagDao().getAll().map {
                BackupTaste(it.id, it.name, it.colorHex, it.isPreset, it.sortOrder)
            },
            shops = db.shopDao().getAll().map {
                BackupShop(it.id, it.name, it.latitude, it.longitude, it.address, it.amapPoiId, it.meituanPoiId, it.stickerPath, it.createdAt)
            },
            records = db.mealRecordDao().getAll().map {
                BackupRecord(it.id, it.shopId, it.dishName, it.rating, it.comment, it.recipe, it.tastedAt, it.isOriginalPhoto, it.photos, it.stickerPath)
            },
            recordTastes = db.mealRecordDao().getAllTasteRefs().map {
                BackupRecordTaste(it.recordId, it.tasteId)
            },
            wishlist = db.wishlistDao().getAll().map {
                BackupWishlist(it.id, it.text, it.note, it.latitude, it.longitude, it.createdAt)
            },
            wishlistTastes = db.wishlistDao().getAllTasteRefs().map {
                BackupWishlistTaste(it.wishlistId, it.tasteId)
            },
            schedules = db.scheduleDao().getAll().map {
                BackupSchedule(it.id, it.date, it.mealSlot, it.shopId, it.note, it.reminderOn)
            },
        )

        val out = context.contentResolver.openOutputStream(uri)
            ?: error("无法打开导出文件")
        out.use {
            ZipOutputStream(it.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(json.encodeToString(BackupManifest.serializer(), BackupManifest()).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
                zip.closeEntry()
                // photos/ 目录 M1 接入照片后写入
            }
        }
    }

    suspend fun importFrom(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        var dataJson: String? = null
        val input = context.contentResolver.openInputStream(uri)
            ?: error("无法打开备份文件")
        input.use {
            ZipInputStream(it.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "data.json") {
                        dataJson = zip.readBytes().decodeToString()
                    }
                    entry = zip.nextEntry
                }
            }
        }
        val data = json.decodeFromString(
            BackupData.serializer(),
            dataJson ?: error("备份缺少 data.json"),
        )

        db.withTransaction {
            // 清库顺序：先子后父（关联表随父表级联删除）
            db.mealRecordDao().deleteAll()
            db.shopDao().deleteAll()
            db.wishlistDao().deleteAllTastes()
            db.wishlistDao().deleteAll()
            db.scheduleDao().deleteAll()
            db.tasteTagDao().deleteAll()

            db.tasteTagDao().insertAll(
                data.tastes.map { TasteTag(it.id, it.name, it.colorHex, it.isPreset, it.sortOrder) },
            )
            data.shops.forEach {
                db.shopDao().insert(
                    Shop(it.id, it.name, it.latitude, it.longitude, it.address, it.amapPoiId, it.meituanPoiId, it.stickerPath, it.createdAt),
                )
            }
            data.records.forEach {
                db.mealRecordDao().insert(
                    MealRecord(it.id, it.shopId, it.dishName, it.rating, it.comment, it.recipe, it.tastedAt, it.isOriginalPhoto, it.photos, it.stickerPath),
                )
            }
            if (data.recordTastes.isNotEmpty()) {
                db.mealRecordDao().insertTastes(
                    data.recordTastes.map { RecordTasteCrossRef(it.recordId, it.tasteId) },
                )
            }
            data.wishlist.forEach {
                db.wishlistDao().insert(WishlistItem(it.id, it.text, it.note, it.latitude, it.longitude, it.createdAt))
            }
            if (data.wishlistTastes.isNotEmpty()) {
                db.wishlistDao().insertTastes(
                    data.wishlistTastes.map { WishlistTasteCrossRef(it.wishlistId, it.tasteId) },
                )
            }
            data.schedules.forEach {
                db.scheduleDao().insert(ScheduleItem(it.id, it.date, it.mealSlot, it.shopId, it.note, it.reminderOn))
            }
        }
        ImportResult(data.tastes.size, data.shops.size, data.records.size)
    }
}
