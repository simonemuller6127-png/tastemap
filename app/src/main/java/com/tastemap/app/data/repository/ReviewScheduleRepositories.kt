package com.tastemap.app.data.repository

import com.tastemap.app.data.db.AppDatabase
import com.tastemap.app.data.db.MealRecord
import com.tastemap.app.data.db.ScheduleItem
import com.tastemap.app.data.db.Shop
import com.tastemap.app.data.db.WishlistItem
import com.tastemap.app.data.db.WishlistTasteCrossRef
import com.tastemap.app.data.photo.PhotoJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** F07 回顾卡片：一条历史记录的卡片形态 */
data class ReviewCard(
    val record: MealRecord,
    val shop: Shop?,
    val tasteNames: List<String>,
    val firstPhoto: String?,
)

@Singleton
class ReviewRepository @Inject constructor(
    private val db: AppDatabase,
) {
    fun observeCards(): Flow<List<ReviewCard>> = combine(
        db.mealRecordDao().observeAll(),
        db.shopDao().observeAll(),
        db.mealRecordDao().observeAllTasteRefs(),
        db.tasteTagDao().observeAll(),
    ) { records, shops, refs, tastes ->
        val shopById = shops.associateBy { it.id }
        val tasteNameById = tastes.associate { it.id to it.name }
        val refsByRecord = refs.groupBy { it.recordId }
        records.map { record ->
            ReviewCard(
                record = record,
                shop = shopById[record.shopId],
                tasteNames = refsByRecord[record.id].orEmpty().mapNotNull { tasteNameById[it.tasteId] },
                firstPhoto = PhotoJson.decode(record.photos).firstOrNull(),
            )
        }
    }
}

@Singleton
class ScheduleRepository @Inject constructor(
    private val db: AppDatabase,
) {
    fun observeByDate(date: LocalDate): Flow<List<ScheduleItem>> =
        db.scheduleDao().observeByDate(date.format(DateTimeFormatter.ISO_LOCAL_DATE))

    suspend fun add(date: LocalDate, mealSlot: String, shopId: Long? = null, note: String = "", reminderOn: Boolean = false): Long =
        db.scheduleDao().insert(
            ScheduleItem(
                date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                mealSlot = mealSlot,
                shopId = shopId,
                note = note,
                reminderOn = reminderOn,
            ),
        )

    suspend fun setReminder(id: Long, on: Boolean) = db.scheduleDao().setReminder(id, on)
    suspend fun remove(id: Long) = db.scheduleDao().deleteById(id)
    suspend fun getById(id: Long): ScheduleItem? = db.scheduleDao().getById(id)

    companion object {
        val MEAL_SLOTS = listOf("早餐", "午餐", "晚餐", "夜宵")
    }
}

@Singleton
class WishlistRepository @Inject constructor(
    private val db: AppDatabase,
) {
    fun observeAll(): Flow<List<WishlistItem>> = db.wishlistDao().observeAll()

    suspend fun add(text: String, note: String = "", latitude: Double? = null, longitude: Double? = null, tasteIds: List<Long> = emptyList()): Long {
        val id = db.wishlistDao().insert(WishlistItem(text = text, note = note, latitude = latitude, longitude = longitude))
        if (tasteIds.isNotEmpty()) {
            db.wishlistDao().insertTastes(tasteIds.map { WishlistTasteCrossRef(id, it) })
        }
        return id
    }

    suspend fun remove(id: Long) = db.wishlistDao().deleteById(id)
    suspend fun getById(id: Long): WishlistItem? = db.wishlistDao().getById(id)
}
