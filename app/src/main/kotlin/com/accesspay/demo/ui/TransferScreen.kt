package com.accesspay.demo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accessai.core.format.Naira
import com.accesspay.demo.AccessPayApp
import com.accesspay.demo.bank.Beneficiary
import com.accesspay.demo.bank.BeneficiaryMatcher
import com.accesspay.demo.bank.NairaInput
import com.accesspay.demo.bank.TransferOutcome
import kotlinx.coroutines.launch

private sealed interface Step {
    data object Form : Step
    data class Review(val beneficiary: Beneficiary, val kobo: Long) : Step
    data object Sending : Step
    data class Done(val beneficiary: Beneficiary, val kobo: Long, val outcome: TransferOutcome) : Step
}

/** Sending money without AccessAI: choose, review, confirm. The bank records these as "manual" transfers. */
@Composable
fun TransferScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val bank = AccessPayApp.bank
    val beneficiaries by bank.beneficiaries.collectAsStateWithLifecycle()
    val balance by bank.balance.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf<Step>(Step.Form) }

    when (val s = step) {
        Step.Form -> ScreenScaffold(title = "Transfer money", onBack = onBack) {
            Text("Recipient", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(Modifier.selectableGroup()) {
                beneficiaries.forEach { b ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(selected = b.id == selectedId, role = Role.RadioButton, onClick = { selectedId = b.id }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RadioButton(selected = b.id == selectedId, onClick = null)
                        Text(BeneficiaryMatcher.displayLabel(b), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.take(16); formError = null },
                label = { Text("Amount in naira") },
                singleLine = true,
                isError = formError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = balance?.let { { Text("Available: ${Naira.display(it.balanceMinor)}") } },
                modifier = Modifier.fillMaxWidth(),
            )

            formError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; error(it) },
                )
            }

            PrimaryAction("Review transfer", onClick = {
                val beneficiary = beneficiaries.firstOrNull { it.id == selectedId }
                val kobo = NairaInput.toKobo(amountText)
                formError = when {
                    beneficiary == null -> "Choose who to send money to."
                    kobo == null -> "Enter an amount in naira, for example 5000."
                    balance != null && kobo > balance!!.balanceMinor -> "That's more than your available balance."
                    else -> null
                }
                if (formError == null) step = Step.Review(beneficiary!!, kobo!!)
            })
        }

        is Step.Review -> ScreenScaffold(title = "Confirm payment", onBack = { step = Step.Form }) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(3.dp, MaterialTheme.colorScheme.outline),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp).semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Send", style = MaterialTheme.typography.bodyLarge)
                    Text(Naira.display(s.kobo), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("to ${s.beneficiary.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${s.beneficiary.bank}, account ending ${s.beneficiary.accountLast4}", style = MaterialTheme.typography.bodyLarge)
                    Text("From Main account. Fee ₦0.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            PrimaryAction("Confirm. Send ${Naira.display(s.kobo)} to ${s.beneficiary.name}", tall = true, onClick = {
                step = Step.Sending
                scope.launch {
                    val outcome = bank.transferManual(s.beneficiary.id, s.kobo)
                    step = Step.Done(s.beneficiary, s.kobo, outcome)
                }
            })
            SecondaryAction("Cancel. Do not send", tall = true, onClick = { step = Step.Form })
        }

        Step.Sending -> ScreenScaffold(title = "Sending", onBack = null) {
            Text(
                "Sending your payment. Please wait.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        is Step.Done -> {
            val (title, banner) = when (val o = s.outcome) {
                is TransferOutcome.Completed -> "Payment sent" to Triple(
                    StatusKind.SUCCESS, "Success",
                    "Sent ${Naira.display(s.kobo)} to ${s.beneficiary.name}. Reference ${o.receipt.reference}. New balance ${Naira.display(o.receipt.balanceMinor)}.",
                )
                is TransferOutcome.Rejected -> "Payment not sent" to Triple(StatusKind.FAILURE, "Failed", "${o.message} No money left your account.")
                is TransferOutcome.Unknown -> "Payment status unknown" to Triple(
                    StatusKind.WARNING, "Not confirmed",
                    "${o.message} Check your transaction history before trying again.",
                )
            }
            ScreenScaffold(title = title, onBack = null) {
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) {
                    StatusBanner(banner.first, banner.second, banner.third)
                }
                PrimaryAction("Done", onClick = {
                    selectedId = null
                    amountText = ""
                    step = Step.Form
                    onDone()
                })
                if (s.outcome is TransferOutcome.Rejected) {
                    SecondaryAction("Try again", onClick = { step = Step.Form })
                }
            }
        }
    }
}
