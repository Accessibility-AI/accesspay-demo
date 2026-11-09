package com.accesspay.demo.bank

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Base64

class OnDeviceBankTest {
    private var now = Instant.parse("2026-11-09T12:00:00Z")
    private val bank = OnDeviceBank { now }

    private fun token(
        amountMinor: Long = 500_000,
        recipient: String = "ben_001",
        jti: String = "nonce-1",
        aid: String = "transfer_money",
        exp: Long = now.epochSecond + 60,
        prefix: String = "eyJhbGciOiJFZERTQSJ9.",
        suffix: String = ".c2ln",
    ): String {
        val claims = """{"iss":"accessai","aid":"$aid","ph":"${OnDeviceBank.paramsSha256(amountMinor, recipient)}","jti":"$jti","exp":$exp}"""
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray()) + suffix
    }

    private suspend fun send(token: String?, key: String = "nonce-1", amountMinor: Long = 500_000, recipient: String = "ben_001") =
        bank.transfer(TransferRequest(recipient, amountMinor, token, if (token == null) "manual" else "accessai"), key)

    private fun TransferOutcome.code() = (this as TransferOutcome.Rejected).code

    @Test fun `seed data matches the mock bank`() = runTest {
        assertEquals(8_425_000, bank.balance().balanceMinor)
        assertEquals(listOf("Amaka Obi", "Chinedu Okeke", "David Adeyemi", "David Okafor", "Halima Bello"), bank.beneficiaries().map { it.name })
        assertEquals("Transfer from Chinedu Okeke", bank.transactions().first().description)
        assertEquals(5, bank.transactions().size)
    }

    @Test fun `params hash matches the SDK reference vector`() {
        assertEquals("b729a280eabf6c63b6a6b889bfa1b26d4dad23286f2e3a36b98028e35879cd47", OnDeviceBank.paramsSha256(500_000, "ben_001"))
    }

    @Test fun `confirmed transfer moves money once and records it`() = runTest {
        val outcome = send(token()) as TransferOutcome.Completed
        assertEquals(7_925_000, outcome.receipt.balanceMinor)
        assertTrue(outcome.receipt.reference.matches(Regex("AP-\\d{4}-2391")))
        assertEquals(7_925_000, bank.balance().balanceMinor)
        val latest = bank.transactions().first()
        assertEquals("Transfer to David Adeyemi", latest.description)
        assertEquals(outcome.receipt.reference, latest.reference)
    }

    @Test fun `retry with the same key returns the original receipt without charging again`() = runTest {
        val first = send(token())
        assertEquals(first, send(token()))
        assertEquals(7_925_000, bank.balance().balanceMinor)
    }

    @Test fun `token is single use even under a different key`() = runTest {
        send(token())
        assertEquals("idempotency_key_mismatch", send(token(), key = "other").code())
    }

    @Test fun `token must match recipient, amount, action and expiry`() = runTest {
        assertEquals("token_params_mismatch", send(token(), amountMinor = 5_000_000).code())
        assertEquals("token_params_mismatch", send(token(), recipient = "ben_002").code())
        assertEquals("token_action_mismatch", send(token(aid = "pay_bill")).code())
        assertEquals("token_expired", send(token(exp = now.epochSecond - 60)).code())
        assertEquals("token_malformed", send("not-a-token").code())
        assertEquals("confirmation_required", bank.transfer(TransferRequest("ben_001", 500_000, null, "accessai"), "k").code())
        assertEquals(8_425_000, bank.balance().balanceMinor)
    }

    @Test fun `offline SDK dev tokens are accepted`() = runTest {
        assertTrue(send(token(prefix = "dev-unsigned.", suffix = "")) is TransferOutcome.Completed)
    }

    @Test fun `insufficient funds spends the token but moves nothing`() = runTest {
        assertEquals("insufficient_funds", send(token(amountMinor = 9_000_000), amountMinor = 9_000_000).code())
        assertEquals("token_replayed", send(token(amountMinor = 9_000_000), amountMinor = 9_000_000, key = "nonce-1").code())
        assertEquals(8_425_000, bank.balance().balanceMinor)
    }

    @Test fun `manual transfers need no token`() = runTest {
        assertTrue(send(null, key = "manual-1", amountMinor = 100_000, recipient = "ben_003") is TransferOutcome.Completed)
        assertEquals(8_325_000, bank.balance().balanceMinor)
    }
}
