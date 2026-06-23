package com.mileagetracker.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T-003 tests for the one-shot pending-navigation seam between `MainActivity`'s intent handling
 * and `MileageTrackerNavHost`'s consumption of it. No Android framework dependency here — this
 * store is plain Kotlin/coroutines, so it is tested directly with no fake/mock needed, consistent
 * with this project's "hand-written fakes only, no mocking framework" convention (there is simply
 * nothing here that needs faking).
 */
class PendingTripClassificationNavigationStoreTest {

    @Test
    fun `pendingTripId starts null when nothing has been set`() {
        val store = PendingTripClassificationNavigationStore()

        assertNull(store.pendingTripId.value)
    }

    @Test
    fun `setPendingTripId makes the tripId observable via pendingTripId`() {
        val store = PendingTripClassificationNavigationStore()

        store.setPendingTripId("trip-123")

        assertEquals("trip-123", store.pendingTripId.value)
    }

    @Test
    fun `consumePendingTripId returns the pending value and clears it`() {
        val store = PendingTripClassificationNavigationStore()
        store.setPendingTripId("trip-456")

        val consumedTripId = store.consumePendingTripId()

        assertEquals("trip-456", consumedTripId)
        assertNull(store.pendingTripId.value)
    }

    @Test
    fun `consumePendingTripId returns null when nothing is pending`() {
        val store = PendingTripClassificationNavigationStore()

        val consumedTripId = store.consumePendingTripId()

        assertNull(consumedTripId)
    }

    @Test
    fun `consuming twice in a row only returns the value once`() {
        val store = PendingTripClassificationNavigationStore()
        store.setPendingTripId("trip-789")

        val firstConsume = store.consumePendingTripId()
        val secondConsume = store.consumePendingTripId()

        assertEquals("trip-789", firstConsume)
        assertNull(secondConsume)
    }

    @Test
    fun `setPendingTripId after a consume makes a new value pending again`() {
        val store = PendingTripClassificationNavigationStore()
        store.setPendingTripId("trip-first")
        store.consumePendingTripId()

        store.setPendingTripId("trip-second")

        assertEquals("trip-second", store.pendingTripId.value)
    }

    @Test
    fun `setPendingTripId overwrites a not-yet-consumed value with the latest tripId`() {
        val store = PendingTripClassificationNavigationStore()
        store.setPendingTripId("trip-stale")

        store.setPendingTripId("trip-fresh")

        assertEquals("trip-fresh", store.pendingTripId.value)
    }
}
