package com.accesspay.demo.bank

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** HTTP client for the mock bank. */
class BankApi(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
) : Bank {
    private val base = baseUrl.trimEnd('/')

    override suspend fun balance(): Balance = get("/v1/balance")

    override suspend fun transactions(limit: Int): List<Transaction> = get<Items<Transaction>>("/v1/transactions?limit=$limit").items

    override suspend fun beneficiaries(): List<Beneficiary> = get<Items<Beneficiary>>("/v1/beneficiaries").items

    override suspend fun transfer(request: TransferRequest, idempotencyKey: String): TransferOutcome = withContext(Dispatchers.IO) {
        val call = Request.Builder()
            .url("$base/v1/transfers")
            .header("Idempotency-Key", idempotencyKey)
            .post(json.encodeToString(TransferRequest.serializer(), request).toRequestBody(JSON))
            .build()
        try {
            client.newCall(call).execute().use { response ->
                if (response.isSuccessful) TransferOutcome.Completed(json.decodeFromString(TransferReceipt.serializer(), response.body!!.string()))
                else response.error().let { TransferOutcome.Rejected(it.code, it.message) }
            }
        } catch (e: ConnectException) {
            TransferOutcome.Rejected("bank_unreachable", "Couldn't reach the bank.")
        } catch (e: UnknownHostException) {
            TransferOutcome.Rejected("bank_unreachable", "Couldn't reach the bank.")
        } catch (e: IOException) {
            // Timed out or dropped after sending: the bank may or may not have processed it.
            TransferOutcome.Unknown("The bank didn't answer in time.")
        }
    }

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(Request.Builder().url(base + path).get().build()).execute()
        } catch (e: IOException) {
            throw BankException("bank_unreachable", "Couldn't reach the bank.")
        }
        response.use {
            if (!it.isSuccessful) it.error().let { e -> throw BankException(e.code, e.message) }
            json.decodeFromString<T>(it.body!!.string())
        }
    }

    private fun Response.error(): ErrorBody.Detail =
        runCatching { json.decodeFromString(ErrorBody.serializer(), body!!.string()).error }
            .getOrElse { ErrorBody.Detail("http_$code", "The bank returned an unexpected error.") }

    companion object {
        private val JSON = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        // Shorter than the SDK's 20 s action timeout, so a slow bank is reported rather than abandoned.
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(17, TimeUnit.SECONDS)
            .build()
    }
}
