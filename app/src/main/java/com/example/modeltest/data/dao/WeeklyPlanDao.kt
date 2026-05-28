package com.example.modeltest.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.modeltest.data.entity.WeeklyPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyPlanDao {

    @Query("SELECT * FROM weekly_plans WHERE date = :date")
    fun getPlansForDate(date: String): Flow<List<WeeklyPlan>>

    @Query("SELECT * FROM weekly_plans WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    fun getPlansForWeek(startDate: String, endDate: String): Flow<List<WeeklyPlan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlans(plans: List<WeeklyPlan>)

    @Query("DELETE FROM weekly_plans WHERE date = :date")
    suspend fun deletePlansForDate(date: String)

    @Query("DELETE FROM weekly_plans WHERE date BETWEEN :startDate AND :endDate")
    suspend fun deletePlansForWeek(startDate: String, endDate: String)
}