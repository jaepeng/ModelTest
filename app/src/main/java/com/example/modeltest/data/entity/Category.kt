package com.example.modeltest.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val displayName: String,
    val emoji: String,
    val sortOrder: Int,
    val isActive: Boolean = true
)
