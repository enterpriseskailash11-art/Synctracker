package com.example.data.repository

import com.example.data.local.ActivityDao
import com.example.data.model.ActivityTask
import com.example.data.model.UnlockedBadge
import com.example.data.model.UserProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.*

class ActivityRepository(private val activityDao: ActivityDao) {

    val allTasks: Flow<List<ActivityTask>> = activityDao.getAllTasks()
    val allBadges: Flow<List<UnlockedBadge>> = activityDao.getAllBadges()
    val progress: Flow<UserProgress?> = activityDao.getProgress()

    suspend fun insertTask(task: ActivityTask): Long {
        return activityDao.insertTask(task)
    }

    suspend fun deleteTask(task: ActivityTask) {
        activityDao.deleteTask(task)
    }

    suspend fun deleteTaskById(id: Int) {
        activityDao.deleteTaskById(id)
    }

    // Helper functions for date operations
    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun getYesterdayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        return sdf.format(cal.time)
    }

    /**
     * Mark a task as completed/uncompleted, award/deduct points, update streak, and evaluate badges.
     */
    suspend fun toggleTaskCompletion(taskId: Int, isCompleted: Boolean, comment: String = "") {
        val task = activityDao.getTaskById(taskId) ?: return
        if (task.isCompleted == isCompleted) return // No change

        // 1. Calculate points
        val pointsDelta = if (isCompleted) {
            if (task.category == "MONTHLY_TARGET") 50 else 10
        } else {
            if (task.category == "MONTHLY_TARGET") -50 else -10
        }

        // 2. Create updated task
        val updatedTask = task.copy(
            isCompleted = isCompleted,
            completedAt = if (isCompleted) System.currentTimeMillis() else null,
            comment = comment
        )
        activityDao.insertTask(updatedTask)

        // 3. Update User Progress (points & streak)
        val currentProgress = activityDao.getProgressValue() ?: UserProgress()
        var newPoints = (currentProgress.totalPoints + pointsDelta).coerceAtLeast(0)
        var newStreak = currentProgress.currentStreak
        var newLongest = currentProgress.longestStreak
        var newLastDate = currentProgress.lastActiveDate

        if (isCompleted) {
            val today = getTodayDateString()
            val yesterday = getYesterdayDateString()

            when (newLastDate) {
                null -> {
                    newStreak = 1
                    newLastDate = today
                    newLongest = newLongest.coerceAtLeast(1)
                }
                today -> {
                    // Already active today, streak doesn't change
                }
                yesterday -> {
                    newStreak += 1
                    newLastDate = today
                    newLongest = newLongest.coerceAtLeast(newStreak)
                }
                else -> {
                    // Broken streak, reset and start at 1
                    newStreak = 1
                    newLastDate = today
                    newLongest = newLongest.coerceAtLeast(1)
                }
            }
        }

        val updatedProgress = currentProgress.copy(
            totalPoints = newPoints,
            currentStreak = newStreak,
            longestStreak = newLongest,
            lastActiveDate = newLastDate
        )
        activityDao.insertProgress(updatedProgress)

        // 4. Check & Unlock Badges
        evaluateBadges(newStreak, newPoints)
    }

    /**
     * Update task comment.
     */
    suspend fun updateTaskComment(taskId: Int, comment: String) {
        val task = activityDao.getTaskById(taskId) ?: return
        val updatedTask = task.copy(comment = comment)
        activityDao.insertTask(updatedTask)
        
        // Re-evaluate comment-based badges
        val streak = activityDao.getProgressValue()?.currentStreak ?: 0
        val points = activityDao.getProgressValue()?.totalPoints ?: 0
        evaluateBadges(streak, points)
    }

    /**
     * Checks if current stats qualify for any badges and unlocks them.
     */
    private suspend fun evaluateBadges(currentStreak: Int, totalPoints: Int) {
        val tasks = activityDao.getAllTasks().firstOrNull() ?: emptyList()
        val completedTasks = tasks.filter { it.isCompleted }
        val completedCount = completedTasks.size

        // 1. Starter Success (Complete at least 1 task)
        if (completedCount >= 1) {
            unlockBadge(
                id = "starter_success",
                title = "Starter Success",
                description = "Completed your first activity tracked task!"
            )
        }

        // 2. Consistency King (3 day streak)
        if (currentStreak >= 3) {
            unlockBadge(
                id = "streak_3",
                title = "Consistency King",
                description = "Maintained a 3-day activity logging streak."
            )
        }

        // 3. Elite Achiever (7 day streak)
        if (currentStreak >= 7) {
            unlockBadge(
                id = "streak_7",
                title = "Elite Achiever",
                description = "Maintained an elite 7-day logging streak."
            )
        }

        // 4. Monthly Master (Complete at least one monthly target)
        val hasMonthlyCompleted = completedTasks.any { it.category == "MONTHLY_TARGET" }
        if (hasMonthlyCompleted) {
            unlockBadge(
                id = "monthly_master",
                title = "Monthly Master",
                description = "Successfully completed a long-term Monthly Target!"
            )
        }

        // 5. Comments Critic (Add comments to 3 or more completed tasks)
        val commentedCompletedCount = completedTasks.count { it.comment.isNotBlank() }
        if (commentedCompletedCount >= 3) {
            unlockBadge(
                id = "comments_critic",
                title = "Comments Critic",
                description = "Wrote helpful completion comments on 3 or more tasks."
            )
        }

        // 6. Task Conqueror (Complete 10 or more tasks)
        if (completedCount >= 10) {
            unlockBadge(
                id = "task_conqueror",
                title = "Task Conqueror",
                description = "Conquered and checked off 10 total tracking tasks!"
            )
        }
    }

    private suspend fun unlockBadge(id: String, title: String, description: String) {
        // This will insert or replace, ensuring the badge is logged in our unlocked list
        activityDao.insertBadge(UnlockedBadge(id = id, title = title, description = description))
    }
}
