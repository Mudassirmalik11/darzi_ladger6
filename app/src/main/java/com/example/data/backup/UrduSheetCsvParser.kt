package com.example.data.backup

import com.example.core.model.DamanStyle
import com.example.core.model.NeckStyle
import com.example.core.model.PhoneNumberUtils
import com.example.core.model.PocketStyle
import com.example.core.model.ShalwarType
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.SnapshotJson
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.repository.ShopRepository
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object UrduSheetCsvParser {

  private val numberRegex = Regex("""\d+(?:\.\d+)?""")
  private val diacriticsRegex = Regex("""[\u064b-\u065f\u0670]""")
  private val spaceDotRegex = Regex("""(\d+)\s+\.(\d+)""")
  private val rtlFiveDotRegex = Regex("""5\s*[،,]?\s*۔\s*(\d+)""")
  private val rtlFiveAsciiDotRegex = Regex("""(?:^|(?<=[\s/;،by]))5\.(\d{2,})(?=$|[^\d])""")
  private val phoneInExtraRegex = Regex("""(?<!\d)(?:\+?92|0)?3(?:\s*\d){9}(?!\d)""")
  private val phoneStripRegex = Regex("""(?<!\d)(?:\+?92|0)?3(?:\s*\d){9}(?!\d)""")
  private val multiSpaceRegex = Regex("""\s+""")

  private val sleeveNotePatterns = listOf(
    Regex("""گول\s*بولی\s*(?:سوا|پونے|پونی)?\s*\d*(?:\.\d+)?"""),
    Regex("""بولی\s*(?:سوا|پونے|پونی)?\s*\d*(?:\.\d+)?"""),
    Regex("""کٹ\s*والے(?:\s*کف)?"""),
    Regex("""کف\s*کٹ\s*والے"""),
    Regex("""لوڑھی\s*والے"""),
    Regex("""لہوری\s*والے"""),
    Regex("""گول\s*کف|گول\b"""),
    Regex("""کف\s*بند|بند\s*کف"""),
    Regex("""کف\s*بولی"""),
    Regex("""ڈبل\s*چاک\s*پٹی"""),
    Regex("""(?:تیار\s*)?چاک\s*پٹی\s*(?:پر\s*)?کاچ"""),
    Regex("""(?:تیار\s*)?چاک\s*پٹی(?:\s*\d+(?:\.\d+)?)?"""),
    Regex("""کف\s*نوک\s*والے"""),
    Regex("""کرتا\s*کنی(?:\s*والا)?"""),
    Regex("""چوڑ[اے]?\s*(?:کف\s*)?(?:سوا|پونے|پونی)?\s*\d+(?:\.\d+)?(?:\s*(?:انیچ|اینچ)(?:\s*تیار)?|\s*تیار)?"""),
    Regex("""تین\s*انیچ\s*چوڑا"""),
    Regex("""تین\s*انیچ\s*کے"""),
    Regex("""کف\s*پونے\s*2\s*انیچ\s*تیار"""),
    Regex("""شولڈر"""),
  )

  private val galaNotePatterns = listOf(
    Regex("""چھو\s*ٹی\s*(?:نوک)?|چوٹی\s*نوک|نوک\s*چھوٹی"""),
    Regex("""ہاف\s*کالر"""),
    Regex("""چھوٹی\s*کالر"""),
    Regex("""ادھ[ےا]?\s*(?:انیچ|اینچ)\s*کی\s*بین"""),
    Regex("""\d+(?:\.\d+)?\s*(?:انیچ|اینچ)\s*(?:نوک(?:\s*تیار)?|تیار)?"""),
    Regex("""(?:سوا|آدھا)\s*(?:انیچ|اینچ)"""),
    Regex("""پونی\s*پٹی"""),
    Regex("""مگزی|معزی"""),
    Regex("""بین\s*ڈبل|ڈبل\s*بین|ڈبل"""),
    Regex("""فل\s*بین|بین\s*فل"""),
    Regex("""سوادی"""),
    Regex("""گول"""),
    Regex("""چورس"""),
  )

  fun convertCsvToBackupJson(
    rawCsv: String,
    compactJson: Json,
    nowMillis: Long = System.currentTimeMillis(),
    sequentialIdPrefix: String? = null,
  ): Result<String> {
    val cleaned = rawCsv.removePrefix("\uFEFF").trim()
    if (cleaned.isEmpty()) {
      return Result.failure(IllegalArgumentException("Selected CSV file is empty."))
    }

    val rows = parseCsvRows(cleaned).filter { row -> row.any { it.isNotBlank() } }
    if (rows.size < 2) {
      return Result.failure(IllegalArgumentException("CSV must contain a header row and at least one customer row."))
    }

    val header = rows.first().map { it.trim().lowercase() }
    fun findCol(vararg aliases: String): Int =
      header.indexOfFirst { col -> aliases.any { alias -> col == alias || col.contains(alias) } }

    val phoneCol = findCol("فون", "موبائل", "phone", "mobile", "contact")
    val nameCol = header.indexOfFirst { col ->
      (col == "نام" || (col.contains("نام") && !col.contains("کسٹمر"))) || col == "name" || col == "customer"
    }.let { if (it >= 0) it else findCol("نام", "name", "customer") }
    val customIdCol = header.indexOfFirst { col ->
      col == "کسٹمر آئی ڈی" || col.contains("آئی ڈی") || col == "customid" || col == "custom_id" ||
        col == "serial" || col == "no" || col == "no2"
    }.let { idx ->
      if (idx >= 0) {
        idx
      } else {
        header.indexOfFirst { col ->
          col != header.getOrNull(phoneCol) && (col.contains("نمبر") || col.contains("number") || col == "id")
        }
      }
    }
    val notesCol = findCol("نوٹ", "پتہ", "address", "note")
    val lambaiCol = findCol("لمبائی", "lambai", "kameez")
    val teraCol = findCol("تیرا", "tera", "teera")
    val bazuCol = findCol("آستین", "بازو", "bazu", "asteen", "sleeve")
    val cuffCol = findCol("کف", "cuff")
    val galaCol = findCol("گلا", "کالر", "gala", "collar", "neck")
    val chatiCol = findCol("سینہ", "چھاتی", "chati", "seena", "chest")
    val kamarCol = findCol("کمر", "kamar", "waist")
    val damanCol = findCol("دامن", "گھیرا", "daman", "ghera")
    val shalwarCol = findCol("شلوار", "shalwar")
    val painchaCol = findCol("پانچہ", "پونچہ", "paincha", "poncha")
    val asanCol = findCol("آسن", "asan")
    val ghairCol = findCol("شلوار گھیرا", "ghair")

    if (nameCol < 0) {
      return Result.failure(
        IllegalArgumentException("Could not find a customer Name column (نام / Name) in CSV header."),
      )
    }

    val knownCols = setOf(
      customIdCol,
      nameCol,
      phoneCol,
      notesCol,
      lambaiCol,
      teraCol,
      bazuCol,
      cuffCol,
      galaCol,
      chatiCol,
      kamarCol,
      damanCol,
      shalwarCol,
      painchaCol,
      asanCol,
      ghairCol,
    ).filter { it >= 0 }.toSet()

    fun List<String>.cell(index: Int): String =
      if (index in indices) this[index].trim() else ""

    val customers = mutableListOf<CustomerEntity>()
    val measurements = mutableListOf<MeasurementEntity>()

    rows.drop(1).forEachIndexed { idx, row ->
      val name = row.cell(nameCol).take(100)
      if (name.isBlank()) return@forEachIndexed

      val customId = row.cell(customIdCol).take(40)
      val phoneCell = row.cell(phoneCol)
      var rawPhone = ""
      val customerNotesParts = mutableListOf<String>()
      val explicitNote = row.cell(notesCol)
      if (explicitNote.isNotBlank()) customerNotesParts.add(explicitNote)

      val extraTexts = mutableListOf<String>()
      fun processPhoneOrExtraCell(cellText: String) {
        val strippedForEmpty = diacriticsRegex.replace(cellText, "").trim()
        if (strippedForEmpty.isEmpty() || strippedForEmpty == "." || strippedForEmpty == "," || strippedForEmpty == "hhh" || strippedForEmpty == "4" || strippedForEmpty == "ی" || strippedForEmpty == "نن" || strippedForEmpty == "ن") {
          return
        }
        val phoneMatches = phoneStripRegex.findAll(cellText)
          .map { it.value.replace(multiSpaceRegex, "") }
          .toList()
        if (phoneMatches.isNotEmpty()) {
          val secondaryList = if (rawPhone.isBlank()) {
            rawPhone = phoneMatches.first()
            phoneMatches.drop(1)
          } else {
            phoneMatches
          }
          if (secondaryList.isNotEmpty()) {
            val secondaryPhones = secondaryList.joinToString(", ") {
              PhoneNumberUtils.formatPakistaniPhone(it)
            }
            customerNotesParts.add("فون 2: $secondaryPhones")
          }
          val remainder = cellText
            .replace(phoneStripRegex, "")
            .replace(Regex("""فون\s*نمبر"""), "")
            .replace(multiSpaceRegex, " ")
            .trim(' ', '/', '،', '۔', '(', ')')
          if (remainder.isNotBlank() && remainder !in name && remainder != "نمبر") {
            extraTexts.add(remainder)
          }
        } else {
          val compactDigits = cellText.replace(Regex("""[\s\-]+"""), "")
          when {
            Regex("""^0?3\d{8}$""").matches(compactDigits) -> {
              customerNotesParts.add("فون نامکمل: $compactDigits")
            }
            Regex("""^3\d{10}$""").matches(compactDigits) -> {
              if (rawPhone.isBlank()) rawPhone = compactDigits
              customerNotesParts.add("فون نمبر چیک کریں (بغیر 0، 11 ہندسے): $compactDigits")
            }
            else -> extraTexts.add(cellText)
          }
        }
      }

      val cLambai = row.cell(lambaiCol)
      val cTera = row.cell(teraCol)
      val cBazu = row.cell(bazuCol)
      val cGala = row.cell(galaCol)
      val cChati = row.cell(chatiCol)
      val cDaman = row.cell(damanCol)
      val cShalwar = row.cell(shalwarCol)
      val cPaincha = row.cell(painchaCol)

      val rightShiftedEmptyTera = cTera.isEmpty() &&
        (cGala.contains("کرتا") || cGala.contains("کف") || cGala.contains("مکف")) &&
        (cChati.contains("بین") || cChati.contains("کالر") || cChati.contains("گول"))

      val shiftedTera = !rightShiftedEmptyTera && (
        cTera.contains("کف") ||
          cTera.contains("کرتا") ||
          cTera.contains("مکف") ||
          cBazu.contains("کالر") ||
          cBazu.contains("بین") ||
          cBazu.contains("کار")
        ) && !cChati.contains("سینہ") && !cGala.contains("بین") && !cGala.contains("کالر")

      val shiftedGala = !rightShiftedEmptyTera && !shiftedTera && cPaincha.isEmpty() && (
        cGala.contains("سینہ") ||
          cGala.contains("چھاتی") ||
          (
            (extractNumber(cGala) ?: 0.0) >= 20.0 &&
              (extractNumber(cChati) ?: 0.0) in 10.0..19.9 &&
              (extractNumber(cShalwar) ?: 0.0) in 3.0..12.0
            )
        )

      if (phoneCell.isNotEmpty()) {
        processPhoneOrExtraCell(phoneCell)
      }

      val shiftedPainchaCol = if (rightShiftedEmptyTera && painchaCol >= 0) painchaCol + 1 else -1
      row.forEachIndexed { colIdx, rawCell ->
        val cellText = rawCell.trim()
        if (cellText.isEmpty() || colIdx in knownCols || colIdx == shiftedPainchaCol) return@forEachIndexed
        processPhoneOrExtraCell(cellText)
      }
      if (shiftedTera && cPaincha.isNotEmpty()) {
        processPhoneOrExtraCell(cPaincha)
      }

      val formattedPhone = if (rawPhone.isBlank()) "" else PhoneNumberUtils.formatPakistaniPhone(rawPhone).take(30)
      val normalizedPhone = if (rawPhone.isBlank()) "" else PhoneNumberUtils.normalizePakistaniPhone(rawPhone).take(30)
      val rowTs = nowMillis - ((rows.size - idx).toLong() * 1000L)
      val seqIdx = customers.size + 1
      val customerId = if (sequentialIdPrefix != null) {
        "${sequentialIdPrefix}_cust_${seqIdx.toString().padStart(3, '0')}"
      } else {
        "csv_${UUID.nameUUIDFromBytes("$idx|$customId|$name|$normalizedPhone".toByteArray(Charsets.UTF_8))}"
      }

      if (shiftedTera) {
        val parts = buildList {
          if (cLambai.isNotBlank()) add("لمبائی ${normalizeRtlNumbers(cLambai)}")
          if (cTera.isNotBlank()) add("آستین ${normalizeRtlNumbers(cTera)}")
          if (cBazu.isNotBlank()) add("گلا ${normalizeRtlNumbers(cBazu)}")
          if (cGala.isNotBlank()) add("سینہ ${normalizeRtlNumbers(cGala)}")
          if (cChati.isNotBlank()) add("گھیرا ${normalizeRtlNumbers(cChati)}")
          if (cDaman.isNotBlank()) add("شلوار ${normalizeRtlNumbers(cDaman)}")
          if (cShalwar.isNotBlank()) add("پانچہ ${normalizeRtlNumbers(cShalwar)}")
          extraTexts.forEach { add(normalizeRtlNumbers(it)) }
        }
        customerNotesParts.add("ناپ نامکمل (تیرا درج نہیں): ${parts.joinToString("، ")}")
        val customerNotes = customerNotesParts
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .distinct()
          .joinToString(" • ")
          .take(500)

        customers.add(
          CustomerEntity(
            customerId = customerId,
            shopId = ShopRepository.DEFAULT_SHOP_ID,
            name = name,
            phone = formattedPhone,
            normalizedPhone = normalizedPhone,
            notes = customerNotes,
            customId = customId,
            createdAt = rowTs,
            updatedAt = rowTs,
            version = 1L,
            lastModifiedByDevice = "csv-import",
            isDeleted = false,
            deletedAt = null,
          ),
        )
        return@forEachIndexed
      }

      val pockets = linkedSetOf<PocketStyle>()
      val measNotes = mutableListOf<String>()
      var shalwarType: ShalwarType? = null
      var silaiStyle: SilaiStyle? = null
      var shalwarGhair = extractNumber(row.cell(ghairCol)) ?: 0.0

      val effectiveTera = if (rightShiftedEmptyTera) cBazu else cTera
      val effectiveBazu = if (rightShiftedEmptyTera) cGala else cBazu
      val effectiveGala = when {
        rightShiftedEmptyTera -> cChati
        shiftedGala -> ""
        else -> cGala
      }
      val effectiveChati = when {
        rightShiftedEmptyTera -> cDaman
        shiftedGala -> cGala
        else -> cChati
      }
      val effectiveDaman = when {
        rightShiftedEmptyTera -> cShalwar
        shiftedGala -> cChati
        else -> cDaman
      }
      val effectiveShalwar = when {
        rightShiftedEmptyTera -> cPaincha
        shiftedGala -> cDaman
        else -> cShalwar
      }
      val effectivePaincha = when {
        rightShiftedEmptyTera -> row.cell(shiftedPainchaCol)
        shiftedGala -> cShalwar
        else -> cPaincha
      }
      if (shiftedGala) {
        measNotes.add("گلا شیٹ میں درج نہیں")
      }

      // 1. Kameez Lambai
      val parenLambaiMatch = Regex("""^\(([^)]+)\)\s*(.+)$""").matchEntire(cLambai)
      val lambai = when {
        cLambai.equals("May-38", ignoreCase = true) -> {
          measNotes.add("قمیض شیٹ میں: May-38 (38.5 درج کیا)")
          38.5
        }
        cLambai == ",," -> 0.0
        parenLambaiMatch != null -> extractNumber(parenLambaiMatch.groupValues[2])?.takeIf { it in 0.1..200.0 } ?: 0.0
        else -> extractNumber(cLambai)?.takeIf { it in 0.1..200.0 } ?: 0.0
      }
      val hasOneSideL = cLambai.contains("سیٹ جیب") || cLambai.contains("سائیٹ جیب")
      val hasFrontL = (cLambai.contains("سامنے") && cLambai.contains("جیب")) ||
        ((cLambai.contains("جیب") || cLambai.contains("جب")) && !hasOneSideL)
      if (hasFrontL) pockets.add(PocketStyle.FRONT)
      if (hasOneSideL) pockets.add(PocketStyle.ONE_SIDE)
      val cLambaiClean = if (parenLambaiMatch != null) {
        normalizeRtlNumbers(parenLambaiMatch.groupValues[1]).trim()
      } else {
        normalizeRtlNumbers(cLambai)
          .replace(Regex("""^(?:ایک\s*سامنے\s*ایک\s*سائیٹ\s*جیب|یک\s*سامنے\s*ایک\s*سائیٹ\s*جیب|جیب)\s*"""), "")
          .replace(Regex("""^\d+(?:\.\d+)?\s*"""), "")
          .replace(Regex("""^(?:ایک\s*سامنے\s*ایک\s*سائیٹ\s*جیب|یک\s*سامنے\s*ایک\s*سائیٹ\s*جیب|جیب)\s*"""), "")
          .trim()
      }
      if (!cLambai.equals("May-38", ignoreCase = true) && cLambaiClean.isNotEmpty() && cLambaiClean != ",," && cLambaiClean != "جیب" && cLambaiClean != "جیب۔" && cLambaiClean != "جیب حے" && cLambaiClean != "شلڈ") {
        measNotes.add("قمیض: $cLambaiClean")
      }

      // 2. Tera
      val tera = extractNumber(effectiveTera)?.takeIf { it in 0.1..200.0 } ?: 0.0
      if (effectiveTera.contains("ایک سیٹ جیب")) pockets.add(PocketStyle.ONE_SIDE)
      if (effectiveTera.contains("ڈون")) measNotes.add("ڈون تیرا")
      if (effectiveTera.contains("بلوچی تیرا")) measNotes.add("بلوچی تیرا")
      if (effectiveTera.contains("چاک پٹی کاچ")) measNotes.add("چاک پٹی کاچ")
      if (effectiveTera.contains("نارمل")) measNotes.add("تیرا: نارمل")
      if (effectiveTera.contains("تیرے پر نام نہیں")) measNotes.add("تیرے پر نام نہیں")
      if (effectiveTera.contains("سامنے پٹی سوا اینچ")) measNotes.add("سامنے پٹی سوا اینچ")
      if (effectiveTera.contains("ٹیچ فینسی")) measNotes.add("ٹیچ فینسی")

      // 3. Asteen + Cuff + SleeveStyle
      val explicitCuff = extractNumber(row.cell(cuffCol))?.takeIf { it in 0.0..200.0 }
      val parsedSleeve = parseAsteen(effectiveBazu)
      val bazu = parsedSleeve.bazu.takeIf { it in 0.0..200.0 } ?: 0.0
      val cuff = (explicitCuff ?: parsedSleeve.cuff).takeIf { it in 0.0..200.0 } ?: 0.0
      var sleeveStyle = parsedSleeve.sleeveStyle
      if (effectiveTera.contains("کرتا")) {
        if (sleeveStyle == null) sleeveStyle = SleeveStyle.KURTA
        measNotes.add(normalizeRtlNumbers(effectiveTera).replace(Regex("""^\d+(?:\.\d+)?\s*"""), "").trim())
      }
      if (effectiveBazu.contains("سیٹ جیب") || effectiveBazu.contains("سائیٹ جیب")) {
        pockets.add(PocketStyle.ONE_SIDE)
      }
      measNotes.addAll(parsedSleeve.notes)

      // 4. Gala + NeckStyle
      val parsedGala = parseGala(effectiveGala)
      val gala = parsedGala.gala.takeIf { it in 0.0..200.0 } ?: 0.0
      val neckStyle = parsedGala.neckStyle
      measNotes.addAll(parsedGala.notes)

      // 5. Chati
      val normChati = normalizeRtlNumbers(effectiveChati)
      val armChestMatch = Regex("""(?:آرمول|آڑمول|اڑ\s*مول|مول)\s*\d+(?:\.\d+)?\s+(\d+(?:\.\d+)?)""").find(normChati)
      val chati = if (armChestMatch != null) {
        armChestMatch.groupValues[1].toDoubleOrNull()?.takeIf { it in 0.1..200.0 } ?: 0.0
      } else {
        extractNumber(normChati)?.takeIf { it in 0.1..200.0 } ?: 0.0
      }
      if (normChati.contains("آرمول") || normChati.contains("آڑمول") || normChati.contains("اڑ مول") || normChati.contains("مول") || normChati.contains("تیار")) {
        val armNote = normChati
          .replace(Regex("""\b${Regex.escape(formatNum(chati))}\b"""), "")
          .replace(Regex("""(?:سینہ|سنیہ|سین)"""), "")
          .replace(multiSpaceRegex, " ")
          .trim()
        if (armNote.isNotEmpty()) measNotes.add(armNote)
      }

      // 6. Kamar / Asan
      val kamar = extractNumber(row.cell(kamarCol))?.takeIf { it in 0.0..200.0 } ?: 0.0
      val asan = extractNumber(row.cell(asanCol))?.takeIf { it in 0.0..200.0 } ?: 0.0

      // 7. Ghera / Daman (detect missing-ghera shifted rows like "37.5 جیب" + "8.5")
      val gheraPrimaryTest = extractNumber(effectiveDaman) ?: 0.0
      val shalwarNumTest = extractNumber(effectiveShalwar) ?: 0.0
      val missingGheraShifted = !shiftedGala && gheraPrimaryTest > 30.0 && (effectiveDaman.contains("جیب") || shalwarNumTest in 4.0..14.0)
      val parsedGhera = if (missingGheraShifted) {
        ParsedGhera(0.0, null, emptyMap(), false, false, emptyList())
      } else {
        parseGhera(effectiveDaman)
      }
      val daman = parsedGhera.primary.takeIf { it in 0.0..200.0 } ?: 0.0
      val damanStyle = parsedGhera.damanStyle
      if (parsedGhera.hasOneSidePocket) pockets.add(PocketStyle.ONE_SIDE)
      if (parsedGhera.hasTwoSidePocket) pockets.add(PocketStyle.TWO_SIDE)
      if (Regex("""ڈبل\s*سلا|ڈٖبل\s*سلا|ڈبلسلا""").containsMatchIn(effectiveDaman)) {
        silaiStyle = SilaiStyle.DOUBLE
      }
      measNotes.addAll(parsedGhera.notes)

      // 8. Shalwar + Paincha
      var shalwar = 0.0
      if (effectiveShalwar.isNotEmpty() && effectiveShalwar != "." && !missingGheraShifted) {
        val shalJanMatch = Regex("""^(\d{1,2})-Jan$""", RegexOption.IGNORE_CASE).matchEntire(effectiveShalwar)
        if (shalJanMatch != null) {
          shalwar = shalJanMatch.groupValues[1].toDoubleOrNull()?.takeIf { it in 0.0..200.0 } ?: 0.0
          measNotes.add("شلوار شیٹ میں: $effectiveShalwar (${formatNum(shalwar)} درج کیا)")
        } else if (
          effectiveShalwar.contains("کمیز") ||
          effectiveShalwar.contains("کنیز") ||
          effectiveShalwar.contains("شیلٹ") ||
          effectiveShalwar.contains("شلیٹ") ||
          effectiveShalwar.contains("شلڈ") ||
          effectivePaincha == "کمیز" ||
          effectiveShalwar.contains("سیٹ جیب لمبی") ||
          effectiveShalwar.contains("طوپ")
        ) {
          if (effectiveShalwar.contains("ایک سیٹ جیب") || effectiveShalwar.contains("سیٹ جیب")) {
            pockets.add(PocketStyle.ONE_SIDE)
          }
          measNotes.add(if (effectiveShalwar == "کنیز") "کمیز" else normalizeRtlNumbers(effectiveShalwar))
        } else {
          if (effectiveShalwar.contains("سماٹ") || effectiveShalwar.contains("سمات")) {
            shalwarType = ShalwarType.SMART
          } else if (effectiveShalwar.contains("پنجامہ") || effectiveShalwar.contains("پنجا مہ") || effectiveShalwar.contains("ببچامہ")) {
            shalwarType = ShalwarType.PANJAMA
          }
          val preGhairMatch = Regex("""^(\d+(?:\.\d+)?)\s*گھیر\s+(\d+(?:\.\d+)?)""").find(normalizeRtlNumbers(effectiveShalwar))
          if (preGhairMatch != null) {
            preGhairMatch.groupValues[1].toDoubleOrNull()?.takeIf { it in 0.0..200.0 }?.let {
              shalwarGhair = it
            }
          } else if (effectiveShalwar.contains("گھیر")) {
            Regex("""گھیر\s*(\d+(?:\.\d+)?)""").find(effectiveShalwar)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let {
              if (it in 0.0..200.0) shalwarGhair = it
            }
          }
          if (effectiveShalwar.contains("جیب") || effectiveShalwar.contains("بیب") || effectiveShalwar.contains("ج یب")) {
            pockets.add(PocketStyle.SHALWAR_POCKET)
          }
          val twoPocketMatch = Regex("""2جیب\s*(\d+(?:\.\d+)?)""").find(normalizeRtlNumbers(effectiveShalwar))
          if (preGhairMatch != null) {
            shalwar = preGhairMatch.groupValues[2].toDoubleOrNull()?.takeIf { it in 0.0..200.0 } ?: 0.0
          } else if (twoPocketMatch != null) {
            shalwar = twoPocketMatch.groupValues[1].toDoubleOrNull()?.takeIf { it in 0.0..200.0 } ?: 0.0
            measNotes.add("شلوار: 2 جیب")
          } else if (effectiveShalwar == "418جیب") {
            shalwar = 41.0
            measNotes.add("شلوار شیٹ میں: 418جیب (41 درج کیا)")
          } else {
            shalwar = extractNumber(effectiveShalwar)?.takeIf { it in 0.0..200.0 } ?: 0.0
          }
          listOf(
            "مچھی کنڈا",
            "مچلی کنڈا",
            "مچھلی کنڈا",
            "کلی بالے والی",
            "کلی بالے",
            "بکرم والا",
            "جیب پاسے تے",
            "پنچاہ پر انیچ",
            "پونے 2 انیچ پٹی",
            "پٹی سوا انیچ",
            "کپڑے والے پائینچہ",
            "مکمل",
          ).forEach { kw ->
            if (effectiveShalwar.contains(kw)) measNotes.add(kw)
          }
        }
      }

      var paincha = 0.0
      if (effectivePaincha.isNotEmpty() && !missingGheraShifted) {
        when (effectivePaincha) {
          "8-Jan" -> {
            paincha = 8.0
            measNotes.add("پانچہ شیٹ میں: 8-Jan (8 درج کیا)")
          }
          "£", "#ERROR!" -> {
            paincha = 0.0
            measNotes.add("پانچہ شیٹ میں: $effectivePaincha")
          }
          "کمیز" -> {
            paincha = 0.0
            measNotes.add("کمیز")
          }
          "ٹیچ" -> {
            paincha = 0.0
            measNotes.add("ٹیچ")
          }
          "فل گھیر" -> {
            paincha = 0.0
            measNotes.add("فل گھیر")
          }
          else -> {
            val painchaGhairMatch = Regex("""^(?:شلوار\s*)?(?:گھیر|گیھر|گیر|گھر)\s*(\d+(?:\.\d+)?)""").find(normalizeRtlNumbers(effectivePaincha))
            if (painchaGhairMatch != null) {
              painchaGhairMatch.groupValues[1].toDoubleOrNull()?.takeIf { it in 0.0..200.0 }?.let {
                shalwarGhair = it
              }
              paincha = 0.0
            } else {
              paincha = extractNumber(effectivePaincha)?.takeIf { it in 0.0..200.0 } ?: 0.0
            }
            if (effectivePaincha.contains("سماٹ")) shalwarType = ShalwarType.SMART
            if (effectivePaincha.contains("جیب")) pockets.add(PocketStyle.SHALWAR_POCKET)
            if (effectivePaincha.contains("مچھی کنڈا") || effectivePaincha.contains("مچھلی کنڈا")) {
              measNotes.add("مچھلی کنڈا")
            }
            if (Regex("""ڈبل\s*سلا|ڈٖبل\s*سلا|ڈبلسلا""").containsMatchIn(effectivePaincha)) {
              silaiStyle = SilaiStyle.DOUBLE
              measNotes.add(normalizeRtlNumbers(effectivePaincha))
            }
            if (effectivePaincha.contains("مکمل") && effectivePaincha.contains("گھیر")) {
              measNotes.add("مکمل گھیر")
            }
            if (effectivePaincha.contains("سلائی 1/3")) measNotes.add("سلائی 1/3")
            if (effectivePaincha.contains("ٹیچ فینسی") || effectivePaincha.contains("ٹیچ فیسنی")) measNotes.add("ٹیچ فینسی")
          }
        }
      }

      // 9. Extra trailing columns
      extraTexts.forEach { rawEx ->
        val ex = normalizeRtlNumbers(rawEx)
        if (ex.contains("سماٹ") && shalwarType == null) {
          shalwarType = ShalwarType.SMART
        } else if (ex.contains("پنجامہ") && shalwarType == null) {
          shalwarType = ShalwarType.PANJAMA
        }
        if (ex.contains("سیٹ جیب") || ex.contains("سائیٹ جیب")) {
          pockets.add(PocketStyle.ONE_SIDE)
        }
        if (ex.contains("جیب") && ex.contains("پنجامہ")) {
          pockets.add(PocketStyle.SHALWAR_POCKET)
        }
        if (shalwarGhair == 0.0) {
          val mg1 = Regex("""(?:شلوار\s*)?(?:گھیر|گیھر|گیر|گھر)\s*(?:[^\d]*\s+)?(\d+(?:\.\d+)?)""").find(ex)
          val mg2 = Regex("""(\d+(?:\.\d+)?)\s*(?:شلوار\s*)?(?:گھیر|گیھر|گیر|گھر)""").find(ex)
          val gVal = (mg1 ?: mg2)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
          if (gVal != null && gVal in 0.0..200.0) {
            shalwarGhair = gVal
          }
        }
        if (Regex("""ڈبل\s*سلا|ڈٖبل\s*سلا|ڈبلسلا|ڈبل\s*سلا|سلائی\s*ڈبل""").containsMatchIn(ex)) {
          silaiStyle = SilaiStyle.DOUBLE
        } else if (Regex("""سنگل\s*سلا|سنگلسلا""").containsMatchIn(ex)) {
          silaiStyle = SilaiStyle.SINGLE
        }
        if (ex != ".") {
          measNotes.add(ex)
        }
      }

      val dedupNotes = measNotes
        .map { it.replace(multiSpaceRegex, " ").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" • ")
        .take(500)

      val hasValidMeasurement = lambai > 0.0 && tera > 0.0 && bazu > 0.0 && chati > 0.0 && daman > 0.0
      if (!hasValidMeasurement) {
        if (missingGheraShifted) {
          val parts = buildList {
            if (cLambai.isNotBlank()) add("لمبائی ${normalizeRtlNumbers(cLambai)}")
            if (cTera.isNotBlank()) add("تیرا ${normalizeRtlNumbers(cTera)}")
            if (cBazu.isNotBlank()) add("آستین ${normalizeRtlNumbers(cBazu)}")
            if (cGala.isNotBlank()) add("گلا ${normalizeRtlNumbers(cGala)}")
            if (cChati.isNotBlank()) add("سینہ ${normalizeRtlNumbers(cChati)}")
            if (cDaman.isNotBlank()) add("شلوار ${normalizeRtlNumbers(cDaman)}")
            if (cShalwar.isNotBlank()) add("پانچہ ${normalizeRtlNumbers(cShalwar)}")
            if (cPaincha.isNotBlank()) add(normalizeRtlNumbers(cPaincha))
          }
          customerNotesParts.add("ناپ نامکمل (گھیرا درج نہیں): ${parts.joinToString("، ")}")
        } else if (tera > 0.0 || bazu > 0.0 || chati > 0.0 || shalwar > 0.0) {
          val detailSuffix = buildList {
            if (gala > 0.0) add("گلا ${normalizeRtlNumbers(effectiveGala)}")
            if (dedupNotes.isNotBlank()) add(dedupNotes)
          }.joinToString("، ")
          val tail = if (detailSuffix.isNotEmpty()) "، $detailSuffix" else ""
          customerNotesParts.add(
            "ناپ نامکمل: لمبائی ${formatNum(lambai)}، تیرا ${formatNum(tera)}، آستین ${formatNum(bazu)}، سینہ ${formatNum(chati)}، گھیرا ${normalizeRtlNumbers(effectiveDaman)}، شلوار ${formatNum(shalwar)}، پانچہ ${formatNum(paincha)}$tail",
          )
        }
      }

      val customerNotes = customerNotesParts
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" • ")
        .take(500)

      customers.add(
        CustomerEntity(
          customerId = customerId,
          shopId = ShopRepository.DEFAULT_SHOP_ID,
          name = name,
          phone = formattedPhone,
          normalizedPhone = normalizedPhone,
          notes = customerNotes,
          customId = customId,
          createdAt = rowTs,
          updatedAt = rowTs,
          version = 1L,
          lastModifiedByDevice = "csv-import",
          isDeleted = false,
          deletedAt = null,
        ),
      )

      if (hasValidMeasurement) {
        val measurementId = if (sequentialIdPrefix != null) {
          "${sequentialIdPrefix}_m_${seqIdx.toString().padStart(3, '0')}"
        } else {
          "csv_m_${UUID.nameUUIDFromBytes("m|$customerId".toByteArray(Charsets.UTF_8))}"
        }
        val rawInputsJson = SnapshotJson.encodeRawMap(parsedGhera.rawMap).take(500)
        measurements.add(
          MeasurementEntity(
            measurementId = measurementId,
            customerId = customerId,
            label = "Standard",
            isCurrent = true,
            kameezLambai = lambai,
            tera = tera,
            bazu = bazu,
            chati = chati,
            kamar = kamar,
            gheraDaman = daman,
            galaCollar = gala,
            cuff = cuff,
            shalwarLambai = shalwar,
            paincha = paincha,
            gheraAsan = asan,
            shalwarGhair = shalwarGhair,
            neckStyle = neckStyle,
            damanStyle = damanStyle,
            pocketStyles = pockets,
            sleeveStyle = sleeveStyle,
            silaiStyle = silaiStyle,
            shalwarType = shalwarType,
            rawInputsJson = rawInputsJson,
            notes = dedupNotes,
            createdAt = rowTs,
            updatedAt = rowTs,
            version = 1L,
            lastModifiedByDevice = "csv-import",
            isDeleted = false,
          ),
        )
      }
    }

    if (customers.isEmpty()) {
      return Result.failure(IllegalArgumentException("No valid customer rows were found in the CSV file."))
    }

    val bundle = DarziBackupBundle(
      schemaVersion = ShopBackupRepository.CURRENT_SCHEMA_VERSION,
      appId = ShopBackupRepository.APP_IDENTIFIER,
      exportedAtMillis = nowMillis,
      shop = null,
      preferences = null,
      customers = customers,
      measurements = measurements,
      orders = emptyList(),
      payments = emptyList(),
    )
    return Result.success(compactJson.encodeToString(bundle))
  }

  private data class ParsedSleeve(
    val bazu: Double,
    val cuff: Double,
    val sleeveStyle: SleeveStyle?,
    val notes: List<String>,
  )

  private data class ParsedGala(
    val gala: Double,
    val neckStyle: NeckStyle?,
    val notes: List<String>,
  )

  private data class ParsedGhera(
    val primary: Double,
    val damanStyle: DamanStyle?,
    val rawMap: Map<String, String>,
    val hasOneSidePocket: Boolean,
    val hasTwoSidePocket: Boolean,
    val notes: List<String>,
  )

  private fun parseAsteen(raw: String): ParsedSleeve {
    val s = raw.trim()
    if (s.isEmpty()) return ParsedSleeve(0.0, 0.0, null, emptyList())
    when (s) {
      "24 10 کف پونے" -> return ParsedSleeve(24.0, 9.75, SleeveStyle.KAF, listOf("آستین: 24 کف پونے 10"))
      "9 23.5.5 کف" -> return ParsedSleeve(23.5, 9.0, SleeveStyle.KAF, listOf("آستین شیٹ میں: 9 23.5.5 کف"))
      "25.5کف95" -> return ParsedSleeve(25.5, 9.5, SleeveStyle.KAF, listOf("کف شیٹ میں: 95 (9.5 درج کیا)"))
      "23.5 کف 5سوا 9" -> return ParsedSleeve(23.5, 9.25, SleeveStyle.KAF, listOf("آستین شیٹ میں: 23.5 کف 5سوا 9"))
      "5 کرتا 22 بولی 5.5" -> return ParsedSleeve(22.0, 0.0, SleeveStyle.KURTA, listOf("5 کرتا بولی 5.5"))
      "25کف پونے" -> return ParsedSleeve(25.0, 0.0, SleeveStyle.KAF, listOf("کف پونے"))
      "23.5 کف.5. 9" -> return ParsedSleeve(23.5, 9.5, SleeveStyle.KAF, listOf("آستین شیٹ میں: 23.5 کف.5. 9"))
      "23کف 3اینچ" -> return ParsedSleeve(23.0, 0.0, SleeveStyle.KAF, listOf("کف 3 اینچ چوڑا"))
      "24کف2اینچ" -> return ParsedSleeve(24.0, 0.0, SleeveStyle.KAF, listOf("کف 2 اینچ چوڑا"))
      "22 کف5،۔8" -> return ParsedSleeve(22.0, 8.5, SleeveStyle.KAF, emptyList())
      "25کف5.9" -> return ParsedSleeve(25.0, 9.5, SleeveStyle.KAF, listOf("کف شیٹ میں: 5.9 (9.5 درج کیا)"))
      "22 کف پونے" -> return ParsedSleeve(22.0, 0.0, SleeveStyle.KAF, listOf("کف پونے"))
    }
    val janMatch = Regex("""^(\d{1,2})-Jan$""", RegexOption.IGNORE_CASE).matchEntire(s)
    if (janMatch != null) {
      val day = janMatch.groupValues[1].toDoubleOrNull() ?: 0.0
      return ParsedSleeve(day, 0.0, null, listOf("آستین شیٹ میں: $s (${formatNum(day)} درج کیا)"))
    }

    val preNormalized = s
      .replace("بن دکف", "بند کف")
      .replace("مکف", "کف")
      .replace("ہونے", "پونے")
      .replace(Regex("""(\d)\s*ک(?=\d|5۔|۔|\s*\d)"""), "$1 کف ")
    val normalized = normalizeRtlNumbers(preNormalized)
    val hasKaf = normalized.contains("کف")
    val hasKurta = normalized.contains("کرتا") || normalized.contains("کر تا")
    var sleeveStyle = when {
      hasKaf -> SleeveStyle.KAF
      hasKurta -> SleeveStyle.KURTA
      else -> null
    }
    val nums = numberRegex.findAll(normalized).mapNotNull { it.value.toDoubleOrNull() }.toList()
    var bazu = nums.firstOrNull() ?: 0.0
    var cuff = 0.0
    val extraNotes = mutableListOf<String>()

    if (hasKaf) {
      val afterKaf = normalized.substringAfter("کف")
      val choraMatch = Regex("""(?:پر\s*)?چوڑ[اے]?\s*(?:سوا|پونے)?\s*\d+(?:\.\d+)?\s*(?:انیچ|اینچ)?\s*(\d+(?:\.\d+)?)""").find(afterKaf)
      if (choraMatch != null) {
        cuff = choraMatch.groupValues[1].toDoubleOrNull() ?: 0.0
      } else {
        val cuffMatch = Regex("""(سوا|پونے|پونی|سو)?\s*(\d+(?:\.\d+)?)\s*(سوا|پونے|پونی)?\s*(انیچ|اینچ|انچ)?""").find(afterKaf)
        if (cuffMatch != null) {
          var v = cuffMatch.groupValues[2].toDoubleOrNull() ?: 0.0
          var mod = cuffMatch.groupValues[1] + cuffMatch.groupValues[3]
          if (mod.isEmpty()) {
            val preMod = Regex("""(سوا|پونے|پونی)\s*$""").find(normalized.substringBefore("کف"))?.groupValues?.get(1)
            if (preMod != null) mod = preMod
          }
          val unit = cuffMatch.groupValues[4]
          val beforeMatch = afterKaf.substring(0, cuffMatch.range.first)
          if (unit.isNotEmpty() || v < 4.0) {
            extraNotes.add("کف ${formatNum(v)} اینچ چوڑا")
            v = 0.0
          } else if (v > 12.5) {
            v = 0.0
          } else if (beforeMatch.contains("بولی") || beforeMatch.contains("چوڑ") || beforeMatch.contains("اینچ")) {
            v = 0.0
          } else {
            if (mod.contains("سوا") || mod.contains("سو")) v += 0.25
            else if (mod.contains("پون")) v = (v - 0.25).coerceAtLeast(0.0)
          }
          cuff = v
        }
      }
      if (cuff > 13.0 && bazu < 13.0) {
        val tmp = bazu
        bazu = cuff
        var swappedCuff = tmp
        val beforeKaf = normalized.substringBefore("کف")
        if (beforeKaf.contains("سوا")) swappedCuff += 0.25
        else if (beforeKaf.contains("پون")) swappedCuff = (swappedCuff - 0.25).coerceAtLeast(0.0)
        cuff = swappedCuff
      } else if (cuff == 0.0 && nums.size >= 2) {
        val large = nums.firstOrNull { it >= 12.0 }
        val small = nums.firstOrNull { it in 4.0..12.5 }
        if (large != null && small != null) {
          bazu = large
          var v = small
          val beforeKaf = normalized.substringBefore("کف")
          if (beforeKaf.contains("سوا")) v += 0.25
          else if (beforeKaf.contains("پون")) v = (v - 0.25).coerceAtLeast(0.0)
          cuff = v
        }
      } else if (bazu < 13.0 && nums.size >= 2) {
        val large = nums.firstOrNull { it >= 13.0 }
        val small = nums.firstOrNull { it in 4.0..12.5 }
        if (large != null && small != null) {
          bazu = large
          var v = small
          val beforeKaf = normalized.substringBefore("کف")
          if (beforeKaf.contains("سوا")) v += 0.25
          else if (beforeKaf.contains("پون")) v = (v - 0.25).coerceAtLeast(0.0)
          cuff = v
        }
      }
    } else if (!hasKurta && nums.size >= 2 && nums[0] in 12.0..40.0 && nums[1] in 4.0..12.5) {
      var v = nums[1]
      if (Regex("""(?:سوا|سو)(?=\s*\d)|\bسوا\b""").containsMatchIn(normalized)) v += 0.25
      else if (normalized.contains("پون")) v = (v - 0.25).coerceAtLeast(0.0)
      cuff = v
      sleeveStyle = SleeveStyle.KAF
    }

    val notes = mutableListOf<String>()
    notes.addAll(extraNotes)
    sleeveNotePatterns.forEach { pat ->
      pat.findAll(normalized).forEach { m ->
        val txt = m.value.replace(multiSpaceRegex, " ").trim()
        if (txt.isNotEmpty() && txt !in notes) notes.add(txt)
      }
    }
    return ParsedSleeve(bazu, cuff, sleeveStyle, notes)
  }

  private fun parseGala(raw: String): ParsedGala {
    val s = raw.trim()
    if (s.isEmpty()) return ParsedGala(0.0, null, emptyList())
    when (s) {
      "14. کالر .5" -> return ParsedGala(14.5, NeckStyle.COLLAR, emptyList())
      "15.5بی۔ پونی" -> return ParsedGala(15.5, NeckStyle.HALF_BAN, emptyList())
      "14.بین پونی" -> return ParsedGala(14.0, NeckStyle.HALF_BAN, emptyList())
      "بین15۔گول" -> return ParsedGala(15.0, NeckStyle.BAN, listOf("بین گول"))
    }
    val commaNormalized = s.replace(Regex("""(\d),(\d)"""), "$1.$2")
    val parenGalaMatch = Regex("""^\(([^)]+)\)\s*(.+)$""").matchEntire(commaNormalized)
    val mainText = parenGalaMatch?.groupValues?.get(2) ?: commaNormalized
    val neckStyle = when {
      Regex("""کالر|کا\s*لر|کذلر|کالد|کار|کلر|کاالر""").containsMatchIn(mainText) -> NeckStyle.COLLAR
      Regex("""پونی\s*بین|بین\s*پو\s*نی|بین\s*پنوی|بیں\s*پونی|پو\s*نی.*بین|پونی\s*گول""").containsMatchIn(mainText) -> NeckStyle.HALF_BAN
      mainText.contains("بین") || mainText.contains("بیبن") || mainText.contains("یین") || Regex("""(?:^|[\d\s.])ین(?=$|\s)""").containsMatchIn(mainText) -> NeckStyle.BAN
      mainText.contains("گول") -> NeckStyle.ROUND
      else -> null
    }
    val gala = extractNumber(mainText) ?: 0.0
    val notes = mutableListOf<String>()
    if (parenGalaMatch != null) {
      notes.add("(${parenGalaMatch.groupValues[1].trim()})")
    }
    galaNotePatterns.forEach { pat ->
      pat.findAll(normalizeRtlNumbers(mainText)).forEach { m ->
        val txt = m.value.replace(multiSpaceRegex, " ").trim()
        if (!(txt == "گول" && neckStyle == NeckStyle.ROUND) && txt.isNotEmpty() && txt !in notes) {
          notes.add(txt)
        }
      }
    }
    return ParsedGala(gala, neckStyle, notes)
  }

  private fun parseGhera(raw: String): ParsedGhera {
    val s = raw.trim()
    if (s.isEmpty()) return ParsedGhera(0.0, null, emptyMap(), false, false, emptyList())
    val damanStyle = when {
      s.contains("گول") || s.contains("کول") || s.contains("گو ") -> DamanStyle.GOL
      s.contains("چورس") || s.contains("چودس") -> DamanStyle.CHORASI
      else -> null
    }
    val hasTwoSide = s.contains("دونوں") && (s.contains("جیب") || s.contains("سیٹ") || s.contains("سائیٹ"))
    val hasOneSide = !hasTwoSide && (s.contains("ایک سیٹ جیب") || s.contains("ایک سائیٹ جیب") || s.contains("سیٹ جیب") || s.contains("سائیٹ جیب"))
    when (s) {
      "221/21" -> return ParsedGhera(22.0, damanStyle, mapOf("GHERA_DAMAN" to "22/21"), hasOneSide, hasTwoSide, listOf("گھیرا شیٹ میں: 221/21 (22/21 درج کیا)"))
      "گول2.3" -> return ParsedGhera(23.0, DamanStyle.GOL, emptyMap(), hasOneSide, hasTwoSide, listOf("گھیرا شیٹ میں: گول2.3 (23 درج کیا)"))
      "2 4" -> return ParsedGhera(24.0, damanStyle, emptyMap(), hasOneSide, hasTwoSide, listOf("گھیرا شیٹ میں: 2 4 (24 درج کیا)"))
      "6/24 گول" -> return ParsedGhera(24.0, DamanStyle.GOL, emptyMap(), hasOneSide, hasTwoSide, listOf("گھیرا شیٹ میں: 6/24 گول (24 درج کیا)"))
      "11-Dec" -> return ParsedGhera(11.0, damanStyle, mapOf("GHERA_DAMAN" to "11/12"), hasOneSide, hasTwoSide, listOf("گھیرا شیٹ میں: 11-Dec (11/12 درج کیا)"))
    }
    val mayMatch = Regex("""^(?:May-(\d{1,2})|(\d{1,2})-May)$""", RegexOption.IGNORE_CASE).matchEntire(s)
    if (mayMatch != null) {
      val base = (mayMatch.groupValues[1].ifEmpty { mayMatch.groupValues[2] }).toDoubleOrNull() ?: 0.0
      val value = base + 0.5
      return ParsedGhera(value, damanStyle, emptyMap(), hasOneSide, hasTwoSide, listOf("گھیرا شیٹ میں: $s (${formatNum(value)} درج کیا)"))
    }
    val janMatch = Regex("""^(\d{1,2})-Jan$""", RegexOption.IGNORE_CASE).matchEntire(s)
    if (janMatch != null) {
      val day = janMatch.groupValues[1].toDoubleOrNull() ?: 0.0
      return ParsedGhera(day, damanStyle, emptyMap(), hasOneSide, hasTwoSide, listOf("گھیرا شیٹ میں: $s (${formatNum(day)} درج کیا)"))
    }

    val normalized = normalizeRtlNumbers(s)
    val nums = numberRegex.findAll(normalized).mapNotNull { it.value.toDoubleOrNull() }.toList()
    if (nums.isEmpty()) return ParsedGhera(0.0, damanStyle, emptyMap(), hasOneSide, hasTwoSide, emptyList())
    val primary = nums[0]
    val rawMap = if (nums.size >= 2) {
      mapOf("GHERA_DAMAN" to "${formatNum(primary)}/${formatNum(nums[1])}")
    } else {
      emptyMap()
    }
    val notes = mutableListOf<String>()
    if (hasOneSide) notes.add("ایک سیٹ جیب")
    if (hasTwoSide) notes.add("جیب سیٹ دونوں")
    if (s.contains("کمیز")) notes.add("کمیز")
    if (s.contains("ٹیچ سادہ")) notes.add("ٹیچ سادہ")
    if (s.contains("شلیٹ") || s.contains("شیلٹ")) {
      val shaletNote = normalized.replace(numberRegex, "").replace(multiSpaceRegex, " ").trim()
      if (shaletNote.isNotEmpty()) notes.add(shaletNote)
    }
    if (Regex("""ڈبل\s*سلا""").containsMatchIn(s)) {
      val extra = normalized
        .replace(numberRegex, "")
        .replace(Regex("""گول|چورس"""), "")
        .replace(multiSpaceRegex, " ")
        .trim()
      if (extra.isNotEmpty()) notes.add(extra)
    }
    return ParsedGhera(primary, damanStyle, rawMap, hasOneSide, hasTwoSide, notes)
  }

  private fun normalizeRtlNumbers(raw: String): String {
    if (raw.isBlank()) return raw
    val noDiacritics = diacriticsRegex.replace(raw.trim(), "")
      .replace(Regex("""(\d)\.\.+(\d)"""), "$1.$2")
      .replace(Regex("""(\d+)[,،]5(?=$|[^\d])"""), "$1.5")
    val collapsedDot = spaceDotRegex.replace(noDiacritics) { match -> "${match.groupValues[1]}.${match.groupValues[2]}" }
    val step1 = rtlFiveDotRegex.replace(collapsedDot) { match -> "${match.groupValues[1]}.5" }
    val step2 = rtlFiveAsciiDotRegex.replace(step1) { match -> "${match.groupValues[1]}.5" }
    return step2.replace("۔", " ").replace(multiSpaceRegex, " ").trim()
  }

  private fun extractNumber(text: String): Double? =
    numberRegex.find(normalizeRtlNumbers(text))?.value?.toDoubleOrNull()?.takeIf { it.isFinite() }

  private fun formatNum(value: Double): String {
    if (abs(value - value.roundToInt()) < 1e-9) {
      return value.roundToInt().toString()
    }
    return String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
  }

  private fun parseCsvRows(input: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val currentRow = mutableListOf<String>()
    val currentField = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < input.length) {
      val ch = input[i]
      when {
        inQuotes -> {
          if (ch == '"') {
            if (i + 1 < input.length && input[i + 1] == '"') {
              currentField.append('"')
              i++
            } else {
              inQuotes = false
            }
          } else {
            currentField.append(ch)
          }
        }
        ch == '"' -> inQuotes = true
        ch == ',' -> {
          currentRow.add(currentField.toString())
          currentField.setLength(0)
        }
        ch == '\r' || ch == '\n' -> {
          if (ch == '\r' && i + 1 < input.length && input[i + 1] == '\n') {
            i++
          }
          currentRow.add(currentField.toString())
          currentField.setLength(0)
          rows.add(currentRow.toList())
          currentRow.clear()
        }
        else -> currentField.append(ch)
      }
      i++
    }
    if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
      currentRow.add(currentField.toString())
      rows.add(currentRow.toList())
    }
    return rows
  }
}
