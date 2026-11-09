package com.accesspay.demo.bank

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** The app's view of the account. Screens observe it; AccessAI actions read and act through it. */
class BankRepository(private val api: Bank) {
    private val _balance = MutableStateFlow<Balance?>(null)
    val balance: StateFlow<Balance?> = _balance.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _beneficiaries = MutableStateFlow<List<Beneficiary>>(emptyList())
    val beneficiaries: StateFlow<List<Beneficiary>> = _beneficiaries.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Last load failure, shown on screen. Cleared by the next successful refresh. */
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun refresh() {
        try {
            _balance.value = api.balance()
            _transactions.value = api.transactions()
            _beneficiaries.value = api.beneficiaries()
            _error.value = null
        } catch (e: BankException) {
            _error.value = e.message
        }
    }

    /** Fresh balance in kobo. Throws [BankException] if the bank can't be reached. */
    suspend fun currentBalance(): Long = api.balance().also { _balance.value = it }.balanceMinor

    suspend fun recentTransactions(): List<Transaction> = api.transactions().also { _transactions.value = it }

    suspend fun savedBeneficiaries(): List<Beneficiary> =
        _beneficiaries.value.ifEmpty { api.beneficiaries().also { _beneficiaries.value = it } }

    fun beneficiary(id: String): Beneficiary? = _beneficiaries.value.firstOrNull { it.id == id }

    /** A transfer confirmed through AccessAI. The bank verifies [confirmationToken]; [idempotencyKey] is its nonce. */
    suspend fun transferConfirmed(beneficiaryId: String, amountKobo: Long, confirmationToken: String, idempotencyKey: String) =
        transfer(TransferRequest(beneficiaryId, amountKobo, confirmationToken, channel = "accessai"), idempotencyKey)

    /** A transfer the user reviewed and confirmed on the app's own Transfer screen. */
    suspend fun transferManual(beneficiaryId: String, amountKobo: Long, idempotencyKey: String = UUID.randomUUID().toString()) =
        transfer(TransferRequest(beneficiaryId, amountKobo, channel = "manual"), idempotencyKey)

    private suspend fun transfer(request: TransferRequest, idempotencyKey: String): TransferOutcome {
        val outcome = api.transfer(request, idempotencyKey)
        if (outcome is TransferOutcome.Completed) {
            _balance.value = _balance.value?.copy(balanceMinor = outcome.receipt.balanceMinor)
        }
        refresh()
        return outcome
    }
}
