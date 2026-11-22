package com.example.core.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatUtils {
  private const val LRI = "\u2066"
  private const val RLI = "\u2067"
  private const val PDI = "\u2069"

  fun formatDate(millis: Long, locale: Locale = Locale.getDefault()): String =
    isolateIfUrdu(SimpleDateFormat("dd MMM yyyy", locale).format(Date(millis)), locale)

  fun formatFullDate(millis: Long, locale: Locale = Locale.getDefault()): String =
    isolateIfUrdu(SimpleDateFormat("d MMMM yyyy", locale).format(Date(millis)), locale)

  fun formatShortDate(millis: Long, locale: Locale = Locale.getDefault()): String =
    isolateIfUrdu(SimpleDateFormat("dd MMM", locale).format(Date(millis)), locale)

  fun formatDateTime(millis: Long, locale: Locale = Locale.getDefault()): String {
    val date = Date(millis)
    if (locale.language != "ur") {
      return SimpleDateFormat("dd MMM yyyy, hh:mm a", locale).format(date)
    }
    val datePart = SimpleDateFormat("dd MMM yyyy", locale).format(date)
    val timePart = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(date)
    return "$RLI$datePart$PDI، $LRI$timePart$PDI"
  }

  fun formatFullDateTime(millis: Long, locale: Locale = Locale.getDefault()): String {
    val date = Date(millis)
    if (locale.language != "ur") {
      return SimpleDateFormat("MMMM d, yyyy h:mm a", locale).format(date)
    }
    val datePart = SimpleDateFormat("d MMMM yyyy", locale).format(date)
    val timePart = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(date)
    return "$RLI$datePart$PDI • $LRI$timePart$PDI"
  }

  private fun isolateIfUrdu(formatted: String, locale: Locale): String =
    if (locale.language == "ur" && formatted.isNotBlank()) {
      "$RLI$formatted$PDI"
    } else {
      formatted
    }

  fun formatInches(value: Double, rawOverride: String? = null): String {
    val trimmed = rawOverride?.trim()
    if (!trimmed.isNullOrEmpty()) return "$trimmed\""
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) {
      "${rounded.toInt()}\""
    } else {
      val text = rounded.toString().trimEnd('0').trimEnd('.')
      "$text\""
    }
  }

  fun formatOptionalInches(value: Double, rawOverride: String? = null): String =
    if (value <= 0.0 && rawOverride.isNullOrBlank()) "—" else formatInches(value, rawOverride)
}
