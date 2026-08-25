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

/** 地图上一个贴纸/图钉的展示数据：店铺 + 主导口味颜色 + 统计 + 照片贴纸源（D17） */
data class ShopPin(
    val shop: Shop,
    val tasteName: String?,
    val colorHex: String,
    val recordCount: Int,
    val avgRating: Double,
    val firstPhotoPath: String?, // 最近一条记录的第一张照片（贴纸渲染用）
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

    /** F03 自定义口味：追加在预置之后，返回落库后的口味（含 id） */
    suspend fun addCustom(name: String, colorHex: String): TasteTag? {
        if (name.isBlank()) return null
        val next = (db.tasteTagDao().getAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
        db.tasteTagDao().insertAll(
            listOf(TasteTag(name = name.trim(), colorHex = colorHex, isPreset = false, sortOrder = next)),
        )
        return db.tasteTagDao().getAll().firstOrNull { it.name == name.trim() }
    }

    /** 只允许删除自定义口味，预置口味是全局色板契约的一部分 */
    suspend fun removeCustom(id: Long) {
        val tag = db.tasteTagDao().getById(id) ?: return
        if (!tag.isPreset) db.tasteTagDao().deleteById(id)
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
            // 记录按 tastedAt 倒序，第一条带照片的就是贴纸源
            val firstPhoto = shopRecords.firstOrNull { record ->
                com.tastemap.app.data.photo.PhotoJson.decode(record.photos).isNotEmpty()
            }?.let { com.tastemap.app.data.photo.PhotoJson.decode(it.photos).first() }
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
                firstPhotoPath = firstPhoto,
            )
        }
    }
}

@Singleton
class RecordRepository @Inject constructor(
    private val db: AppDatabase,
) {    /**
     * 新建记录的最小流（F02）：建店 → 建记录 → 挂口味，单事务。
     * 注意：M0 不做同名/同点店铺去重，M1 做店铺详情时间线（F04）时再合并。
     */
    /**
     * 新建记录（F02，R2 完整版）：建店 → 建记录 → 挂口味，单事务。
     * photos 为 PhotoStore 产出的相对路径列表（序列化进 records.photos 列）。
     * 注意：暂不做同名/同点店铺去重，F04 店铺详情时间线落地时再合并。
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
        photos: List<String> = emptyList(),
        isOriginalPhoto: Boolean = true,
    ): Long = db.withTransaction {
        val shopId = db.shopDao().insert(
            Shop(name = shopName, latitude = latitude, longitude = longitude, address = address),
        )
        val recordId = db.mealRecordDao().insert(
            MealRecord(
                shopId = shopId,
                dishName = dishName,
                rating = rating,
                comment = comment,
                photos = com.tastemap.app.data.photo.PhotoJson.encode(photos),
                isOriginalPhoto = isOriginalPhoto,
            ),
        )
        if (tasteIds.isNotEmpty()) {
            db.mealRecordDao().insertTastes(tasteIds.map { RecordTasteCrossRef(recordId, it) })
        }
        shopId
    }
}

/** F04 店铺详情时间线的展示数据 */
data class RecordUi(
    val record: com.tastemap.app.data.db.MealRecord,
    val tasteNames: List<String>,
    val photoPaths: List<String>,
)

data class ShopDetailUi(
    val shop: Shop?,
    val records: List<RecordUi>,
    val avgRating: Double,
    val dominantTasteName: String?,
)

@Singleton
class ShopDetailRepository @Inject constructor(
    private val db: AppDatabase,
) {
    fun observe(shopId: Long): Flow<ShopDetailUi> = combine(
        db.shopDao().observeById(shopId),
        db.mealRecordDao().observeByShop(shopId),
        db.mealRecordDao().observeAllTasteRefs(),
        db.tasteTagDao().observeAll(),
    ) { shop, records, refs, tastes ->
        val tasteNameById = tastes.associate { it.id to it.name }
        val refsByRecord = refs.groupBy { it.recordId }
        val recordUis = records.map { record ->
            RecordUi(
                record = record,
                tasteNames = refsByRecord[record.id].orEmpty().mapNotNull { tasteNameById[it.tasteId] },
                photoPaths = com.tastemap.app.data.photo.PhotoJson.decode(record.photos),
            )
        }
        val dominant = recordUis
            .flatMap { it.tasteNames }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key
        ShopDetailUi(
            shop = shop,
            records = recordUis,
            avgRating = if (records.isEmpty()) 0.0 else records.sumOf { it.rating }.toDouble() / records.size,
            dominantTasteName = dominant,
        )
    }
}
