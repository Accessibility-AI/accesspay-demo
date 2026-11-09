package com.accesspay.demo.ai

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Dates as people say them: "3 November". Falls back to the raw text if it can't be parsed. */
object SpokenDate {
    private val format = DateTimeFormatter.ofPattern("d MMMM", Locale.UK)

    fun of(iso: String, zone: ZoneId = ZoneId.of("Africa/Lagos")): String =
        try {
            format.format(Instant.parse(iso).atZone(zone))
        } catch (e: DateTimeParseException) {
            iso
        }
}
