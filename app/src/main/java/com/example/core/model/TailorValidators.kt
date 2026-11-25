package com.example.core.model

import java.util.Locale

enum class MeasurementFieldKey(
  val testTag: String,
  val isRequired: Boolean,
) {
  KAMEEZ_LAMBAI("meas_kameez_lambai", isRequired = true),
  TERA("meas_tera", isRequired = true),
  BAZU("meas_bazu", isRequired = true),
  GALA_COLLAR("meas_gala_collar", isRequired = true),
  CHATI("meas_chati", isRequired = true),
  GHERA_DAMAN("meas_ghera_daman", isRequired = true),
  SHALWAR_LAMBAI("meas_shalwar_lambai", isRequired = true),
  PAINCHA("meas_paincha", isRequired = true),
  KAMAR("meas_kamar", isRequired = false),
  CUFF("meas_cuff", isRequired = false),
  GHERA_ASAN("meas_ghera_asan", isRequired = false),
  SHALWAR_GHAIR("meas_shalwar_ghair", isRequired = false);

  fun label(urdu: Boolean): String = when (this) {
    KAMEEZ_LAMBAI -> if (urdu) "قمیض لمبائی" else "Kameez Lambai"
    TERA -> if (urdu) "تیرا" else "Teera"
    BAZU -> if (urdu) "آستین" else "Asteen"
    GALA_COLLAR -> if (urdu) "گلا" else "Gala"
    CHATI -> if (urdu) "سینہ" else "Seena"
    GHERA_DAMAN -> if (urdu) "گھیرا / دامن" else "Ghera / Daman"
    SHALWAR_LAMBAI -> if (urdu) "شلوار" else "Shalwar"
    PAINCHA -> if (urdu) "پانچہ" else "Poncha"
    KAMAR -> if (urdu) "کمر" else "Kamar"
    CUFF -> if (urdu) "کف" else "Cuff"
    GHERA_ASAN -> if (urdu) "آسن" else "Asan"
    SHALWAR_GHAIR -> if (urdu) "شلوار گھیرا" else "Shalwar Ghair"
  }
}

object TailorValidators {

  data class ParsedMeasurementDetails(
    val primary: Double,
    val secondary: Double? = null,
    val isDualSlash: Boolean = false,
  )

  private fun isUrdu(): Boolean = Locale.getDefault().language == "ur"

  private val wholeAndFractionRegex = Regex("^(\\d+(?:\\.\\d+)?)[\\s-]+(\\d+/\\d+)$")
  private val dualSlashRegex = Regex("^(\\d+(?:\\.\\d+)?)/(\\d+(?:\\.\\d+)?)$")
  private val commonKeypadFractions = setOf("1/4", "1/2", "3/4")

  fun parseMeasurementDetails(raw: String): ParsedMeasurementDetails? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    if (!trimmed.contains('/') && !trimmed.contains(' ') && !trimmed.contains('-')) {
      if (trimmed.endsWith('.')) return null
      val value = trimmed.toDoubleOrNull() ?: return null
      if (!value.isFinite() || value < 0.0) return null
      return ParsedMeasurementDetails(primary = value)
    }

    if (!trimmed.contains(' ') && !trimmed.contains('-') && trimmed.contains('/')) {
      if (trimmed in commonKeypadFractions) {
        val frac = parseFractionPart(trimmed) ?: return null
        return ParsedMeasurementDetails(primary = frac)
      }
      val dualMatch = dualSlashRegex.matchEntire(trimmed) ?: return null
      val first = dualMatch.groupValues[1].toDoubleOrNull() ?: return null
      val second = dualMatch.groupValues[2].toDoubleOrNull() ?: return null
      if (!first.isFinite() || !second.isFinite() || first < 0.0 || second <= 0.0) {
        return null
      }
      return ParsedMeasurementDetails(
        primary = first,
        secondary = second,
        isDualSlash = true,
      )
    }

    val match = wholeAndFractionRegex.matchEntire(trimmed) ?: return null
    val whole = match.groupValues[1].toDoubleOrNull() ?: return null
    val frac = parseFractionPart(match.groupValues[2]) ?: return null
    val sum = whole + frac
    if (!sum.isFinite() || sum < 0.0) return null
    return ParsedMeasurementDetails(primary = sum)
  }

  fun parseMeasurementInches(raw: String): Double? =
    parseMeasurementDetails(raw)?.primary

  fun normalizeRawDualValue(raw: String): String? {
    val trimmed = raw.trim()
    val details = parseMeasurementDetails(trimmed) ?: return null
    return if (details.isDualSlash) trimmed else null
  }

  fun buildRawValuesMap(entries: List<Pair<MeasurementFieldKey, String>>): Map<String, String> =
    buildMap {
      entries.forEach { (key, raw) ->
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return@forEach
        val details = parseMeasurementDetails(trimmed) ?: return@forEach
        when {
          details.isDualSlash -> put(key.name, trimmed)
          details.primary == 0.0 -> put(key.name, "0")
        }
      }
    }

  private fun parseFractionPart(part: String): Double? {
    val pieces = part.split('/')
    if (pieces.size != 2) return null
    val numerator = pieces[0].toDoubleOrNull() ?: return null
    val denominator = pieces[1].toDoubleOrNull() ?: return null
    if (denominator <= 0.0 || numerator < 0.0 || !numerator.isFinite() || !denominator.isFinite()) return null
    return numerator / denominator
  }

  fun validateMeasurementFieldInput(
    key: MeasurementFieldKey,
    rawText: String,
    allowBlankRequired: Boolean = true,
  ): String? {
    val urdu = isUrdu()
    val trimmed = rawText.trim()
    val label = key.label(urdu)

    if (trimmed.isEmpty()) {
      if (!key.isRequired || allowBlankRequired) return null
      return if (urdu) "$label درج کرنا لازمی ہے۔" else "$label is required."
    }

    val details = parseMeasurementDetails(trimmed)
      ?: return if (urdu) {
        "درست عدد درج کریں (مثلاً 0، 41.5 یا 8/10)۔"
      } else {
        "Enter a valid value (e.g. 0, 41.5, or 8/10)."
      }

    if (details.primary < 0.0) {
      return if (urdu) {
        "$label منفی نہیں ہو سکتا۔"
      } else {
        "$label cannot be negative."
      }
    }
    return null
  }

  fun validateCustomer(name: String, phone: String): String? {
    if (name.trim().length < 2) {
      return if (isUrdu()) {
        "گاہک کا نام کم از کم 2 حروف کا ہونا چاہیے۔"
      } else {
        "Customer name must be at least 2 characters."
      }
    }
    if (phone.isNotBlank() && !PhoneNumberUtils.isValidPakistaniPhone(phone)) {
      return if (isUrdu()) {
        "درست پاکستانی موبائل نمبر درج کریں (مثلاً 1234567-0300)۔"
      } else {
        "Enter a valid Pakistani phone number (e.g., 0300-1234567)."
      }
    }
    return null
  }

  fun validateMeasurement(snapshot: MeasurementSnapshot): String? {
    val urdu = isUrdu()
    val fields = listOf(
      MeasurementFieldKey.KAMEEZ_LAMBAI to snapshot.kameezLambai,
      MeasurementFieldKey.TERA to snapshot.tera,
      MeasurementFieldKey.BAZU to snapshot.bazu,
      MeasurementFieldKey.GALA_COLLAR to snapshot.galaCollar,
      MeasurementFieldKey.CHATI to snapshot.chati,
      MeasurementFieldKey.GHERA_DAMAN to snapshot.gheraDaman,
      MeasurementFieldKey.SHALWAR_LAMBAI to snapshot.shalwarLambai,
      MeasurementFieldKey.PAINCHA to snapshot.paincha,
      MeasurementFieldKey.KAMAR to snapshot.kamar,
      MeasurementFieldKey.CUFF to snapshot.cuff,
      MeasurementFieldKey.GHERA_ASAN to snapshot.gheraAsan,
      MeasurementFieldKey.SHALWAR_GHAIR to snapshot.shalwarGhair,
    )
    for ((key, value) in fields) {
      if (value.isNaN() || value.isInfinite() || value < 0.0) {
        val label = key.label(urdu)
        return if (urdu) {
          "$label 0 یا اس سے زیادہ ہونا چاہیے۔"
        } else {
          "$label must be 0 or greater."
        }
      }
    }
    return null
  }

  fun validateNewOrder(
    suitCount: Int,
    totalAmountPkr: Long,
    initialAdvancePkr: Long,
  ): String? {
    val urdu = isUrdu()
    if (suitCount < 1 || suitCount > 100) {
      return if (urdu) {
        "سوٹ کی تعداد 1 سے 100 کے درمیان ہونی چاہیے۔"
      } else {
        "Suit count must be between 1 and 100."
      }
    }
    if (totalAmountPkr < 0L) {
      return if (urdu) "کل رقم منفی نہیں ہو سکتی۔" else "Total amount cannot be negative."
    }
    if (initialAdvancePkr < 0L) {
      return if (urdu) "پیشگی ادائیگی منفی نہیں ہو سکتی۔" else "Advance payment cannot be negative."
    }
    if (initialAdvancePkr > totalAmountPkr) {
      return if (urdu) {
        "پیشگی ادائیگی کل بل سے زیادہ نہیں ہو سکتی۔"
      } else {
        "Advance payment cannot exceed total bill."
      }
    }
    return null
  }

  fun validatePayment(
    amountPkr: Long,
    remainingBalancePkr: Long,
  ): String? {
    val urdu = isUrdu()
    if (amountPkr <= 0L) {
      return if (urdu) {
        "ادائیگی کی رقم 0 روپے سے زیادہ ہونی چاہیے۔"
      } else {
        "Payment amount must be greater than Rs. 0."
      }
    }
    if (amountPkr > remainingBalancePkr) {
      return if (urdu) {
        "ادائیگی بقایا رقم ($remainingBalancePkr روپے) سے زیادہ نہیں ہو سکتی۔"
      } else {
        "Payment cannot exceed remaining balance of Rs. $remainingBalancePkr."
      }
    }
    return null
  }
}
