package com.tastemap.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TasteTagDao {
    @Query("SELECT * FROM taste_tags ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<TasteTag>>

    @Query("SELECT * FROM taste_tags WHERE id = :id")
    suspend fun getById(id: Long): TasteTag?

    @Query("SELECT COUNT(*) FROM taste_tags")
    suspend fun count(): Int

    @Query("SELECT * FROM taste_tags ORDER BY sortOrder, id")
    suspend fun getAll(): List<TasteTag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<TasteTag>)

    @Query("DELETE FROM taste_tags")
    suspend fun deleteAll()
}

@Dao
interface ShopDao {
    @Query("SELECT * FROM shops ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Shop>>

    @Query("SELECT * FROM shops WHERE id = :id")
    fun observeById(id: Long): Flow<Shop?>

    @Query("SELECT * FROM shops WHERE id = :id")
    suspend fun getById(id: Long): Shop?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shop: Shop): Long

    @Query("SELECT * FROM shops")
    suspend fun getAll(): List<Shop>

    @Query("DELETE FROM shops")
    suspend fun deleteAll()
}

@Dao
interface MealRecordDao {
    @Query("SELECT * FROM meal_records ORDER BY tastedAt DESC")
    fun observeAll(): Flow<List<MealRecord>>

    @Query("SELECT * FROM meal_records WHERE shopId = :shopId ORDER BY tastedAt DESC")
    fun observeByShop(shopId: Long): Flow<List<MealRecord>>

    @Query("SELECT * FROM record_tastes")
    fun observeAllTasteRefs(): Flow<List<RecordTasteCrossRef>>

    @Insert
    suspend fun insert(record: MealRecord): Long

    @Insert
    suspend fun insertTastes(refs: List<RecordTasteCrossRef>)

    @Query("SELECT * FROM meal_records")
    suspend fun getAll(): List<MealRecord>

    @Query("SELECT * FROM record_tastes")
    suspend fun getAllTasteRefs(): List<RecordTasteCrossRef>

    @Query("DELETE FROM meal_records")
    suspend fun deleteAll()
}

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WishlistItem>>

    @Insert
    suspend fun insert(item: WishlistItem): Long

    @Insert
    suspend fun insertTastes(refs: List<WishlistTasteCrossRef>)

    @Query("SELECT * FROM wishlist_tastes")
    suspend fun getAllTasteRefs(): List<WishlistTasteCrossRef>

    @Query("SELECT * FROM wishlist")
    suspend fun getAll(): List<WishlistItem>

    @Query("SELECT * FROM wishlist WHERE id = :id")
    suspend fun getById(id: Long): WishlistItem?

    @Query("DELETE FROM wishlist")
    suspend fun deleteAll()

    @Query("DELETE FROM wishlist_tastes")
    suspend fun deleteAllTastes()

    @Query("DELETE FROM wishlist WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY date, id")
    fun observeAll(): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM schedules WHERE date = :date ORDER BY id")
    fun observeByDate(date: String): Flow<List<ScheduleItem>>

    @Insert
    suspend fun insert(item: ScheduleItem): Long

    @Query("UPDATE schedules SET reminderOn = :on WHERE id = :id")
    suspend fun setReminder(id: Long, on: Boolean)

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getById(id: Long): ScheduleItem?

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM schedules")
    suspend fun getAll(): List<ScheduleItem>

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()
}
