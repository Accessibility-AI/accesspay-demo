package com.accesspay.demo.bank

import com.accessai.core.actions.ParamValue
import com.accessai.core.actions.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeneficiaryMatcherTest {
    private val saved = listOf(
        Beneficiary("ben_001", "David Adeyemi", "GTBank", "2391"),
        Beneficiary("ben_002", "David Okafor", "Access Bank", "7710"),
        Beneficiary("ben_003", "Amaka Obi", "Zenith Bank", "5521"),
    )

    @Test fun `first name shared by two people asks rather than guesses`() {
        val r = BeneficiaryMatcher.resolve("David", saved) as Resolution.Ambiguous
        assertEquals(listOf(ParamValue.Text("ben_001"), ParamValue.Text("ben_002")), r.candidates.map { it.value })
    }

    @Test fun `full name resolves to one beneficiary with spoken digits`() {
        val r = BeneficiaryMatcher.resolve("david adeyemi", saved) as Resolution.Resolved
        assertEquals(ParamValue.Text("ben_001"), r.value)
        assertEquals("David Adeyemi (…2391)", r.label)
        assertEquals("David Adeyemi, account ending 2 3 9 1", r.spokenLabel)
    }

    @Test fun `unique first name and possessive resolve`() {
        assertEquals(ParamValue.Text("ben_003"), (BeneficiaryMatcher.resolve("Amaka's", saved) as Resolution.Resolved).value)
        assertEquals(ParamValue.Text("ben_002"), (BeneficiaryMatcher.resolve("Okafor", saved) as Resolution.Resolved).value)
    }

    @Test fun `unknown or blank names are not found`() {
        assertTrue(BeneficiaryMatcher.resolve("Tunde", saved) is Resolution.NotFound)
        assertTrue(BeneficiaryMatcher.resolve("  ", saved) is Resolution.NotFound)
    }

    @Test fun `partial words do not match`() {
        assertTrue(BeneficiaryMatcher.resolve("Dav", saved) is Resolution.NotFound)
    }
}
