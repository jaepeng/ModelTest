package com.example.modeltest.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weekly_plans",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["date", "categoryId"], unique = true)]
)
data class WeeklyPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,           // YYYY-MM-DD
    val categoryId: Long,
    val isCustom: Boolean = false  // true = user manually set
)