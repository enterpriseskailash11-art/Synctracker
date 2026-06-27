package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_tasks")
data class ActivityTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val category: String, // "DAILY_TRACKER" or "MONTHLY_TARGET"
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val comment: String = "",
    val pointsAwarded: Int = 10,
    val createdAt: Long = System.currentTimeMillis()
)
