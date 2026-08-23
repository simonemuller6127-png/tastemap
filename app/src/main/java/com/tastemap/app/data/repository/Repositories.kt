package com.tastemap.app.data.repository

import com.tastemap.app.data.db.AppDatabase
import com.tastemap.app.data.db.MealRecord
import com.tastemap.app.data.db.RecordTasteCrossRef
import com.tastemap.app.data.db.Shop
import com.tastemap.app.data.db.TasteTag
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** 地图上一个图钉的展示数据：店铺 + 主导口味颜色 + 统计 */
data class ShopPin(
    val shop: Shop,
    val tasteName: String?,
    val colorHex: String,
    val recordCount: Int,
    val avgRating: Double,
)

@Singleton
class TasteRepository @Inject constructor(
    private val db: AppDatabase,
) {
    val tastes: Flow<List<TasteTag>> = db.tasteTagDao().observeAll()

    /** 首次启动播种预置口味（F03） */
    suspend fun ensureSeeded() {
        if (db.tasteTagDao().count() == 0) {
            db.tasteTagDao().insertAll(PresetTastes.ALL)
        }
    }
}

@Singleton
class MapRepository @Inject constructor(
    private val db: AppDatabase,
) {
    /** 图钉颜色 = 该店所有记录里出现最多的口味（主导口味），无记录用中性墨色 */
    fun observePins(): Flow<List<ShopPin>> = combine(
        db.shopDao().observeAll(),
        db.mealRecordDao().observeAll(),
        db.mealRecordDao().observeAllTasteRefs(),
        db.tasteTagDao().observeAll(),
    ) { shops, records, refs, tastes ->
        val tasteById = tastes.associateBy { it.id }
        val refsByRecord = refs.groupBy { it.recordId }
        val recordsByShop = records.groupBy { it.shopId }
        shops.map { shop ->
            val shopRecords = recordsByShop[shop.id].orEmpty()
            val counts = HashMap<Long, Int>()
            shopRecords.forEach { record ->
                refsByRecord[record.id].orEmpty().forEach { ref ->
                    counts[ref.tasteId] = (counts[ref.tasteId] ?: 0) + 1
                }
            }
            val dominant = counts.entries.maxByOrNull { it.value }?.key?.let { tasteById[it] }
            ShopPin(
                shop = shop,
                tasteName = dominant?.name,
                colorHex = dominant?.colorHex ?: PresetTastes.NEUTRAL_PIN_COLOR,
                recordCount = shopRecords.size,
                avgRating = if (shopRecords.isEmpty()) {
                    0.0
                } else {
                    shopRecords.sumOf { it.rating }.toDouble() / shopRecords.size
                },
            )
        }
    }
}

@Singleton
class RecordRepository @Inject constructor(
    private val db: AppDatabase,
) {
    /**
     * 新建记录的最小流（F02）：建店 → 建记录 → 挂口味，单事务。
     * 注意：M0 不做同名/同点店铺去重，M1 做店铺详情时间线（F04）时再合并。
     */
    suspend fun addRecord(
        shopName: String,
        latitude: Double,
        longitude: Double,
        address: String,
        dishName: String,
        rating: Int,
        comment: String,
        tasteIds: List<Long>,
    ): Long = db.withTransaction {
        val shopId = db.shopDao().insert(
            Shop(name = shopName, latitude = latitude, longitude = longitude, address = address),
        )
        val recordId = db.mealRecordDao().insert(
            MealRecord(shopId = shopId, dishName = dishName, rating = rating, comment = comment),
        )
        if (tasteIds.isNotEmpty()) {
            db.mealRecordDao().insertTastes(tasteIds.map { RecordTasteCrossRef(recordId, it) })
        }
        shopId
    }
}
