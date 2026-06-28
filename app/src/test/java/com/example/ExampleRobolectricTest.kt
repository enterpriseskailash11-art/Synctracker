package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.viewmodel.ActivityViewModel
import com.example.widget.SyncTrackerWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SyncTracker", appName)
  }

  @Test
  fun `test save and load sync settings persistence`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = ActivityViewModel(application)

    // Save initial values
    viewModel.saveSyncSettings(
      token = "oauth_test_token_123",
      spreadsheetId = "sheet_test_id_abc",
      sheetName = "TasksTab",
      email = "user@example.com"
    )

    // Verify current states are updated
    assertEquals("oauth_test_token_123", viewModel.googleSheetsToken.value)
    assertEquals("sheet_test_id_abc", viewModel.googleSpreadsheetId.value)
    assertEquals("TasksTab", viewModel.googleSheetName.value)
    assertEquals("user@example.com", viewModel.googleUserEmail.value)

    // Instantiate a new ViewModel to verify deep persistence via SharedPreferences
    val secondViewModel = ActivityViewModel(application)
    assertEquals("oauth_test_token_123", secondViewModel.googleSheetsToken.value)
    assertEquals("sheet_test_id_abc", secondViewModel.googleSpreadsheetId.value)
    assertEquals("TasksTab", secondViewModel.googleSheetName.value)
    assertEquals("user@example.com", secondViewModel.googleUserEmail.value)
  }

  @Test
  fun `test widget trigger update action`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // Trigger update and verify it completes without crashing
    try {
      SyncTrackerWidgetProvider.triggerUpdate(context)
    } catch (e: Exception) {
      org.junit.Assert.fail("Widget update trigger threw an exception: " + e.message)
    }
  }
}

