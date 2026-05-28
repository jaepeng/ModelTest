package com.example.modeltest.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.modeltest.data.entity.UserSetting
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingDao {

    @Query("SELECT value FROM user_settings WHERE `key` = :key")
    fun getSetting(key: String): Flow<String?>

    @Query("SELECT value FROM user_settings WHERE `key` = :key")
    suspend fun getSettingOnce(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: UserSetting)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSettings(settings: List<UserSetting>)
}