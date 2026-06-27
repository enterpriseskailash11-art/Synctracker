package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.ActivityTask
import com.example.data.model.UnlockedBadge
import com.example.data.model.UserProgress
import com.example.data.repository.ActivityRepository
import com.example.widget.SyncTrackerWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ActivityRepository
    val tasks: StateFlow<List<ActivityTask>>
    val progress: StateFlow<UserProgress?>
    val badges: StateFlow<List<UnlockedBadge>>

    // Synchronization statuses
    private val _syncStatus = MutableStateFlow<String>("Not Synchronized")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow<Boolean>(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ActivityRepository(database.activityDao())
        
        tasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        progress = repository.progress.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProgress(id = 1, totalPoints = 0, currentStreak = 0, longestStreak = 0, lastActiveDate = null)
        )

        badges = repository.allBadges.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Seed initial progress singleton row if not exists
        viewModelScope.launch(Dispatchers.IO) {
            val db = database.activityDao()
            if (db.getProgressValue() == null) {
                db.insertProgress(UserProgress())
            }
        }
    }

    fun addTask(title: String, description: String, category: String, points: Int = 10) {
        viewModelScope.launch(Dispatchers.IO) {
            val newTask = ActivityTask(
                title = title,
                description = description,
                category = category,
                pointsAwarded = points
            )
            repository.insertTask(newTask)
            SyncTrackerWidgetProvider.triggerUpdate(getApplication())
        }
    }

    fun deleteTask(task: ActivityTask) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTask(task)
            SyncTrackerWidgetProvider.triggerUpdate(getApplication())
        }
    }

    fun toggleTask(taskId: Int, isCompleted: Boolean, comment: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleTaskCompletion(taskId, isCompleted, comment)
            SyncTrackerWidgetProvider.triggerUpdate(getApplication())
        }
    }

    fun updateComment(taskId: Int, comment: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTaskComment(taskId, comment)
            SyncTrackerWidgetProvider.triggerUpdate(getApplication())
        }
    }

    /**
     * Converts the current tasks list into a CSV format compatible with Google Sheets.
     */
    fun generateCsvContent(taskList: List<ActivityTask>): String {
        val sb = StringBuilder()
        sb.append("ID,Title,Description,Category,Completed,Completion Date,Comment,Points Awarded,Created Date\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (task in taskList) {
            val compDate = if (task.completedAt != null) sdf.format(Date(task.completedAt)) else "N/A"
            val credDate = sdf.format(Date(task.createdAt))
            // Escape CSV commas
            val title = task.title.replace(",", ";")
            val desc = task.description.replace(",", ";")
            val comment = task.comment.replace(",", ";")
            sb.append("${task.id},\"$title\",\"$desc\",${task.category},${task.isCompleted},\"$compDate\",\"$comment\",${task.pointsAwarded},\"$credDate\"\n")
        }
        return sb.toString()
    }

    /**
     * Export Tasks directly to Google Drive Excel/Sheets via Android Sharesheet (extremely reliable).
     */
    fun shareTasksAsCsv(context: Context) {
        val csvData = generateCsvContent(tasks.value)
        try {
            val cachePath = File(context.cacheDir, "csv_exports")
            cachePath.mkdirs()
            val file = File(cachePath, "SyncTracker_Activities_${System.currentTimeMillis()}.csv")
            file.writeText(csvData)

            // Get URI using FileProvider
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/comma-separated-values"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "My Activity Trackers (SyncTracker)")
                putExtra(Intent.EXTRA_TEXT, "Here are my tracked activities from SyncTracker. Save this file directly to your Google Drive to keep it in your spreadsheets!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Save to Google Drive Sheet"))
            _syncStatus.value = "Exported CSV successfully at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Share tasks formatted beautifully as a Google Keep Note list.
     */
    fun shareToGoogleKeep(context: Context) {
        val activeTasks = tasks.value
        if (activeTasks.isEmpty()) {
            Toast.makeText(context, "No activities to share!", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        sb.append("📋 SYNCTRACKER DAILY ACTIVITIES\n")
        sb.append("Exported on: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n")
        sb.append("=========================\n\n")

        val dailyTasks = activeTasks.filter { it.category == "DAILY_TRACKER" }
        if (dailyTasks.isNotEmpty()) {
            sb.append("🔹 DAILY TRACKERS:\n")
            dailyTasks.forEach { task ->
                val status = if (task.isCompleted) "[X]" else "[ ]"
                sb.append("$status ${task.title}\n")
                if (task.comment.isNotBlank()) {
                    sb.append("   ↳ Comment: ${task.comment}\n")
                }
            }
            sb.append("\n")
        }

        val monthlyTasks = activeTasks.filter { it.category == "MONTHLY_TARGET" }
        if (monthlyTasks.isNotEmpty()) {
            sb.append("🏆 MONTHLY TARGETS:\n")
            monthlyTasks.forEach { task ->
                val status = if (task.isCompleted) "[X]" else "[ ]"
                sb.append("$status ${task.title}\n")
                if (task.comment.isNotBlank()) {
                    sb.append("   ↳ Comment: ${task.comment}\n")
                }
            }
        }

        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "SyncTracker Activity Notes")
                putExtra(Intent.EXTRA_TEXT, sb.toString())
            }
            // Explicitly try to target Google Keep if installed
            shareIntent.`package` = "com.google.android.keep"
            
            try {
                context.startActivity(shareIntent)
            } catch (e: Exception) {
                // Fallback to standard chooser if Keep isn't directly targetable
                shareIntent.`package` = null
                context.startActivity(Intent.createChooser(shareIntent, "Send to Google Keep / Notes"))
            }
            _syncStatus.value = "Synced with Keep successfully at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
        } catch (e: Exception) {
            Toast.makeText(context, "Keep Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Optional Direct REST Sync with Google Sheets API (for power users with Access Tokens).
     */
    fun performDirectGoogleSheetsSync(context: Context, accessToken: String, spreadsheetId: String, sheetName: String = "Sheet1") {
        if (accessToken.isBlank() || spreadsheetId.isBlank()) {
            Toast.makeText(context, "Please enter valid Access Token and Spreadsheet ID", Toast.LENGTH_LONG).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            _syncStatus.value = "Syncing with Google Sheets API..."
            
            try {
                val csvData = generateCsvContent(tasks.value)
                
                // Formulate spreadsheet update body or append body using the Google Sheets API v4
                // We will write values directly using a simple API PUT request
                val client = OkHttpClient()
                val targetSheet = if (sheetName.isBlank()) "Sheet1" else sheetName
                val encodedRange = java.net.URLEncoder.encode("$targetSheet!A1:I${tasks.value.size + 2}", "UTF-8")
                val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedRange?valueInputOption=USER_ENTERED"
                
                val rows = mutableListOf<List<String>>()
                rows.add(listOf("ID", "Title", "Description", "Category", "Completed", "Completion Date", "Comment", "Points Awarded", "Created Date"))
                
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                tasks.value.forEach { task ->
                    val compDate = if (task.completedAt != null) sdf.format(Date(task.completedAt)) else "N/A"
                    rows.add(listOf(
                        task.id.toString(),
                        task.title,
                        task.description,
                        task.category,
                        task.isCompleted.toString(),
                        compDate,
                        task.comment,
                        task.pointsAwarded.toString(),
                        sdf.format(Date(task.createdAt))
                    ))
                }

                // Construct Google API Body
                val bodyJson = StringBuilder()
                bodyJson.append("{ \"range\": \"$targetSheet!A1:I${rows.size}\", \"majorDimension\": \"ROWS\", \"values\": [")
                rows.forEachIndexed { index, row ->
                    bodyJson.append("[")
                    row.forEachIndexed { colIndex, cell ->
                        val escapedCell = cell.replace("\\", "\\\\").replace("\"", "\\\"")
                        bodyJson.append("\"$escapedCell\"")
                        if (colIndex < row.size - 1) bodyJson.append(",")
                    }
                    bodyJson.append("]")
                    if (index < rows.size - 1) bodyJson.append(",")
                }
                bodyJson.append("] }")

                val requestBody = bodyJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .put(requestBody)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        _syncStatus.value = "Synced with Sheets API Successfully!"
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "Direct Sheets Sync Successful!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorMsg = response.body?.string() ?: "Unknown error"
                        _syncStatus.value = "Sheets Sync Failed: HTTP ${response.code}"
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "Direct Sheets Sync Failed: ${response.code}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                _syncStatus.value = "Sheets Sync Error: ${e.message}"
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Direct Sheets Sync Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
