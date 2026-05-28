package com.example.modeltest.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "challenge_completions",
    foreignKeys = [
        ForeignKey(
            entity = Challenge::class,
            parentColumns = ["id"],
            childColumns = ["challengeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["challengeId"], unique = true)]
)
data class ChallengeCompletion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val challengeId: Long,
    val completedAt: Long = System.currentTimeMillis()
)
