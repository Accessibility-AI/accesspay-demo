package com.accesspay.demo.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessAiSessionTrackerTest {
    @Test
    fun `records and clears pending transfer and outcome`() {
        val tracker = AccessAiSessionTracker()
        val pending = PendingTransferDetails(
            amountMinor = 500_000L,
            recipientName = "David Adeyemi",
            accountLast4 = "2391",
        )
        tracker.recordPendingTransfer(pending)
        assertEquals(pending, tracker.pendingTransfer.value)

        val success = AccessAiActionOutcome.TransferSuccess(
            amountMinor = 500_000L,
            recipientName = "David Adeyemi",
            accountLast4 = "2391",
            reference = "AP-4471-2391",
            newBalanceMinor = 7_925_000L,
            time = "2:16 pm",
        )
        tracker.recordOutcome(success)
        assertEquals(success, tracker.outcome.value)

        tracker.clear()
        assertNull(tracker.pendingTransfer.value)
        assertNull(tracker.outcome.value)
    }

    @Test
    fun `spokenReference spaces characters by dash groups`() {
        assertEquals("A P, 4 4 7 1, 2 3 9 1", spokenReference("AP-4471-2391"))
    }

    @Test
    fun `recordOutcome null clears previous outcome without dropping pending transfer`() {
        val tracker = AccessAiSessionTracker()
        val pending = PendingTransferDetails(
            amountMinor = 200_000L,
            recipientName = "Amaka",
            accountLast4 = "1122",
        )
        tracker.recordPendingTransfer(pending)
        tracker.recordOutcome(AccessAiActionOutcome.Balance(500_000L, "Main account", "2:20 pm"))

        tracker.recordOutcome(null)
        assertNull(tracker.outcome.value)
        assertEquals(pending, tracker.pendingTransfer.value)
    }
}
