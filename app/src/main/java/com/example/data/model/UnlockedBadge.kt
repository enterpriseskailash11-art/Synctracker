package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unlocked_badges")
data class UnlockedBadge(
    @PrimaryKey val id: String, // e.g., "starter_success", "streak_3", "streak_7", etc.
    val title: String,
    val description: String,
    val unlockedAt: Long = System.currentTimeMillis()
)
