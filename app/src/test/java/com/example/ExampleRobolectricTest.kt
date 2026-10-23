package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    assertEquals("Offline Notes", appName)
  }

  @Test
  fun `checklist serialization roundtrip`() {
    val items = listOf(
      ChecklistItem(text = "Offline item 1", isChecked = true),
      ChecklistItem(text = "Offline item 2", isChecked = false)
    )
    val json = ChecklistItem.listToJson(items)
    val parsed = ChecklistItem.jsonToList(json)

    assertEquals(2, parsed.size)
    assertEquals("Offline item 1", parsed[0].text)
    assertTrue(parsed[0].isChecked)
    assertEquals("Offline item 2", parsed[1].text)
    assertEquals(false, parsed[1].isChecked)
  }
}
