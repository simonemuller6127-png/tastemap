package com.tastemap.app.data.backup

import kotlinx.serialization.Serializable

/** .tastemap 备份的可序列化模型（D4：zip 内 manifest.json + data.json + photos/） */

@Serializable
data class BackupManifest(
    val app: String = "tastemap",
    val formatVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class BackupData(
    val tastes: List<BackupTaste>,
    val shops: List<BackupShop>,
    val records: List<BackupRecord>,
    val recordTastes: List<BackupRecordTaste>,
    val wishlist: List<BackupWishlist>,
    val schedules: List<BackupSchedule>,
)

@Serializable
data class BackupTaste(
    val id: Long,
    val name: String,
    val colorHex: String,
    val isPreset: Boolean,
    val sortOrder: Int,
)

@Serializable
data class BackupShop(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val createdAt: Long,
)

@Serializable
data class BackupRecord(
    val id: Long,
    val shopId: Long,
    val dishName: String,
    val rating: Int,
    val comment: String,
    val recipe: String,
    val tastedAt: Long,
    val isOriginalPhoto: Boolean,
)

@Serializable
data class BackupRecordTaste(val recordId: Long, val tasteId: Long)

@Serializable
data class BackupWishlist(
    val id: Long,
    val text: String,
    val note: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long,
)

@Serializable
data class BackupSchedule(
    val id: Long,
    val date: String,
    val mealSlot: String,
    val shopId: Long? = null,
    val note: String,
    val reminderOn: Boolean,
)
