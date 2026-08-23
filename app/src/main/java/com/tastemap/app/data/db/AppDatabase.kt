package com.tastemap.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        TasteTag::class,
        Shop::class,
        MealRecord::class,
        RecordTasteCrossRef::class,
        WishlistItem::class,
        ScheduleItem::class,
    ],
    version = 1,
    exportSchema = true, // schema 导出到 app/schemas/，为后续 migration 留底
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasteTagDao(): TasteTagDao
    abstract fun shopDao(): ShopDao
    abstract fun mealRecordDao(): MealRecordDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun scheduleDao(): ScheduleDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "tastemap.db")
            // M0 schema 未稳定，降级（导入旧备份）允许重建；升级 migration 从 M1 起严格管理
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
}
