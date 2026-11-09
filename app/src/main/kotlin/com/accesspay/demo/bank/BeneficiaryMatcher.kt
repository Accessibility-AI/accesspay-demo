package com.accesspay.demo.bank

import com.accessai.core.actions.ParamValue
import com.accessai.core.actions.Resolution

/** Turns a name as the user said it into one saved beneficiary, a choice between several, or none. Never guesses. */
object BeneficiaryMatcher {

    fun resolve(query: String, beneficiaries: List<Beneficiary>): Resolution {
        val words = words(query)
        if (words.isEmpty()) return Resolution.NotFound("Please say the name of a saved beneficiary.")
        val matches = beneficiaries.filter { b -> words(b.name).containsAll(words) }
        return when (matches.size) {
            0 -> Resolution.NotFound("I couldn't find ${query.trim()} in your saved beneficiaries.")
            1 -> matches[0].let { Resolution.Resolved(ParamValue.Text(it.id), displayLabel(it), spokenLabel(it)) }
            else -> Resolution.Ambiguous(matches.map { Resolution.Candidate(ParamValue.Text(it.id), displayLabel(it), spokenLabel(it)) })
        }
    }

    fun displayLabel(b: Beneficiary): String = "${b.name} (…${b.accountLast4})"

    fun spokenLabel(b: Beneficiary): String = "${b.name}, account ending ${b.accountLast4.toList().joinToString(" ")}"

    private fun words(text: String): List<String> =
        text.lowercase().replace(Regex("'s\\b"), "").split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
}
