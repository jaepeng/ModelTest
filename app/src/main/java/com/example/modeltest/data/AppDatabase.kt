package com.example.modeltest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.modeltest.data.dao.CategoryDao
import com.example.modeltest.data.dao.ChallengeDao
import com.example.modeltest.data.dao.UserSettingDao
import com.example.modeltest.data.dao.WeeklyPlanDao
import com.example.modeltest.data.entity.Category
import com.example.modeltest.data.entity.Challenge
import com.example.modeltest.data.entity.ChallengeCompletion
import com.example.modeltest.data.entity.UserSetting
import com.example.modeltest.data.entity.WeeklyPlan

@Database(
    entities = [Category::class, Challenge::class, ChallengeCompletion::class, WeeklyPlan::class, UserSetting::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun weeklyPlanDao(): WeeklyPlanDao
    abstract fun userSettingDao(): UserSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "daily_challenge.db"
                )
                    .addCallback(SeedCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class SeedCallback : Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Ensure 7 categories always exist (safe for existing DBs)
            val cursor = db.query("SELECT COUNT(*) FROM categories")
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            cursor.close()
            if (count == 0) {
                val categories = listOf(
                    "('health', '健康', '💧', 1, 1)",
                    "('mindfulness', '正念', '🧘', 2, 1)",
                    "('learning', '学习', '📚', 3, 1)",
                    "('creativity', '创造', '🎨', 4, 1)",
                    "('social', '社交', '💬', 5, 1)",
                    "('fitness', '运动', '🏃', 6, 1)",
                    "('nature', '自然', '🌿', 7, 1)"
                )
                categories.forEach {
                    db.execSQL("INSERT INTO categories (name, displayName, emoji, sortOrder, isActive) VALUES $it")
                }
            }
        }
    }
}
