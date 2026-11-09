package com.accesspay.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    @Test fun `back walks the stack and stops at home`() {
        val nav = Navigator()
        nav.open(Route.BENEFICIARIES)
        nav.open(Route.TRANSFER)
        assertTrue(nav.back())
        assertEquals(Route.BENEFICIARIES, nav.route.value)
        assertTrue(nav.back())
        assertEquals(Route.HOME, nav.route.value)
        assertFalse(nav.back())
    }

    @Test fun `reopening a screen does not duplicate it`() {
        val nav = Navigator()
        nav.open(Route.TRANSFER)
        nav.open(Route.HISTORY)
        nav.open(Route.TRANSFER)
        nav.back()
        assertEquals(Route.HISTORY, nav.route.value)
    }
}
