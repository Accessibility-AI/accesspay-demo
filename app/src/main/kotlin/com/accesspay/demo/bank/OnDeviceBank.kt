package com.accesspay.demo.bank

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale
import kotlin.random.Random

/**
 * A simulated bank inside the app, so the APK works on any phone with only the AccessAI service online.
 * Same seed data and transfer rules as `mock-bank/` (balance, beneficiaries, idempotent retries, insufficient
 * funds, error codes). State lives in memory and resets when the app restarts.
 *
 * AccessAI transfers are checked against the confirmation token's claims: `aid`, `ph` for exactly this
 * recipient and amount, expiry, single-use `jti`, and an Idempotency-Key equal to the `jti`. The signature
 * is not verified here; the token never leaves the phone in this mode. `mock-bank/` verifies it against
 * the service's JWKS, as a real bank backend must.
 */
class OnDeviceBank(private val now: () -> Instant = Instant::now) : Bank {
    private val mutex = Mutex()
    private var balanceMinor = SEED_BALANCE_MINOR
    private val transactions = SEED_TRANSACTIONS.mapIndexed { i, t -> t.copy(id = txnId(i + 1)) }.toMutableList()
    private val receipts = mutableMapOf<String, Pair<TransferRequest, TransferReceipt>>()
    private val usedTokens = mutableSetOf<String>()

    override suspend fun balance(): Balance = mutex.withLock {
        Balance("Main account", balanceMinor, "NGN", timestamp())
    }

    override suspend fun transactions(limit: Int): List<Transaction> = mutex.withLock {
        transactions.asReversed().take(limit)
    }

    override suspend fun beneficiaries(): List<Beneficiary> = SEED_BENEFICIARIES.sortedBy { it.name }

    override suspend fun transfer(request: TransferRequest, idempotencyKey: String): TransferOutcome = mutex.withLock {
        // A retry of a transfer that already succeeded gets the original receipt.
        receipts[idempotencyKey]?.let { (previous, receipt) ->
            return@withLock if (previous.beneficiaryId == request.beneficiaryId && previous.amountMinor == request.amountMinor) {
                TransferOutcome.Completed(receipt)
            } else {
                rejected("idempotency_conflict", "This Idempotency-Key was used for a different transfer")
            }
        }

        if (request.channel == "manual") {
            if (request.confirmationToken != null) return@withLock rejected("invalid_request", "Manual transfers do not carry a confirmation token")
        } else {
            val token = request.confirmationToken
                ?: return@withLock rejected("confirmation_required", "An AccessAI confirmation token is required")
            val claims = claimsOf(token) ?: return@withLock rejected("token_malformed", "Confirmation token could not be read")
            checkClaims(claims, request, idempotencyKey)?.let { return@withLock it }
            // Spent by any attempt that reaches the ledger, including the refusals below.
            usedTokens += claims.string("jti")!!
        }

        val beneficiary = SEED_BENEFICIARIES.firstOrNull { it.id == request.beneficiaryId }
            ?: return@withLock rejected("beneficiary_not_found", "That beneficiary does not exist")
        if (request.amountMinor <= 0) return@withLock rejected("invalid_request", "The amount must be more than zero")
        if (request.amountMinor > balanceMinor) {
            return@withLock rejected("insufficient_funds", "Your balance is too low for this transfer. No money was moved.")
        }

        balanceMinor -= request.amountMinor
        val reference = "AP-${Random.nextInt(1000, 10000)}-${beneficiary.accountLast4}"
        transactions += Transaction(
            id = txnId(transactions.size + 1),
            kind = "debit",
            description = "Transfer to ${beneficiary.name}",
            amountMinor = request.amountMinor,
            reference = reference,
            createdAt = timestamp(),
        )
        val receipt = TransferReceipt(reference, beneficiary.id, request.amountMinor, balanceMinor)
        receipts[idempotencyKey] = request to receipt
        TransferOutcome.Completed(receipt)
    }

    private fun checkClaims(claims: JsonObject, request: TransferRequest, idempotencyKey: String): TransferOutcome? {
        val exp = claims["exp"]?.jsonPrimitive?.longOrNull
            ?: return rejected("token_invalid", "Confirmation token has no expiry")
        if (now().epochSecond > exp + CLOCK_SKEW_SECONDS) return rejected("token_expired", "Confirmation token has expired")
        if (claims.string("aid") != TRANSFER_ACTION_ID) {
            return rejected("token_action_mismatch", "Confirmation token is for a different action")
        }
        if (claims.string("ph") != paramsSha256(request.amountMinor, request.beneficiaryId)) {
            return rejected("token_params_mismatch", "Confirmation token does not match this recipient and amount")
        }
        val jti = claims.string("jti")
        if (jti.isNullOrEmpty()) return rejected("token_invalid", "Confirmation token has no nonce")
        if (jti != idempotencyKey) return rejected("idempotency_key_mismatch", "Idempotency-Key must be the confirmation token's nonce")
        if (jti in usedTokens) return rejected("token_replayed", "This confirmation has already been used")
        return null
    }

    private fun timestamp(): String = now().truncatedTo(ChronoUnit.SECONDS).toString()

    companion object {
        const val TRANSFER_ACTION_ID = "transfer_money"
        private const val CLOCK_SKEW_SECONDS = 5
        private const val DEV_PREFIX = "dev-unsigned."
        private val json = Json

        // Same as mock-bank/mock_bank/db.py.
        private const val SEED_BALANCE_MINOR = 8_425_000L // ₦84,250
        private val SEED_BENEFICIARIES = listOf(
            Beneficiary("ben_001", "David Adeyemi", "GTBank", "2391"),
            Beneficiary("ben_002", "David Okafor", "Access Bank", "7710"),
            Beneficiary("ben_003", "Amaka Obi", "Zenith Bank", "5521"),
            Beneficiary("ben_004", "Chinedu Okeke", "First Bank", "3304"),
            Beneficiary("ben_005", "Halima Bello", "UBA", "8812"),
        )
        private val SEED_TRANSACTIONS = listOf(
            Transaction("", "credit", "Salary from Lagos Logistics Ltd", 25_000_000, createdAt = "2026-10-25T09:00:00Z"),
            Transaction("", "debit", "Transfer to Amaka Obi", 1_500_000, createdAt = "2026-10-27T18:42:00Z"),
            Transaction("", "debit", "Electricity token, Ikeja Electric", 1_000_000, createdAt = "2026-10-29T08:15:00Z"),
            Transaction("", "debit", "MTN airtime", 200_000, createdAt = "2026-11-01T12:30:00Z"),
            Transaction("", "credit", "Transfer from Chinedu Okeke", 500_000, createdAt = "2026-11-03T16:05:00Z"),
        )

        private fun txnId(n: Int) = String.format(Locale.ROOT, "txn_%04d", n)

        private fun rejected(code: String, message: String) = TransferOutcome.Rejected(code, message)

        /** SHA-256 of the canonical JSON the SDK confirmed: keys sorted, no spaces, non-ASCII kept as is. */
        internal fun paramsSha256(amountMinor: Long, beneficiaryId: String): String {
            val canonical = """{"amount_minor":$amountMinor,"recipient":${JsonPrimitive(beneficiaryId)}}"""
            return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { String.format(Locale.ROOT, "%02x", it) }
        }

        /** Payload of a compact JWS, or of the SDK's offline "dev-unsigned." token. */
        internal fun claimsOf(token: String): JsonObject? = runCatching {
            val payload = if (token.startsWith(DEV_PREFIX)) token.removePrefix(DEV_PREFIX) else token.split('.')[1]
            json.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload.trimEnd('=')), Charsets.UTF_8)).jsonObject
        }.getOrNull()

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
