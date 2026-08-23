package com.tastemap.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 口味标签（PRD §7 / F03）：色值与 AGENTS.md 色值表同源 */
@Entity(tableName = "taste_tags", indices = [Index(value = ["name"], unique = true)])
data class TasteTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val isPreset: Boolean = false,
    val sortOrder: Int = 0,
)

/** 店铺（F01/F02）：口味不落库，由该店记录的口味推导（主导口味） */
@Entity(tableName = "shops", indices = [Index("name")])
data class Shop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** 用餐记录（F02）：照片字段 M1 加列（Room migration），M0 先跑通最小记录流 */
@Entity(
    tableName = "meal_records",
    indices = [Index("shopId")],
    foreignKeys = [
        ForeignKey(
            entity = Shop::class,
            parentColumns = ["id"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MealRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val dishName: String = "",
    val rating: Int = 3,                 // 1-5
    val comment: String = "",
    val recipe: String = "",             // 做法备注，M5 升级为结构化菜谱
    val tastedAt: Long = System.currentTimeMillis(),
    val isOriginalPhoto: Boolean = true, // EXIF 原图角标（D5），M1 接照片后生效
)

/** 记录 ↔ 口味 多对多 */
@Entity(
    tableName = "record_tastes",
    primaryKeys = ["recordId", "tasteId"],
    indices = [Index("tasteId")], // 外键列建索引，避免父表修改时全表扫描
    foreignKeys = [
        ForeignKey(
            entity = MealRecord::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TasteTag::class,
            parentColumns = ["id"],
            childColumns = ["tasteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RecordTasteCrossRef(
    val recordId: Long,
    val tasteId: Long,
)

/** 想吃清单（F09，M1 做界面，M0 先建表） */
@Entity(tableName = "wishlist")
data class WishlistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 美食日程（F08，M1 做界面，M0 先建表）：早/午/晚/夜宵四餐格 */
@Entity(tableName = "schedules", indices = [Index("date")])
data class ScheduleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,          // yyyy-MM-dd
    val mealSlot: String,      // 早餐/午餐/晚餐/夜宵
    val shopId: Long? = null,
    val note: String = "",
    val reminderOn: Boolean = false,
)
