package com.example.modeltest.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.modeltest.data.entity.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?
    
    @Query("SELECT * FROM categories WHERE isActive = 1")
    fun getAllActiveCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE name IN (:names) OR displayName IN (:names)")
    suspend fun getCategoriesByNames(names: List<String>): List<Category>
}
