package com.accesspay.demo.bank

/** The account backend: the mock bank over HTTP ([BankApi]) or a simulated bank inside the app ([OnDeviceBank]). */
interface Bank {
    /** Throws [BankException] if the bank can't be reached. */
    suspend fun balance(): Balance

    /** Newest first. Throws [BankException] if the bank can't be reached. */
    suspend fun transactions(limit: Int = 20): List<Transaction>

    /** Throws [BankException] if the bank can't be reached. */
    suspend fun beneficiaries(): List<Beneficiary>

    suspend fun transfer(request: TransferRequest, idempotencyKey: String): TransferOutcome
}
