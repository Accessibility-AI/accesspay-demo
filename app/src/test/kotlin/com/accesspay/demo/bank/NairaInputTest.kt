package com.accesspay.demo.bank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NairaInputTest {
    @Test fun `whole and grouped amounts`() {
        assertEquals(500_000L, NairaInput.toKobo("5000"))
        assertEquals(500_000L, NairaInput.toKobo("5,000"))
        assertEquals(500_000L, NairaInput.toKobo(" ₦5,000 "))
    }

    @Test fun `kobo are exact`() {
        assertEquals(500_050L, NairaInput.toKobo("5000.50"))
        assertEquals(500_005L, NairaInput.toKobo("5000.05"))
        assertEquals(500_050L, NairaInput.toKobo("5000.5"))
    }

    @Test fun `invalid amounts are rejected`() {
        listOf("", "0", "0.00", "-5", "5.001", "abc", "5e3", "1234567890").forEach { assertNull(it, NairaInput.toKobo(it)) }
    }
}
