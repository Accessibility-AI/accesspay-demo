package com.accesspay.demo.ai

import com.accessai.android.AccessAI
import com.accessai.core.Announcement
import com.accessai.core.actions.ActionResult
import com.accessai.core.actions.ParamValue
import com.accessai.core.actions.Resolution
import com.accessai.core.actions.Sensitivity
import com.accessai.core.actions.Validation
import com.accessai.core.actions.action
import com.accessai.core.announce
import com.accesspay.demo.Navigator
import com.accesspay.demo.Route
import com.accesspay.demo.bank.BankException
import com.accesspay.demo.bank.BankRepository
import com.accesspay.demo.bank.BeneficiaryMatcher
import com.accesspay.demo.bank.TransferOutcome

private val BANK_UNREACHABLE = Announcement.of("I couldn't reach your bank. Please check your connection and try again.")

/** Everything AccessAI may do in AccessPay. Nothing outside this list can ever run. */
fun registerAccessPayActions(bank: BankRepository, navigator: Navigator) {
    AccessAI.registerAction(action("get_balance") {
        name = "check your balance"
        description = "Read the user's available balance"
        examples("what is my balance", "how much money do I have", "check my balance")
        handler {
            try {
                val kobo = bank.currentBalance()
                ActionResult.Success(announce { text("Your balance is "); naira(kobo); text(".") })
            } catch (e: BankException) {
                ActionResult.Failure(BANK_UNREACHABLE)
            }
        }
    })

    AccessAI.registerAction(action("navigate_to_transfer") {
        name = "open transfers"
        description = "Open the Transfer money screen"
        sensitivity = Sensitivity.NAVIGATE
        examples("I want to send money", "open transfers", "go to transfer")
        handler {
            navigator.open(Route.TRANSFER)
            ActionResult.Success(Announcement.of("Transfer money screen. Tell me who to send to and how much."))
        }
    })

    AccessAI.registerAction(action("get_transaction_history") {
        name = "read your recent transactions"
        description = "Read recent transactions one at a time"
        examples("read my recent transactions", "transaction history", "what did I spend recently")
        handler {
            val items = try {
                bank.recentTransactions()
            } catch (e: BankException) {
                return@handler ActionResult.Failure(BANK_UNREACHABLE)
            }
            ActionResult.Success(
                Announcement.of("You have ${items.size} recent transactions. Say next to hear the next one."),
                items = items.map { t ->
                    announce {
                        text(if (t.isCredit) "Received " else "Paid ")
                        naira(t.amountMinor)
                        text(". ${t.description}, ${SpokenDate.of(t.createdAt)}.")
                    }
                },
                emptyMessage = Announcement.of("You have no transactions yet."),
            )
        }
    })

    AccessAI.registerAction(action("transfer_money") {
        name = "send money"
        description = "Send simulated money to a saved beneficiary"
        sensitivity = Sensitivity.SENSITIVE
        confirmPhrase = "confirm transfer"
        text("recipient", "Beneficiary name as the user said it", prompt = "Who would you like to send money to?") { raw ->
            val beneficiaries = try {
                bank.savedBeneficiaries()
            } catch (e: BankException) {
                return@text Resolution.NotFound("I couldn't reach your bank to check your beneficiaries.")
            }
            BeneficiaryMatcher.resolve((raw as ParamValue.Text).value, beneficiaries)
        }
        amount("amount_minor", "Amount in kobo", prompt = "How much would you like to send?")
        examples("send 5000 naira to David", "transfer 2k to Amaka", "pay Amaka Obi 10,000 naira")
        validate { p ->
            val balance = try {
                bank.currentBalance()
            } catch (e: BankException) {
                return@validate Validation.Invalid("I couldn't reach your bank to check your balance. Nothing was sent.")
            }
            if (p.number("amount_minor") > balance) Validation.Invalid("That's more than your balance. What amount would you like to send?")
            else Validation.Valid
        }
        summary { p -> announce { text("send "); naira(p.number("amount_minor")); text(" to ${p.spokenLabel("recipient")}, from your Main account. The fee is zero naira") } }
        handler { ctx ->
            val beneficiaryId = ctx.params.text("recipient")
            val kobo = ctx.params.number("amount_minor")
            // The bank verifies the signed token and uses its nonce as the idempotency key.
            val token = ctx.confirmationToken ?: return@handler ActionResult.Failure(Announcement.of("This payment wasn't confirmed. Nothing was sent."))
            val name = bank.beneficiary(beneficiaryId)?.name ?: "your beneficiary"
            when (val outcome = bank.transferConfirmed(beneficiaryId, kobo, token, ctx.idempotencyKey)) {
                is TransferOutcome.Completed -> ActionResult.Success(announce {
                    text("Payment sent. "); naira(kobo); text(" to $name. Reference ")
                    pair(spokenReference(outcome.receipt.reference), outcome.receipt.reference)
                    text(". Your new balance is "); naira(outcome.receipt.balanceMinor); text(".")
                })
                is TransferOutcome.Rejected -> ActionResult.Failure(rejected(outcome.code))
                is TransferOutcome.Unknown -> ActionResult.Failure(Announcement.of(
                    "Your bank didn't answer, so I can't tell whether the payment went through. Check your transaction history before trying again.",
                ))
            }
        }
    })
}

private fun rejected(code: String): Announcement = Announcement.of(
    when (code) {
        "bank_declined" -> "Payment not sent. Your bank declined this payment. No money left your account."
        "insufficient_funds" -> "Payment not sent. Your balance is too low. No money left your account."
        "token_expired" -> "Payment not sent. The confirmation expired. No money left your account. You can try again."
        "bank_unreachable" -> "Payment not sent. I couldn't reach your bank. No money left your account."
        else -> "Payment not sent. Your bank couldn't complete it. No money left your account."
    },
)

/** "AP-4471-2391" → "A P, 4 4 7 1, 2 3 9 1". */
internal fun spokenReference(reference: String): String =
    reference.split('-').joinToString(", ") { part -> part.toList().joinToString(" ") }
