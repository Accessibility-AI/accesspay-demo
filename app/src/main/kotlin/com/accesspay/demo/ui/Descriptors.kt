package com.accesspay.demo.ui

import com.accessai.core.format.Naira
import com.accessai.core.screen.ScreenDescriptor
import com.accessai.core.screen.ScreenElement
import com.accesspay.demo.Route
import com.accesspay.demo.bank.Balance
import com.accesspay.demo.bank.Beneficiary
import com.accesspay.demo.bank.Transaction

/**
 * What AccessAI knows about each screen. Element values (balance, names) are read aloud on the phone only;
 * the SDK strips them before anything is sent to the AccessAI service.
 */
fun descriptorFor(route: Route, balance: Balance?, transactions: List<Transaction>, beneficiaries: List<Beneficiary>): ScreenDescriptor =
    when (route) {
        Route.HOME -> ScreenDescriptor(
            screenId = "home",
            title = "Home",
            summary = "Your Main account",
            elements = listOf(
                ScreenElement("balance", "text", "Available balance", value = balance?.let { Naira.display(it.balanceMinor) }),
                ScreenElement("accessai", "button", "Turn on AccessAI accessibility mode"),
                ScreenElement("send", "button", "Send money"),
                ScreenElement("history", "button", "Transaction history"),
                ScreenElement("beneficiaries", "button", "Beneficiaries"),
            ),
        )
        Route.TRANSFER -> ScreenDescriptor(
            screenId = "transfer",
            title = "Transfer money",
            elements = listOf(
                ScreenElement("recipient", "input", "Recipient", required = true),
                ScreenElement("amount", "input", "Amount in naira", required = true),
                ScreenElement("review", "button", "Review transfer"),
            ),
        )
        Route.HISTORY -> ScreenDescriptor(
            screenId = "history",
            title = "Transaction history",
            elements = listOf(ScreenElement("transactions", "list", "Recent transactions", value = "${transactions.size} items")),
        )
        Route.BENEFICIARIES -> ScreenDescriptor(
            screenId = "beneficiaries",
            title = "Beneficiaries",
            elements = listOf(
                ScreenElement("beneficiaries", "list", "Saved beneficiaries", value = beneficiaries.joinToString { it.name }),
            ),
        )
    }
