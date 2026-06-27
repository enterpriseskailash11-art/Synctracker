package com.example.data.local

import androidx.room.*
import com.example.data.model.ActivityTask
import com.example.data.model.UnlockedBadge
import com.example.data.model.UserProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    // Activity Tasks
    @Query("SELECT * FROM activity_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<ActivityTask>>

    @Query("SELECT * FROM activity_tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): ActivityTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ActivityTask): Long

    @Delete
    suspend fun deleteTask(task: ActivityTask)

    @Query("DELETE FROM activity_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    // Unlocked Badges
    @Query("SELECT * FROM unlocked_badges")
    fun getAllBadges(): Flow<List<UnlockedBadge>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadge(badge: UnlockedBadge)

    // User Progress
    @Query("SELECT * FROM user_progress WHERE id = 1")
    fun getProgress(): Flow<UserProgress?>

    @Query("SELECT * FROM user_progress WHERE id = 1")
    suspend fun getProgressValue(): UserProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: UserProgress)
}
