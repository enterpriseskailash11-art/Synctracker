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

    // Persistent SharedPreferences for user convenience and cross-device sync configuration
    private val prefs = application.getSharedPreferences("sync_tracker_prefs", Context.MODE_PRIVATE)

    val googleSheetsToken = MutableStateFlow(prefs.getString("google_sheets_token", "") ?: "")
    val googleSpreadsheetId = MutableStateFlow(prefs.getString("google_spreadsheet_id", "") ?: "")
    val googleSheetName = MutableStateFlow(prefs.getString("google_sheet_name", "Sheet1") ?: "Sheet1")
    val googleUserEmail = MutableStateFlow(prefs.getString("google_user_email", "") ?: "")

    fun saveSyncSettings(token: String, spreadsheetId: String, sheetName: String, email: String) {
        prefs.edit().apply {
            putString("google_sheets_token", token)
            putString("google_spreadsheet_id", spreadsheetId)
            putString("google_sheet_name", sheetName)
            putString("google_user_email", email)
            apply()
        }
        googleSheetsToken.value = token
        googleSpreadsheetId.value = spreadsheetId
        googleSheetName.value = sheetName
        googleUserEmail.value = email
    }

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
     * Bidirectional REST Sync with Google Sheets API.
     * This pulls the sheet contents, merges modifications and new trackers, updates Room, and pushes the final state back.
     */
    fun performDirectGoogleSheetsSync(
        context: Context,
        accessToken: String,
        spreadsheetId: String,
        sheetName: String = "Sheet1",
        gmailAccount: String = ""
    ) {
        if (accessToken.isBlank() || spreadsheetId.isBlank()) {
            Toast.makeText(context, "Please enter valid Access Token and Spreadsheet ID", Toast.LENGTH_LONG).show()
            return
        }

        // Save settings persistently
        saveSyncSettings(accessToken, spreadsheetId, sheetName, gmailAccount)

        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            _syncStatus.value = "Starting Bidirectional Sync..."
            
            try {
                val client = OkHttpClient()
                val targetSheet = if (sheetName.isBlank()) "Sheet1" else sheetName
                val encodedRange = java.net.URLEncoder.encode("$targetSheet!A1:I500", "UTF-8")
                
                // 1. GET current sheet tasks to pull changes from other devices
                _syncStatus.value = "Pulling updates from Cloud..."
                val getUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedRange"
                val getRequest = Request.Builder()
                    .url(getUrl)
                    .get()
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                val sheetTasks = mutableListOf<ActivityTask>()
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                try {
                    client.newCall(getRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyString = response.body?.string() ?: ""
                            if (bodyString.isNotBlank()) {
                                val jsonObject = org.json.JSONObject(bodyString)
                                val valuesArray = jsonObject.optJSONArray("values")
                                if (valuesArray != null && valuesArray.length() > 1) {
                                    for (i in 1 until valuesArray.length()) { // Skip the header row
                                        val rowArray = valuesArray.optJSONArray(i) ?: continue
                                        val title = if (rowArray.length() > 1) rowArray.optString(1) else ""
                                        if (title.isBlank()) continue
                                        
                                        val desc = if (rowArray.length() > 2) rowArray.optString(2) else ""
                                        val category = if (rowArray.length() > 3) rowArray.optString(3) else "DAILY_TRACKER"
                                        val isCompleted = if (rowArray.length() > 4) rowArray.optString(4).toBoolean() else false
                                        
                                        val compDateStr = if (rowArray.length() > 5) rowArray.optString(5) else "N/A"
                                        val completedAt = if (compDateStr != "N/A" && compDateStr.isNotBlank()) {
                                            try { sdf.parse(compDateStr)?.time } catch(e: Exception) { null }
                                        } else null
                                        
                                        val comment = if (rowArray.length() > 6) rowArray.optString(6) else ""
                                        val points = if (rowArray.length() > 7) rowArray.optString(7).toIntOrNull() ?: 10 else 10
                                        
                                        val createdDateStr = if (rowArray.length() > 8) rowArray.optString(8) else ""
                                        val createdAt = if (createdDateStr.isNotBlank()) {
                                            try { sdf.parse(createdDateStr)?.time ?: System.currentTimeMillis() } catch(e: Exception) { System.currentTimeMillis() }
                                        } else System.currentTimeMillis()

                                        sheetTasks.add(ActivityTask(
                                            id = 0,
                                            title = title,
                                            description = desc,
                                            category = category,
                                            isCompleted = isCompleted,
                                            completedAt = completedAt,
                                            comment = comment,
                                            pointsAwarded = points,
                                            createdAt = createdAt
                                        ))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // It's fine if GET fails (e.g., sheet is blank), we will just merge from local and upload
                }

                // 2. Local Merge with sheetTasks
                _syncStatus.value = "Merging cloud & local trackers..."
                val localTasks = tasks.value
                for (sheetTask in sheetTasks) {
                    val matchingLocal = localTasks.find {
                        it.title.equals(sheetTask.title, ignoreCase = true) &&
                        it.category.equals(sheetTask.category, ignoreCase = true)
                    }
                    if (matchingLocal != null) {
                        // Merge completion state and comments
                        val mergedCompleted = matchingLocal.isCompleted || sheetTask.isCompleted
                        val mergedCompletedAt = if (mergedCompleted) {
                            matchingLocal.completedAt ?: sheetTask.completedAt ?: System.currentTimeMillis()
                        } else null
                        val mergedComment = when {
                            matchingLocal.comment.isNotBlank() && sheetTask.comment.isNotBlank() && matchingLocal.comment != sheetTask.comment -> {
                                "${matchingLocal.comment} | ${sheetTask.comment}"
                            }
                            matchingLocal.comment.isNotBlank() -> matchingLocal.comment
                            else -> sheetTask.comment
                        }
                        
                        val updatedLocal = matchingLocal.copy(
                            isCompleted = mergedCompleted,
                            completedAt = mergedCompletedAt,
                            comment = mergedComment
                        )
                        repository.insertTask(updatedLocal)
                    } else {
                        // New task pulled from sheet! (Created on another device)
                        repository.insertTask(sheetTask.copy(id = 0))
                    }
                }

                // 3. Fetch latest local state after merge and Push back to Sheet
                _syncStatus.value = "Uploading synced trackers to Cloud..."
                val finalTasks = repository.allTasks.firstOrNull() ?: emptyList()

                val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedRange?valueInputOption=USER_ENTERED"
                val rows = mutableListOf<List<String>>()
                rows.add(listOf("ID", "Title", "Description", "Category", "Completed", "Completion Date", "Comment", "Points Awarded", "Created Date"))
                
                finalTasks.forEach { task ->
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
                        _syncStatus.value = "Synced with Sheets successfully at ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "Bidirectional cloud sync successful! All devices in sync.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val errorMsg = response.body?.string() ?: "Unknown error"
                        _syncStatus.value = "Sheets Push Failed: HTTP ${response.code}"
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "Cloud sync push failed: ${response.code}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // Also trigger widget update
                SyncTrackerWidgetProvider.triggerUpdate(getApplication())

            } catch (e: Exception) {
                _syncStatus.value = "Sync Error: ${e.message}"
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Cloud Sync Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Sync with Google Sheets using saved settings
     */
    fun triggerPersistedSheetsSync(context: Context) {
        val token = googleSheetsToken.value
        val sheetId = googleSpreadsheetId.value
        val sName = googleSheetName.value
        val email = googleUserEmail.value
        if (token.isNotBlank() && sheetId.isNotBlank()) {
            performDirectGoogleSheetsSync(context, token, sheetId, sName, email)
        } else {
            Toast.makeText(context, "Please configure Sheets settings in Sync tab first!", Toast.LENGTH_LONG).show()
        }
    }
}
