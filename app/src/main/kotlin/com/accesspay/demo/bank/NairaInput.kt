package com.accesspay.demo.bank

/** Parses an amount typed in naira ("5000", "5,000", "₦5,000.50") to kobo without floating point. */
object NairaInput {
    private val PATTERN = Regex("""^(\d{1,9})(?:\.(\d{1,2}))?$""")

    fun toKobo(input: String): Long? {
        val cleaned = input.trim().removePrefix("₦").replace(",", "").trim()
        val match = PATTERN.matchEntire(cleaned) ?: return null
        val naira = match.groupValues[1].toLong()
        val kobo = match.groupValues[2].padEnd(2, '0').toLong()
        return (naira * 100 + kobo).takeIf { it > 0 }
    }
}
