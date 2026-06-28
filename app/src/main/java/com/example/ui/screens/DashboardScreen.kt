package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ActivityTask
import com.example.data.model.UnlockedBadge
import com.example.data.model.UserProgress
import com.example.ui.viewmodel.ActivityViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ActivityViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val progressState by viewModel.progress.collectAsStateWithLifecycle()
    val unlockedBadges by viewModel.badges.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    val progress = progressState ?: UserProgress()

    var selectedTab by remember { mutableStateOf(0) }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    // Colors & Theme override for a deep aesthetic look
    val darkSlateBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f)
        )
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrackChanges,
                            contentDescription = "SyncTracker Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "SyncTracker",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                actions = {
                    // Flame Streak Badge
                    Row(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable { selectedTab = 2 },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Streak Flame",
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${progress.currentStreak}d",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp
                        )
                    }

                    // Score Display
                    Row(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable { selectedTab = 2 },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = "Points Star",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${progress.totalPoints} pts",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 14.sp
                        )
                    }

                    // Bidirectional Cloud Sync Icon (Visible when sheets sync configured)
                    val savedToken by viewModel.googleSheetsToken.collectAsStateWithLifecycle()
                    if (savedToken.isNotBlank()) {
                        IconButton(
                            onClick = { viewModel.triggerPersistedSheetsSync(context) },
                            enabled = !isSyncing,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("top_app_bar_sync_button")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync all devices",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(imageVector = Icons.Default.TaskAlt, contentDescription = "Tasks") },
                    label = { Text("Trackers", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_trackers_tab")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(imageVector = Icons.Default.Sync, contentDescription = "Sync") },
                    label = { Text("Sync Hub", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_sync_tab")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = "Achievements") },
                    label = { Text("Progress", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_progress_tab")
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddTaskDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("add_task_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Activity")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> TrackersTab(
                    tasks = tasks,
                    onToggle = { id, done, comment -> viewModel.toggleTask(id, done, comment) },
                    onDelete = { viewModel.deleteTask(it) },
                    onUpdateComment = { id, comment -> viewModel.updateComment(id, comment) }
                )
                1 -> SyncTab(
                    tasks = tasks,
                    syncStatus = syncStatus,
                    isSyncing = isSyncing,
                    viewModel = viewModel,
                    onShareKeep = { viewModel.shareToGoogleKeep(context) },
                    onShareSheets = { viewModel.shareTasksAsCsv(context) },
                    onDirectSheetsSync = { token, sheetId, name, email -> 
                        viewModel.performDirectGoogleSheetsSync(context, token, sheetId, name, email)
                    }
                )
                2 -> ProgressTab(
                    progress = progress,
                    unlockedBadges = unlockedBadges
                )
            }
        }
    }

    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, desc, category, pts ->
                viewModel.addTask(title, desc, category, pts)
                showAddTaskDialog = false
                Toast.makeText(context, "Activity Tracker Added!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun TrackersTab(
    tasks: List<ActivityTask>,
    onToggle: (Int, Boolean, String) -> Unit,
    onDelete: (ActivityTask) -> Unit,
    onUpdateComment: (Int, String) -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf("ALL") } // "ALL", "DAILY_TRACKER", "MONTHLY_TARGET"
    var showCompletedOnly by remember { mutableStateOf<Boolean?>(null) } // null = All, true = completed, false = active

    val filteredTasks = tasks.filter { task ->
        val matchesCategory = selectedCategoryFilter == "ALL" || task.category == selectedCategoryFilter
        val matchesStatus = when (showCompletedOnly) {
            true -> task.isCompleted
            false -> !task.isCompleted
            null -> true
        }
        matchesCategory && matchesStatus
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Aesthetic Segmented Category Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedCategoryFilter == "ALL",
                onClick = { selectedCategoryFilter = "ALL" },
                label = { Text("All Trackers") },
                leadingIcon = if (selectedCategoryFilter == "ALL") {
                    { Icon(Icons.Default.Done, contentDescription = null, Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.testTag("filter_chip_all")
            )

            FilterChip(
                selected = selectedCategoryFilter == "DAILY_TRACKER",
                onClick = { selectedCategoryFilter = "DAILY_TRACKER" },
                label = { Text("Daily Trackers") },
                leadingIcon = if (selectedCategoryFilter == "DAILY_TRACKER") {
                    { Icon(Icons.Default.Done, contentDescription = null, Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.testTag("filter_chip_daily")
            )

            FilterChip(
                selected = selectedCategoryFilter == "MONTHLY_TARGET",
                onClick = { selectedCategoryFilter = "MONTHLY_TARGET" },
                label = { Text("Monthly Targets") },
                leadingIcon = if (selectedCategoryFilter == "MONTHLY_TARGET") {
                    { Icon(Icons.Default.Done, contentDescription = null, Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.testTag("filter_chip_monthly")
            )
        }

        // Status Segment Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showCompletedOnly = null },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showCompletedOnly == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (showCompletedOnly == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("All Status", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { showCompletedOnly = false },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showCompletedOnly == false) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (showCompletedOnly == false) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Active Only", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { showCompletedOnly = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showCompletedOnly == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (showCompletedOnly == true) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Completed", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

        if (filteredTasks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.EventNote,
                    contentDescription = "Empty tasks",
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No tracking tasks found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap the '+' button below to define a daily activity or monthly goal target!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTasks, key = { it.id }) { task ->
                    TaskCardItem(
                        task = task,
                        onToggle = { isChecked ->
                            onToggle(task.id, isChecked, task.comment)
                        },
                        onDelete = { onDelete(task) },
                        onCommentChange = { newComment ->
                            onUpdateComment(task.id, newComment)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskCardItem(
    task: ActivityTask,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onCommentChange: (String) -> Unit
) {
    var expandedComment by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf(task.comment) }

    val categoryColor = if (task.category == "DAILY_TRACKER") {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    val containerColor = if (task.isCompleted) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_${task.id}"),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Checkbox for task completion
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = { onToggle(it) },
                        modifier = Modifier.testTag("task_checkbox_${task.id}")
                    )

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = if (task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Category Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = categoryColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (task.category == "DAILY_TRACKER") "Daily Tracker" else "Monthly Target",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = categoryColor
                                )
                            }

                            // Points display badge
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFFFFB300).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "+${task.pointsAwarded} pts",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE5A000)
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete tracker",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 48.dp)
                )
            }

            // Completed Comments indicator and expand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // If comments are logged, show them
                if (task.comment.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Comment,
                            contentDescription = "Comment logged",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = task.comment,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = "No completion comments recorded",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontSize = 11.sp
                    )
                }

                TextButton(
                    onClick = { expandedComment = !expandedComment },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = if (expandedComment) "Close" else "Record Comment",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (expandedComment) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Expanding comment input form
            AnimatedVisibility(
                visible = expandedComment,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        label = { Text("Log Completion Comment", fontSize = 11.sp) },
                        placeholder = { Text("How did this activity go? (e.g. Worked out for 45 mins)", fontSize = 11.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_comment_input_${task.id}"),
                        singleLine = true,
                        trailingIcon = {
                            if (commentText != task.comment) {
                                IconButton(
                                    onClick = {
                                        onCommentChange(commentText)
                                        expandedComment = false
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Save comment",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                onCommentChange(commentText)
                                expandedComment = false
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Save Comment", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyncTab(
    tasks: List<ActivityTask>,
    syncStatus: String,
    isSyncing: Boolean,
    viewModel: ActivityViewModel,
    onShareKeep: () -> Unit,
    onShareSheets: () -> Unit,
    onDirectSheetsSync: (String, String, String, String) -> Unit
) {
    val savedToken by viewModel.googleSheetsToken.collectAsStateWithLifecycle()
    val savedSpreadsheetId by viewModel.googleSpreadsheetId.collectAsStateWithLifecycle()
    val savedSheetName by viewModel.googleSheetName.collectAsStateWithLifecycle()
    val savedEmail by viewModel.googleUserEmail.collectAsStateWithLifecycle()

    var developerMode by remember { mutableStateOf(savedToken.isNotBlank()) }
    var sheetsAccessToken by remember(savedToken) { mutableStateOf(savedToken) }
    var spreadsheetId by remember(savedSpreadsheetId) { mutableStateOf(savedSpreadsheetId) }
    var sheetName by remember(savedSheetName) { mutableStateOf(savedSheetName) }
    var gmailAccount by remember(savedEmail) { mutableStateOf(savedEmail) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = "Sync Info",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(40.dp)
                )
                Column {
                    Text(
                        text = "Synchronization Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = syncStatus,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Keep Sync Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFF9C4), shape = CircleShape)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NoteAlt,
                            contentDescription = "Keep Notes",
                            tint = Color(0xFFFBC02D),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Sync with Google Keep",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Format & compile trackers into checklists",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Text(
                    text = "Compiles all trackers, status, logged completed comments, and gamification milestone accomplishments into formatted checkable lists. Clicking below exports this summary directly to the Google Keep application.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(
                    onClick = onShareKeep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("sync_keep_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBC02D), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text("Share to Google Keep", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Sheets Sync Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFC8E6C9), shape = CircleShape)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GridOn,
                            contentDescription = "Spreadsheets",
                            tint = Color(0xFF388E3C),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Sync with Google Drive Sheets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Log tasks into your Excel spreadsheets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Text(
                    text = "Generates a complete, structured CSV spreadsheet file of your tracked activities, including ID, Title, Category, Completed, Completion Date, custom logged comments, and earned points.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(
                    onClick = onShareSheets,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("sync_sheets_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Text("Export CSV / Save to Drive", fontWeight = FontWeight.Bold)
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))

                // Cloud Multi-Device Account Sync Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { developerMode = !developerMode },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Cloud Multi-Device Account Sync (Google Sheets)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (developerMode) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Expanding Direct Sheet Sync Controls
                AnimatedVisibility(visible = developerMode) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Authenticate with your Google/Gmail account and point to a shared Spreadsheet ID to instantly synchronize all trackers and activities across multiple devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        OutlinedTextField(
                            value = gmailAccount,
                            onValueChange = { gmailAccount = it },
                            label = { Text("Google/Gmail Account Email", fontSize = 11.sp) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sheets_email_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = sheetsAccessToken,
                            onValueChange = { sheetsAccessToken = it },
                            label = { Text("Google OAuth Bearer Token", fontSize = 11.sp) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sheets_token_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = spreadsheetId,
                            onValueChange = { spreadsheetId = it },
                            label = { Text("Spreadsheet ID (from URL)", fontSize = 11.sp) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sheets_id_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = sheetName,
                            onValueChange = { sheetName = it },
                            label = { Text("Target Sheet/Tab Name (e.g. Sheet1, Trackers)", fontSize = 11.sp) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sheets_name_input"),
                            singleLine = true
                        )

                        Button(
                            onClick = { onDirectSheetsSync(sheetsAccessToken, spreadsheetId, sheetName, gmailAccount) },
                            enabled = !isSyncing && sheetsAccessToken.isNotBlank() && spreadsheetId.isNotBlank() && sheetName.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("direct_sync_button"),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Synchronize Trackers (Two-Way)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressTab(
    progress: UserProgress,
    unlockedBadges: List<UnlockedBadge>
) {
    val predefinedBadges = listOf(
        BadgeInfo("starter_success", "Starter Success", "Complete at least 1 task", Icons.Default.CheckCircle, Color(0xFF4CAF50)),
        BadgeInfo("streak_3", "Consistency King", "Maintain a 3-day streak", Icons.Default.LocalFireDepartment, Color(0xFFFF9800)),
        BadgeInfo("streak_7", "Elite Achiever", "Maintain an elite 7-day streak", Icons.Default.WorkspacePremium, Color(0xFF9C27B0)),
        BadgeInfo("monthly_master", "Monthly Master", "Complete 1 Monthly Target", Icons.Default.EmojiEvents, Color(0xFFFFB300)),
        BadgeInfo("comments_critic", "Comments Critic", "Write comments on 3 completed tasks", Icons.Default.RateReview, Color(0xFF03A9F4)),
        BadgeInfo("task_conqueror", "Task Conqueror", "Complete 10 total tracking tasks", Icons.Default.MilitaryTech, Color(0xFFE91E63))
    )

    // Compute progress points level
    val currentPoints = progress.totalPoints
    val level = (currentPoints / 100) + 1
    val pointsForNextLevel = level * 100
    val pointsInCurrentLevel = currentPoints % 100
    val progressFraction = pointsInCurrentLevel.toFloat() / 100f

    val levelName = when (level) {
        1 -> "Novice Tracker"
        2 -> "Dedicated Logger"
        3 -> "Consistency Guardian"
        4 -> "Milestone Conqueror"
        else -> "Paragon Achiever"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gamification Dashboard Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "LEVEL $level",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = levelName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${progress.longestStreak}d",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Longest Streak",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Level Progress bar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Level Progress",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "$pointsInCurrentLevel / 100 XP",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = progressFraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${progress.totalPoints}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Total XP",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${unlockedBadges.size} / ${predefinedBadges.size}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Badges Unlocked",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${progress.currentStreak} Days",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFFFF5722),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Active Streak",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Trophy Cabinet Header
        Text(
            text = "🏆 TROPHY CABINET",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary
        )

        // Badges grid
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            predefinedBadges.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { badge ->
                        val isUnlocked = unlockedBadges.any { it.id == badge.id }
                        BadgeCard(
                            badge = badge,
                            isUnlocked = isUnlocked,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeCard(
    badge: BadgeInfo,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha = if (isUnlocked) 1f else 0.4f
    val containerColor = if (isUnlocked) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Card(
        modifier = modifier
            .height(136.dp)
            .testTag("badge_card_${badge.id}"),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isUnlocked) BorderStroke(1.dp, badge.color.copy(alpha = 0.6f)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isUnlocked) badge.color.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isUnlocked) badge.icon else Icons.Default.Lock,
                    contentDescription = badge.title,
                    tint = if (isUnlocked) badge.color else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = badge.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )

            Text(
                text = badge.requirement,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isUnlocked) 0.6f else 0.3f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// Data holder for badge layout helper
data class BadgeInfo(
    val id: String,
    val title: String,
    val requirement: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("DAILY_TRACKER") } // "DAILY_TRACKER" or "MONTHLY_TARGET"
    var points by remember { mutableStateOf("10") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add Activity Tracker",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tracker Title") },
                    placeholder = { Text("e.g. Morning Cardio, Finish Chapter") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_task_title_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short Description") },
                    placeholder = { Text("e.g. 30 minutes run on treadmill") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_task_desc_input"),
                    maxLines = 3
                )

                // Category Selection Option Buttons
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Category Type:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { 
                                category = "DAILY_TRACKER" 
                                points = "10"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (category == "DAILY_TRACKER") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (category == "DAILY_TRACKER") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("category_daily_btn"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Daily Tracker", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { 
                                category = "MONTHLY_TARGET" 
                                points = "50"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (category == "MONTHLY_TARGET") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (category == "MONTHLY_TARGET") MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("category_monthly_btn"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Monthly Target", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = points,
                    onValueChange = { points = it },
                    label = { Text("Points Reward (XP)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_task_points_input"),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val pts = points.toIntOrNull() ?: 10
                            if (title.isNotBlank()) {
                                onConfirm(title, description, category, pts)
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_task_confirm_btn")
                    ) {
                        Text("Add Tracker")
                    }
                }
            }
        }
    }
}
