package im.autonova.mobile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.autonova.mobile.data.AgentDatabase
import im.autonova.mobile.data.DeviceAuditEvent
import im.autonova.mobile.data.DeviceAuditRemote
import im.autonova.mobile.data.DeviceAuditSynchronizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceAuditSynchronizerInstrumentedTest {
    @Test fun device_actions_write_the_offline_grant_and_activity_before_the_protected_sync() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AgentDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = database.cacheDao(); val synchronizer = DeviceAuditSynchronizer(dao); val transmitted = mutableListOf<DeviceAuditEvent>()
            val event = DeviceAuditEvent("clipboard.import", "Android clipboard", "User approved importing shared text", "APPROVED")
            assertTrue(synchronizer.record(event, DeviceAuditRemote { transmitted += it }))
            val grants = dao.observeCapabilityGrants().first(); val activity = dao.observeActivity().first()
            assertEquals(listOf(event), transmitted)
            assertEquals(1, grants.size); assertEquals("clipboard.import", grants.single().capability); assertEquals("APPROVED", grants.single().status); assertEquals("APPROVED", grants.single().outcome)
            assertEquals(1, activity.size); assertEquals("CAPABILITY_AUDIT", activity.single().eventType); assertEquals("User approved importing shared text", activity.single().detail)
        } finally { database.close() }
    }

    @Test fun background_review_outcomes_remain_cached_when_the_protected_sync_fails() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AgentDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = database.cacheDao(); val synchronizer = DeviceAuditSynchronizer(dao); val transmitted = mutableListOf<DeviceAuditEvent>()
            val completed = DeviceAuditEvent("background.review", "Android WorkManager", "Review completed", "COMPLETED")
            val failed = DeviceAuditEvent("background.review", "Android WorkManager", "Review retry required", "FAILED")
            assertTrue(synchronizer.record(completed, DeviceAuditRemote { transmitted += it }))
            assertFalse(synchronizer.record(failed, DeviceAuditRemote { transmitted += it; error("Temporary network failure") }))
            val grants = dao.observeCapabilityGrants().first(); val activity = dao.observeActivity().first()
            assertEquals(listOf(completed, failed), transmitted)
            assertEquals(setOf("COMPLETED", "FAILED"), grants.map { it.outcome }.toSet())
            assertEquals(2, activity.count { it.eventType == "CAPABILITY_AUDIT" })
        } finally { database.close() }
    }
}
