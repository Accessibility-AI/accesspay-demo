package com.accesspay.demo.bank

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BankApiTest {
    private val server = MockWebServer()
    private lateinit var api: BankApi

    @Before fun setUp() {
        server.start()
        api = BankApi(server.url("/").toString())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `confirmed transfer sends token, nonce and channel`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"reference":"AP-4471-2391","status":"completed","beneficiary_id":"ben_001","amount_minor":500000,"balance_minor":7925000,"channel":"accessai","created_at":"2026-11-05T10:00:00Z","replayed":false}""",
        ))
        val outcome = api.transfer(TransferRequest("ben_001", 500_000, "tok", "accessai"), "nonce-1")
        assertEquals(TransferOutcome.Completed(TransferReceipt("AP-4471-2391", "ben_001", 500_000, 7_925_000)), outcome)

        val sent = server.takeRequest()
        assertEquals("/v1/transfers", sent.path)
        assertEquals("nonce-1", sent.getHeader("Idempotency-Key"))
        val body = sent.body.readUtf8()
        assertTrue(body, body.contains("\"confirmation_token\":\"tok\"") && body.contains("\"amount_minor\":500000") && body.contains("\"channel\":\"accessai\""))
    }

    @Test fun `bank error codes come through`() = runTest {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":{"code":"bank_declined","message":"Declined."}}"""))
        assertEquals(TransferOutcome.Rejected("bank_declined", "Declined."), api.transfer(TransferRequest("ben_001", 1, "t", "accessai"), "k"))
    }

    @Test fun `unparseable error body still fails safely`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))
        assertEquals("http_502", (api.transfer(TransferRequest("ben_001", 1, "t", "accessai"), "k") as TransferOutcome.Rejected).code)
    }

    @Test fun `no response after sending is unknown, not failure`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val fast = BankApi(server.url("/").toString(), BankApi.defaultClient().newBuilder().readTimeout(java.time.Duration.ofMillis(300)).build())
        assertTrue(fast.transfer(TransferRequest("ben_001", 1, "t", "accessai"), "k") is TransferOutcome.Unknown)
    }

    @Test fun `unreachable bank is a rejection`() = runTest {
        server.shutdown()
        val outcome = api.transfer(TransferRequest("ben_001", 1, "t", "accessai"), "k")
        assertEquals("bank_unreachable", (outcome as TransferOutcome.Rejected).code)
    }

    @Test fun `lists unwrap items`() = runTest {
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"ben_001","name":"David Adeyemi","bank":"GTBank","account_last4":"2391"}]}"""))
        assertEquals(listOf(Beneficiary("ben_001", "David Adeyemi", "GTBank", "2391")), api.beneficiaries())
    }
}
