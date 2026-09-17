package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.permission.PermissionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("Joy", appName)
  }

  @Test
  fun `verify permission manager initialization`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val permissionManager = PermissionManager(context)
    assertNotNull(permissionManager.getMissingPermissions())
  }
}

