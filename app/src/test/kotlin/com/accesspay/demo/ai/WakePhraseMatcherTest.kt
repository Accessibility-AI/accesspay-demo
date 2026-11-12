package com.accesspay.demo.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseMatcherTest {
    @Test
    fun `matches hello and start access ai variants`() {
        assertTrue(WakePhraseMatcher.matches("Hello Access AI"))
        assertTrue(WakePhraseMatcher.matches("hello, accessai!"))
        assertTrue(WakePhraseMatcher.matches("Start Access AI"))
        assertTrue(WakePhraseMatcher.matches("hey access a i please"))
        assertTrue(WakePhraseMatcher.matches("open access eye"))
    }

    @Test
    fun `ignores unrelated speech`() {
        assertFalse(WakePhraseMatcher.matches("what is my balance"))
        assertFalse(WakePhraseMatcher.matches("send 5000 naira to David"))
        assertFalse(WakePhraseMatcher.matches(""))
    }
}
