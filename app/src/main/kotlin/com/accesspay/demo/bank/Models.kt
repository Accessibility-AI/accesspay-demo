package com.accesspay.demo.bank

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire models for the mock bank (mock-bank/mock_bank/app.py). Amounts are kobo.

@Serializable
data class Balance(
    @SerialName("account_name") val accountName: String,
    @SerialName("balance_minor") val balanceMinor: Long,
    val currency: String,
    @SerialName("as_of") val asOf: String,
)

@Serializable
data class Transaction(
    val id: String,
    val kind: String,
    val description: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val reference: String? = null,
    @SerialName("created_at") val createdAt: String,
) {
    val isCredit: Boolean get() = kind == "credit"
}

@Serializable
data class Beneficiary(
    val id: String,
    val name: String,
    val bank: String,
    @SerialName("account_last4") val accountLast4: String,
)

@Serializable
internal data class Items<T>(val items: List<T>)

@Serializable
data class TransferRequest(
    @SerialName("beneficiary_id") val beneficiaryId: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("confirmation_token") val confirmationToken: String? = null,
    val channel: String,
)

@Serializable
data class TransferReceipt(
    val reference: String,
    @SerialName("beneficiary_id") val beneficiaryId: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("balance_minor") val balanceMinor: Long,
)

@Serializable
internal data class ErrorBody(val error: Detail) {
    @Serializable
    data class Detail(val code: String, val message: String)
}

sealed interface TransferOutcome {
    data class Completed(val receipt: TransferReceipt) : TransferOutcome

    /** The bank refused, or the request never reached it. No money moved. */
    data class Rejected(val code: String, val message: String) : TransferOutcome

    /** The request may have reached the bank but no answer came back. The result is not known. */
    data class Unknown(val message: String) : TransferOutcome
}

class BankException(val code: String, message: String) : Exception(message)
