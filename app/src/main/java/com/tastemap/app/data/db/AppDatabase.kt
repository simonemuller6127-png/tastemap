package com.tastemap.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        WishlistTasteCrossRef::class,
        ScheduleItem::class,
    ],
    version = 2, // R0 schema v2：一次到位（PRD §8），此后只做增量迁移
    exportSchema = true, // schema 导出到 app/schemas/，为后续 migration 留底
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasteTagDao(): TasteTagDao
    abstract fun shopDao(): ShopDao
    abstract fun mealRecordDao(): MealRecordDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        /** v1→v2（R0）：新列可空/带默认值，老数据无损；新增 wishlist_tastes 关联表 */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shops ADD COLUMN amapPoiId TEXT")
                db.execSQL("ALTER TABLE shops ADD COLUMN meituanPoiId TEXT")
                db.execSQL("ALTER TABLE shops ADD COLUMN stickerPath TEXT")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN photos TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN stickerPath TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wishlist_tastes` (" +
                        "`wishlistId` INTEGER NOT NULL, `tasteId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`wishlistId`, `tasteId`), " +
                        "FOREIGN KEY(`wishlistId`) REFERENCES `wishlist`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`tasteId`) REFERENCES `taste_tags`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wishlist_tastes_tasteId` ON `wishlist_tastes` (`tasteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shops_amapPoiId` ON `shops` (`amapPoiId`)")
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "tastemap.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            // 只允许降级重建（导入旧备份场景）；升级必须走显式 migration
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
}
