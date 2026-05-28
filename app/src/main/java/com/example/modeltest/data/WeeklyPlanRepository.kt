package com.example.modeltest.data

import com.example.modeltest.data.dao.WeeklyPlanDao
import com.example.modeltest.data.entity.WeeklyPlan
import kotlinx.coroutines.flow.Flow

class WeeklyPlanRepository(private val dao: WeeklyPlanDao) {

    fun getPlansForDate(date: String) = dao.getPlansForDate(date)

    fun getPlansForWeek(startDate: String, endDate: String) = dao.getPlansForWeek(startDate, endDate)

    suspend fun savePlansForDate(date: String, categoryIds: List<Long>, isCustom: Boolean) {
        dao.deletePlansForDate(date)
        val plans = categoryIds.map { categoryId ->
            WeeklyPlan(date = date, categoryId = categoryId, isCustom = isCustom)
        }
        dao.insertPlans(plans)
    }

    suspend fun savePlansForWeek(startDate: String, endDate: String, plansByDate: Map<String, List<Long>>) {
        dao.deletePlansForWeek(startDate, endDate)
        val allPlans = plansByDate.flatMap { (date, categoryIds) ->
            categoryIds.map { categoryId ->
                WeeklyPlan(date = date, categoryId = categoryId, isCustom = true)
            }
        }
        dao.insertPlans(allPlans)
    }
}