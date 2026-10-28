package de.crunchymate.sync

import android.app.PendingIntent
import android.content.Intent
import de.crunchymate.MainActivity
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.SyncPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val SDK = 35

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK])
class SyncNotificationsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun tapOnNotification_opensMainActivityWithImmutableIntent() {
        val cancel = PendingIntent.getBroadcast(context, 0, Intent("cancel"), PendingIntent.FLAG_IMMUTABLE)
        val notification = SyncNotifications.build(context, SyncKind.CATALOG, SyncPhase.ITEMS, 1L, 2L, cancel)

        val content = notification.contentIntent
        assertNotNull(content)
        val shadow = shadowOf(content)
        assertTrue(shadow.isActivityIntent)
        assertEquals(MainActivity::class.java.name, shadow.savedIntent.component?.className)
        assertTrue(shadow.flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }
}
