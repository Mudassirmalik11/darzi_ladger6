package com.example.features.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentMethod
import com.example.core.model.PaymentStatus
import com.example.core.model.PocketStyle
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.StyleSnapshot
import com.example.core.preferences.AppLanguage
import com.example.core.ui.DateFormatUtils
import com.example.core.ui.PkrFormatUtils
import com.example.data.local.daos.OrderWithCustomer
import com.example.data.local.entities.PaymentEntity
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale

enum class PaperWidth(val widthMm: Int, val columns: Int) {
  MM_58(widthMm = 58, columns = 32),
  MM_80(widthMm = 80, columns = 48);

  companion object {
    fun fromMm(widthMm: Int): PaperWidth = if (widthMm == 80) MM_80 else MM_58
  }
}

enum class PrintSlipType {
  CUSTOMER_RECEIPT,
  KARIGAR_SLIP,
  MEASUREMENT_SLIP,
  TEST_PAGE,
}

data class ParchiMetricCell(
  val label: String,
  val value: String,
)

data class ParchiSlipData(
  val slipType: PrintSlipType,
  val isUrdu: Boolean,
  val customerInfoTitle: String,
  val orderIdLabel: String? = null,
  val orderIdValue: String? = null,
  val customerIdLabel: String? = null,
  val customerIdValue: String? = null,
  val nameLabel: String,
  val nameValue: String,
  val mobileLabel: String,
  val mobileValue: String,
  val addressLabel: String? = null,
  val addressValue: String? = null,
  val suitsLabel: String? = null,
  val suitsValue: String? = null,
  val dueLabel: String? = null,
  val dueValue: String? = null,
  val headerBannerText: String,
  val dateText: String,
  val compulsoryMetrics: List<ParchiMetricCell>,
  val optionalMetrics: List<ParchiMetricCell>,
  val additionalNotesTitle: String,
  val additionalNoteLines: List<String>,
)

object EscPosCommands {
  val INIT: ByteArray = byteArrayOf(0x1B, 0x40)
  val ALIGN_LEFT: ByteArray = byteArrayOf(0x1B, 0x61, 0x00)
  val ALIGN_CENTER: ByteArray = byteArrayOf(0x1B, 0x61, 0x01)
  val ALIGN_RIGHT: ByteArray = byteArrayOf(0x1B, 0x61, 0x02)
  val BOLD_ON: ByteArray = byteArrayOf(0x1B, 0x45, 0x01)
  val BOLD_OFF: ByteArray = byteArrayOf(0x1B, 0x45, 0x00)
  val TEXT_NORMAL: ByteArray = byteArrayOf(0x1D, 0x21, 0x00)
  val TEXT_DOUBLE_HEIGHT: ByteArray = byteArrayOf(0x1D, 0x21, 0x01)
  val TEXT_DOUBLE_SIZE: ByteArray = byteArrayOf(0x1D, 0x21, 0x11)
  val FEED_AND_CUT: ByteArray = byteArrayOf(0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x42, 0x00)
}

object ThermalReceiptFormatter {

  private val commaNumberFormat: NumberFormat
    get() = NumberFormat.getIntegerInstance(Locale.US)

  fun wrapText(
    text: String,
    maxWidth: Int,
    preserveUnicode: Boolean = true,
  ): List<String> {
    if (maxWidth <= 0) return emptyList()
    val clean = sanitize(text, preserveUnicode).trim()
    if (clean.isEmpty()) return emptyList()

    val lines = mutableListOf<String>()
    for (paragraph in clean.split('\n')) {
      val trimmedPara = paragraph.trim()
      if (trimmedPara.isEmpty()) {
        lines.add("")
        continue
      }
      var current = StringBuilder()
      for (word in trimmedPara.split(Regex("\\s+"))) {
        if (word.length > maxWidth) {
          if (current.isNotEmpty()) {
            lines.add(current.toString())
            current = StringBuilder()
          }
          var idx = 0
          while (idx < word.length) {
            val end = (idx + maxWidth).coerceAtMost(word.length)
            lines.add(word.substring(idx, end))
            idx = end
          }
        } else if (current.isEmpty()) {
          current.append(word)
        } else if (current.length + 1 + word.length <= maxWidth) {
          current.append(' ').append(word)
        } else {
          lines.add(current.toString())
          current = StringBuilder(word)
        }
      }
      if (current.isNotEmpty()) {
        lines.add(current.toString())
      }
    }
    return lines
  }

  fun formatKeyValue(
    left: String,
    right: String,
    columns: Int,
    preserveUnicode: Boolean = true,
  ): List<String> {
    val cleanLeft = sanitizeSingleLine(left, preserveUnicode)
    val cleanRight = sanitizeSingleLine(right, preserveUnicode).ifBlank { "-" }
    val minGap = 1
    if (cleanLeft.length + minGap + cleanRight.length <= columns) {
      val spaces = (columns - cleanLeft.length - cleanRight.length).coerceAtLeast(1)
      return listOf(cleanLeft + " ".repeat(spaces) + cleanRight)
    }
    val wrappedLeft = wrapText(cleanLeft, columns, preserveUnicode).toMutableList()
    if (wrappedLeft.isEmpty()) {
      val wrappedRight = wrapText(cleanRight, columns, preserveUnicode)
      return if (wrappedRight.size == 1) {
        listOf(wrappedRight.first().padStart(columns))
      } else {
        wrappedRight
      }
    }
    val lastLeft = wrappedLeft.last()
    return if (lastLeft.length + minGap + cleanRight.length <= columns) {
      val spaces = columns - lastLeft.length - cleanRight.length
      wrappedLeft[wrappedLeft.lastIndex] = lastLeft + " ".repeat(spaces) + cleanRight
      wrappedLeft
    } else {
      val wrappedRight = wrapText(cleanRight, columns, preserveUnicode)
      if (wrappedRight.size == 1) {
        wrappedLeft.add(wrappedRight.first().padStart(columns))
      } else {
        wrappedLeft.addAll(wrappedRight)
      }
      wrappedLeft
    }
  }

  fun formatInlinePair(
    leftEntry: String,
    rightEntry: String,
    columns: Int,
    preserveUnicode: Boolean = true,
    alignMonospaceGrid: Boolean = true,
  ): List<String> {
    val left = sanitizeSingleLine(leftEntry, preserveUnicode)
    val right = sanitizeSingleLine(rightEntry, preserveUnicode)
    if (left.isEmpty() && right.isEmpty()) return emptyList()
    if (right.isEmpty()) return wrapText(left, columns, preserveUnicode)
    if (left.isEmpty()) return wrapText(right, columns, preserveUnicode)

    val halfCol = (columns - 2) / 2
    if (alignMonospaceGrid && left.length <= halfCol && right.length <= columns - halfCol - 2) {
      return listOf("${left.padEnd(halfCol)}| $right")
    }
    if (!alignMonospaceGrid && left.length + 5 + right.length <= columns) {
      return listOf("$left  |  $right")
    }
    if (left.length + 3 + right.length <= columns) {
      return listOf("$left | $right")
    }
    return buildList {
      addAll(wrapText(left, columns, preserveUnicode))
      addAll(wrapText(right, columns, preserveUnicode))
    }
  }

  private fun formatPairedEntries(
    entries: List<String>,
    columns: Int,
    preserveUnicode: Boolean,
    alignMonospaceGrid: Boolean,
  ): List<String> {
    val lines = mutableListOf<String>()
    var i = 0
    while (i < entries.size) {
      val left = entries[i]
      if (i + 1 < entries.size) {
        lines.addAll(
          formatInlinePair(
            leftEntry = left,
            rightEntry = entries[i + 1],
            columns = columns,
            preserveUnicode = preserveUnicode,
            alignMonospaceGrid = alignMonospaceGrid,
          ),
        )
      } else {
        lines.addAll(wrapText(left, columns, preserveUnicode))
      }
      i += 2
    }
    return lines
  }

  fun divider(columns: Int, ch: Char = '-'): String = ch.toString().repeat(columns)

  fun centerText(
    text: String,
    columns: Int,
    preserveUnicode: Boolean = true,
  ): List<String> =
    wrapText(text, columns, preserveUnicode).map { line ->
      val pad = ((columns - line.length) / 2).coerceAtLeast(0)
      " ".repeat(pad) + line
    }

  fun formatCustomerReceiptLines(
    shopName: String,
    shopContact: String,
    orderDetail: OrderWithCustomer,
    payments: List<PaymentEntity>,
    paperWidth: PaperWidth,
    preserveUnicode: Boolean = true,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): List<String> {
    val cols = paperWidth.columns
    val order = orderDetail.order
    val isUrdu = language == AppLanguage.URDU && preserveUnicode
    val alignGrid = !isUrdu
    val customerName = orderDetail.customerName.ifBlank { if (isUrdu) "گاہک" else "Customer" }
    val customerPhone = orderDetail.customerPhone.ifBlank { "-" }
    val lines = mutableListOf<String>()

    val defaultShop = if (isUrdu) "درزی لیجر" else "Darzi Ledger"
    val resolvedShop = shopName.ifBlank { defaultShop }.let { name ->
      if (isUrdu && name == "Darzi Ledger") "درزی لیجر" else name
    }
    lines.addAll(centerText(resolvedShop, cols, preserveUnicode))
    if (shopContact.isNotBlank()) {
      val telPrefix = if (isUrdu) "فون:" else "Tel:"
      lines.addAll(centerText("$telPrefix $shopContact", cols, preserveUnicode))
    }
    lines.add(divider(cols, '='))

    val orderNumberLabel = if (isUrdu) {
      order.orderNumber?.let { "آرڈر #$it" } ?: "آرڈر: نمبر زیر التوا"
    } else {
      order.orderNumber?.let { "ORDER #$it" } ?: "ORDER: NUMBER PENDING"
    }
    lines.addAll(centerText(orderNumberLabel, cols, preserveUnicode))
    lines.addAll(centerText(if (isUrdu) "گاہک کی رسید" else "CUSTOMER RECEIPT", cols, preserveUnicode))
    lines.add(divider(cols))

    if (isUrdu) {
      lines.addAll(wrapText("گاہک: $customerName", cols, preserveUnicode))
      lines.addAll(wrapText("موبائل: $customerPhone", cols, preserveUnicode))
      lines.addAll(
        formatInlinePair(
          "سوٹ: ${order.suitCount}",
          "حالت: ${orderStatusLabel(order.orderStatus, true)}",
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )
      val urduLocale = Locale.forLanguageTag("ur")
      val createdText = if (paperWidth == PaperWidth.MM_80) {
        DateFormatUtils.formatDate(order.createdAt, urduLocale)
      } else {
        DateFormatUtils.formatShortDate(order.createdAt, urduLocale)
      }
      val dueText = if (paperWidth == PaperWidth.MM_80) {
        DateFormatUtils.formatDate(order.deliveryDate, urduLocale)
      } else {
        DateFormatUtils.formatShortDate(order.deliveryDate, urduLocale)
      }
      lines.addAll(
        formatInlinePair(
          "بکنگ: $createdText",
          "واپسی: $dueText",
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )

      lines.add(divider(cols))
      lines.addAll(
        formatInlinePair(
          "بل: ${formatCompactUrduPkr(order.totalAmountPkr)}",
          "ادا: ${formatUrduPkr(order.paidAmountPkr)}",
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )
      lines.addAll(
        formatInlinePair(
          "بقایا: ${formatUrduPkr(order.remainingAmountPkr)}",
          paymentStatusLabel(order.paymentStatus, true),
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )

      if (payments.isNotEmpty()) {
        lines.add(divider(cols))
        lines.add("ادائیگیاں:")
        payments.forEach { p ->
          val dateStr = DateFormatUtils.formatShortDate(p.createdAt, urduLocale)
          val label = "$dateStr (${paymentMethodLabel(p.paymentMethod, true)})"
          lines.addAll(formatInlinePair(label, formatUrduPkr(p.amountPkr), cols, preserveUnicode, alignGrid))
        }
      }

      if (order.notes.isNotBlank()) {
        lines.add(divider(cols))
        lines.addAll(wrapText("نوٹ: ${order.notes}", cols, preserveUnicode))
      }

      lines.add(divider(cols, '='))
      lines.addAll(centerText("شکریہ! یہ پرچی ساتھ لائیں۔", cols, preserveUnicode))
    } else {
      lines.addAll(formatKeyValue("Customer:", customerName, cols, preserveUnicode))
      lines.addAll(formatKeyValue("Mobile:", customerPhone, cols, preserveUnicode))
      lines.addAll(
        formatInlinePair(
          "Suits: ${order.suitCount}",
          "Status: ${orderStatusLabel(order.orderStatus, false)}",
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )
      val createdText = if (paperWidth == PaperWidth.MM_80) {
        DateFormatUtils.formatDate(order.createdAt, Locale.ENGLISH)
      } else {
        DateFormatUtils.formatShortDate(order.createdAt, Locale.ENGLISH)
      }
      val dueText = if (paperWidth == PaperWidth.MM_80) {
        DateFormatUtils.formatDate(order.deliveryDate, Locale.ENGLISH)
      } else {
        DateFormatUtils.formatShortDate(order.deliveryDate, Locale.ENGLISH)
      }
      lines.addAll(
        formatInlinePair(
          "In: $createdText",
          "Due: $dueText",
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )

      lines.add(divider(cols))
      lines.addAll(
        formatInlinePair(
          "Total: ${PkrFormatUtils.formatPkr(order.totalAmountPkr)}",
          "Paid: ${PkrFormatUtils.formatPkr(order.paidAmountPkr)}",
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )
      lines.addAll(
        formatInlinePair(
          "REMAINING: ${PkrFormatUtils.formatPkr(order.remainingAmountPkr)}",
          paymentStatusLabel(order.paymentStatus, false),
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )

      if (payments.isNotEmpty()) {
        lines.add(divider(cols))
        lines.add("Payments:")
        payments.forEach { p ->
          val dateStr = if (paperWidth == PaperWidth.MM_80) {
            DateFormatUtils.formatDate(p.createdAt, Locale.ENGLISH)
          } else {
            DateFormatUtils.formatShortDate(p.createdAt, Locale.ENGLISH)
          }
          val label = "$dateStr (${paymentMethodLabel(p.paymentMethod, false)})"
          lines.addAll(formatKeyValue(label, PkrFormatUtils.formatPkr(p.amountPkr), cols, preserveUnicode))
        }
      }

      if (order.notes.isNotBlank()) {
        lines.add(divider(cols))
        lines.addAll(wrapText("Note: ${order.notes}", cols, preserveUnicode))
      }

      lines.add(divider(cols, '='))
      lines.addAll(centerText("Shukriya! Please bring this slip.", cols, preserveUnicode))
    }
    return lines
  }

  fun buildKarigarParchiData(
    shopName: String,
    orderDetail: OrderWithCustomer,
    measurement: MeasurementSnapshot,
    style: StyleSnapshot,
    language: AppLanguage = AppLanguage.ENGLISH,
    customerDisplayId: String? = null,
  ): ParchiSlipData {
    val order = orderDetail.order
    val isUrdu = language == AppLanguage.URDU
    val customerName = orderDetail.customerName.ifBlank { if (isUrdu) "گاہک" else "Customer" }
    val resolvedCustomerId = customerDisplayId?.trim()?.takeIf { it.isNotEmpty() }
      ?: orderDetail.customerCustomId.trim().takeIf { it.isNotEmpty() }
      ?: order.customerId.take(6)
    val resolvedAddress = orderDetail.customerAddress.trim().takeIf { it.isNotEmpty() }
    val defaultShop = if (isUrdu) "درزی لیجر" else "Darzi Ledger"
    val resolvedShop = shopName.ifBlank { defaultShop }.let { name ->
      if (isUrdu && name == "Darzi Ledger") "درزی لیجر" else name
    }
    val garmentTitle = if (isUrdu) "ناپ مردانہ شلوار قمیض" else "Male Shalwar Kameez"
    val locale = if (isUrdu) Locale.forLanguageTag("ur") else Locale.ENGLISH
    val dateText = DateFormatUtils.formatDate(order.createdAt, locale)
    val dueText = DateFormatUtils.formatDate(order.deliveryDate, locale)
    val orderIdValue = if (isUrdu) {
      order.orderNumber?.let { "#$it" } ?: "نمبر زیر التوا"
    } else {
      order.orderNumber?.let { "#$it" } ?: "Pending"
    }

    val compulsory = buildParchiCompulsoryCells(measurement, isUrdu)
    val optional = buildParchiOptionalCells(measurement, isUrdu)
    val styleLines = buildSlipStyleEntries(style, isUrdu = isUrdu, compact = false)
    val noteLines = buildList {
      addAll(styleLines)
      if (measurement.notes.isNotBlank()) add(measurement.notes.trim())
      if (order.notes.isNotBlank()) add(order.notes.trim())
    }

    return ParchiSlipData(
      slipType = PrintSlipType.KARIGAR_SLIP,
      isUrdu = isUrdu,
      customerInfoTitle = if (isUrdu) "کسٹمر کی معلومات" else "CUSTOMER INFORMATION",
      orderIdLabel = if (isUrdu) "آرڈر آئی ڈی" else "Order ID",
      orderIdValue = orderIdValue,
      customerIdLabel = if (isUrdu) "کسٹمر آئی ڈی" else "Customer ID",
      customerIdValue = resolvedCustomerId,
      nameLabel = if (isUrdu) "نام" else "Name",
      nameValue = customerName,
      mobileLabel = if (isUrdu) "موبائل" else "Mobile",
      mobileValue = orderDetail.customerPhone.trim(),
      addressLabel = if (isUrdu) "پتہ" else "Address",
      addressValue = resolvedAddress,
      suitsLabel = if (isUrdu) "سوٹ" else "Suits",
      suitsValue = order.suitCount.toString(),
      dueLabel = if (isUrdu) "واپسی" else "Due",
      dueValue = dueText,
      headerBannerText = "$resolvedShop — $garmentTitle",
      dateText = dateText,
      compulsoryMetrics = compulsory,
      optionalMetrics = optional,
      additionalNotesTitle = if (isUrdu) "اضافی نوٹ" else "ADDITIONAL NOTES",
      additionalNoteLines = noteLines,
    )
  }

  fun buildMeasurementParchiData(
    shopName: String,
    customerName: String,
    customerPhone: String,
    customerDisplayId: String?,
    measurementLabel: String,
    measurement: MeasurementSnapshot,
    style: StyleSnapshot,
    recordedAtMillis: Long,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): ParchiSlipData {
    val isUrdu = language == AppLanguage.URDU
    val resolvedName = customerName.ifBlank { if (isUrdu) "گاہک" else "Customer" }
    val resolvedLabel = if (measurementLabel.isBlank() || measurementLabel.equals("Standard", ignoreCase = true)) {
      if (isUrdu) "ناپ مردانہ شلوار قمیض" else "Male Shalwar Kameez"
    } else {
      measurementLabel
    }
    val defaultShop = if (isUrdu) "درزی لیجر" else "Darzi Ledger"
    val resolvedShop = shopName.ifBlank { defaultShop }.let { name ->
      if (isUrdu && name == "Darzi Ledger") "درزی لیجر" else name
    }
    val locale = if (isUrdu) Locale.forLanguageTag("ur") else Locale.ENGLISH
    val dateText = DateFormatUtils.formatDate(recordedAtMillis, locale)
    val compulsory = buildParchiCompulsoryCells(measurement, isUrdu)
    val optional = buildParchiOptionalCells(measurement, isUrdu)
    val styleLines = buildSlipStyleEntries(style, isUrdu = isUrdu, compact = false)
    val noteLines = buildList {
      addAll(styleLines)
      if (measurement.notes.isNotBlank()) add(measurement.notes.trim())
    }

    return ParchiSlipData(
      slipType = PrintSlipType.MEASUREMENT_SLIP,
      isUrdu = isUrdu,
      customerInfoTitle = if (isUrdu) "کسٹمر کی معلومات" else "CUSTOMER INFORMATION",
      customerIdLabel = if (isUrdu) "کسٹمر آئی ڈی" else "Customer ID",
      customerIdValue = customerDisplayId?.trim()?.takeIf { it.isNotEmpty() },
      nameLabel = if (isUrdu) "نام" else "Name",
      nameValue = resolvedName,
      mobileLabel = if (isUrdu) "موبائل" else "Mobile",
      mobileValue = customerPhone.trim(),
      headerBannerText = "$resolvedShop — $resolvedLabel",
      dateText = dateText,
      compulsoryMetrics = compulsory,
      optionalMetrics = optional,
      additionalNotesTitle = if (isUrdu) "اضافی نوٹ" else "ADDITIONAL NOTES",
      additionalNoteLines = noteLines,
    )
  }

  fun formatKarigarSlipLines(
    shopName: String,
    orderDetail: OrderWithCustomer,
    measurement: MeasurementSnapshot,
    style: StyleSnapshot,
    paperWidth: PaperWidth,
    preserveUnicode: Boolean = true,
    language: AppLanguage = AppLanguage.ENGLISH,
    customerDisplayId: String? = null,
  ): List<String> {
    val cols = paperWidth.columns
    val order = orderDetail.order
    val isUrdu = language == AppLanguage.URDU && preserveUnicode
    val alignGrid = !isUrdu
    val customerName = orderDetail.customerName.ifBlank { if (isUrdu) "گاہک" else "Customer" }
    val resolvedCustomerId = customerDisplayId?.trim()?.takeIf { it.isNotEmpty() }
      ?: orderDetail.customerCustomId.trim().takeIf { it.isNotEmpty() }
      ?: order.customerId.take(6)
    val resolvedAddress = orderDetail.customerAddress.trim()
    val lines = mutableListOf<String>()

    val title = if (isUrdu) "کاریگر ورکشاپ پرچی" else "KARIGAR WORKSHOP SLIP"
    val defaultShop = if (isUrdu) "درزی لیجر" else "Darzi Ledger"
    val resolvedShop = shopName.ifBlank { defaultShop }.let { name ->
      if (isUrdu && name == "Darzi Ledger") "درزی لیجر" else name
    }

    if (isUrdu) {
      val orderIdValue = order.orderNumber?.let { "#$it" } ?: "نمبر زیر التوا"
      val dueText = if (paperWidth == PaperWidth.MM_80) {
        DateFormatUtils.formatDate(order.deliveryDate, Locale.forLanguageTag("ur"))
      } else {
        DateFormatUtils.formatShortDate(order.deliveryDate, Locale.forLanguageTag("ur"))
      }
      val slipDateText = DateFormatUtils.formatDate(order.createdAt, Locale.forLanguageTag("ur"))

      lines.add(divider(cols, '='))
      lines.addAll(centerText("کسٹمر کی معلومات", cols, preserveUnicode))
      lines.add(divider(cols, '='))
      lines.addAll(wrapText("آرڈر آئی ڈی: $orderIdValue", cols, preserveUnicode))
      lines.addAll(wrapText("کسٹمر آئی ڈی: $resolvedCustomerId", cols, preserveUnicode))
      lines.addAll(wrapText("نام: $customerName", cols, preserveUnicode))
      if (orderDetail.customerPhone.isNotBlank()) {
        lines.addAll(wrapText("موبائل: ${orderDetail.customerPhone}", cols, preserveUnicode))
      }
      if (resolvedAddress.isNotBlank()) {
        lines.addAll(wrapText("پتہ: $resolvedAddress", cols, preserveUnicode))
      }
      lines.addAll(formatInlinePair("سوٹ: ${order.suitCount}", "واپسی: $dueText", cols, preserveUnicode, alignGrid))

      lines.add(divider(cols, '='))
      lines.addAll(centerText(resolvedShop, cols, preserveUnicode))
      lines.addAll(centerText("$title | $slipDateText", cols, preserveUnicode))

      lines.add(divider(cols))
      lines.add("ناپ (انچ)")
      val compulsoryEntries = buildSlipCompulsoryMeasurementEntries(measurement, isUrdu = true)
      lines.addAll(formatPairedEntries(compulsoryEntries, cols, preserveUnicode, alignGrid))

      val optionalEntries = buildSlipOptionalMeasurementEntries(measurement, isUrdu = true)
      if (optionalEntries.isNotEmpty()) {
        lines.add(divider(cols))
        lines.addAll(formatPairedEntries(optionalEntries, cols, preserveUnicode, alignGrid))
      }

      val styleEntries = buildSlipStyleEntries(style, isUrdu = true, compact = false)
      if (styleEntries.isNotEmpty()) {
        lines.add(divider(cols))
        lines.add("سلائی و ڈیزائن")
        lines.addAll(formatPairedEntries(styleEntries, cols, preserveUnicode, alignGrid))
      }

      val combinedNotes = buildList {
        if (measurement.notes.isNotBlank()) add(measurement.notes.trim())
        if (order.notes.isNotBlank()) add(order.notes.trim())
      }
      if (combinedNotes.isNotEmpty()) {
        lines.add(divider(cols))
        lines.add("اضافی نوٹ")
        combinedNotes.forEach { note ->
          lines.addAll(wrapText(note, cols, preserveUnicode))
        }
      }
    } else {
      val orderIdValue = order.orderNumber?.let { "#$it" } ?: "NUMBER PENDING"
      val dueText = if (paperWidth == PaperWidth.MM_80) {
        DateFormatUtils.formatDate(order.deliveryDate, Locale.ENGLISH)
      } else {
        DateFormatUtils.formatShortDate(order.deliveryDate, Locale.ENGLISH)
      }
      val slipDateText = DateFormatUtils.formatDate(order.createdAt, Locale.ENGLISH)

      lines.add(divider(cols, '='))
      lines.addAll(centerText("CUSTOMER INFORMATION", cols, preserveUnicode))
      lines.add(divider(cols, '='))
      lines.addAll(formatKeyValue("Order ID:", orderIdValue, cols, preserveUnicode))
      lines.addAll(formatKeyValue("Customer ID:", resolvedCustomerId, cols, preserveUnicode))
      lines.addAll(formatKeyValue("Name:", customerName, cols, preserveUnicode))
      if (orderDetail.customerPhone.isNotBlank()) {
        lines.addAll(formatKeyValue("Mobile:", orderDetail.customerPhone, cols, preserveUnicode))
      }
      if (resolvedAddress.isNotBlank()) {
        lines.addAll(formatKeyValue("Address:", resolvedAddress, cols, preserveUnicode))
      }
      lines.addAll(formatInlinePair("Suits: ${order.suitCount}", "Due: $dueText", cols, preserveUnicode, alignGrid))

      lines.add(divider(cols, '='))
      lines.addAll(centerText(resolvedShop, cols, preserveUnicode))
      lines.addAll(centerText("$title | $slipDateText", cols, preserveUnicode))

      lines.add(divider(cols))
      lines.add("MEASUREMENTS (Inches)")
      val compulsoryEntries = buildSlipCompulsoryMeasurementEntries(measurement, isUrdu = false)
      lines.addAll(formatPairedEntries(compulsoryEntries, cols, preserveUnicode, alignGrid))

      val optionalEntries = buildSlipOptionalMeasurementEntries(measurement, isUrdu = false)
      if (optionalEntries.isNotEmpty()) {
        lines.add(divider(cols))
        lines.addAll(formatPairedEntries(optionalEntries, cols, preserveUnicode, alignGrid))
      }

      val useCompact = paperWidth == PaperWidth.MM_58
      val styleEntries = buildSlipStyleEntries(style, isUrdu = false, compact = useCompact)
      if (styleEntries.isNotEmpty()) {
        lines.add(divider(cols))
        lines.add("STITCHING STYLES")
        lines.addAll(formatPairedEntries(styleEntries, cols, preserveUnicode, alignGrid))
      }

      val combinedNotes = buildList {
        if (measurement.notes.isNotBlank()) add(measurement.notes.trim())
        if (order.notes.isNotBlank()) add(order.notes.trim())
      }
      if (combinedNotes.isNotEmpty()) {
        lines.add(divider(cols))
        lines.add("ADDITIONAL NOTES")
        combinedNotes.forEach { note ->
          lines.addAll(wrapText(note, cols, preserveUnicode))
        }
      }
    }

    lines.add(divider(cols, '='))
    return lines
  }

  fun formatMeasurementSlipLines(
    shopName: String,
    customerName: String,
    customerPhone: String,
    customerDisplayId: String?,
    measurementLabel: String,
    measurement: MeasurementSnapshot,
    style: StyleSnapshot,
    recordedAtMillis: Long,
    paperWidth: PaperWidth,
    preserveUnicode: Boolean = true,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): List<String> {
    val cols = paperWidth.columns
    val isUrdu = language == AppLanguage.URDU && preserveUnicode
    val alignGrid = !isUrdu
    val resolvedName = customerName.ifBlank { if (isUrdu) "گاہک" else "Customer" }
    val resolvedLabel = if (measurementLabel.isBlank() || measurementLabel.equals("Standard", ignoreCase = true)) {
      if (isUrdu) "مردانہ شلوار قمیض" else "Male Shalwar Kameez"
    } else {
      measurementLabel
    }
    val dateLocale = if (isUrdu) Locale.forLanguageTag("ur") else Locale.ENGLISH
    val dateText = if (paperWidth == PaperWidth.MM_80) {
      DateFormatUtils.formatDate(recordedAtMillis, dateLocale)
    } else {
      DateFormatUtils.formatShortDate(recordedAtMillis, dateLocale)
    }
    val title = if (isUrdu) "ناپ کی پرچی" else "MEASUREMENT SLIP"
    val defaultShop = if (isUrdu) "درزی لیجر" else "Darzi Ledger"
    val resolvedShop = shopName.ifBlank { defaultShop }.let { name ->
      if (isUrdu && name == "Darzi Ledger") "درزی لیجر" else name
    }

    val lines = mutableListOf<String>()
    lines.addAll(centerText(title, cols, preserveUnicode))
    lines.addAll(centerText(resolvedShop, cols, preserveUnicode))
    lines.add(divider(cols, '='))

    if (isUrdu) {
      lines.addAll(centerText("کسٹمر کی معلومات", cols, preserveUnicode))
      lines.add(divider(cols, '='))
      customerDisplayId?.trim()?.takeIf { it.isNotEmpty() }?.let { id ->
        lines.addAll(wrapText("کسٹمر آئی ڈی: $id", cols, preserveUnicode))
      }
      lines.addAll(wrapText("نام: $resolvedName", cols, preserveUnicode))
      if (customerPhone.isNotBlank()) {
        lines.addAll(wrapText("موبائل: $customerPhone", cols, preserveUnicode))
      }
      lines.addAll(wrapText("تاریخ: $dateText", cols, preserveUnicode))
      lines.addAll(wrapText("قسم: $resolvedLabel", cols, preserveUnicode))
      lines.add(divider(cols))
      lines.add("ناپ (انچ)")
      val compulsoryEntries = buildSlipCompulsoryMeasurementEntries(measurement, isUrdu = true)
      lines.addAll(formatPairedEntries(compulsoryEntries, cols, preserveUnicode, alignGrid))

      val optionalEntries = buildSlipOptionalMeasurementEntries(measurement, isUrdu = true)
      if (optionalEntries.isNotEmpty()) {
        lines.add(divider(cols))
        lines.addAll(formatPairedEntries(optionalEntries, cols, preserveUnicode, alignGrid))
      }

      val styleEntries = buildSlipStyleEntries(style, isUrdu = true, compact = false)
      if (styleEntries.isNotEmpty()) {
        lines.add(divider(cols))
        lines.add("سلائی و ڈیزائن")
        lines.addAll(formatPairedEntries(styleEntries, cols, preserveUnicode, alignGrid))
      }

      if (measurement.notes.isNotBlank()) {
        lines.add(divider(cols))
        lines.add("اضافی نوٹ")
        lines.addAll(wrapText(measurement.notes.trim(), cols, preserveUnicode))
      }
    } else {
      lines.addAll(centerText("CUSTOMER INFORMATION", cols, preserveUnicode))
      lines.add(divider(cols, '='))
      customerDisplayId?.trim()?.takeIf { it.isNotEmpty() }?.let { id ->
        lines.addAll(formatKeyValue("Customer ID:", id, cols, preserveUnicode))
      }
      lines.addAll(formatKeyValue("Name:", resolvedName, cols, preserveUnicode))
      if (customerPhone.isNotBlank()) {
        lines.addAll(formatKeyValue("Mobile:", customerPhone, cols, preserveUnicode))
      }
      lines.addAll(formatKeyValue("Date:", dateText, cols, preserveUnicode))
      lines.addAll(wrapText("Garment: $resolvedLabel", cols, preserveUnicode))
      lines.add(divider(cols))
      lines.add("MEASUREMENTS (Inches)")
      val compulsoryEntries = buildSlipCompulsoryMeasurementEntries(measurement, isUrdu = false)
      lines.addAll(formatPairedEntries(compulsoryEntries, cols, preserveUnicode, alignGrid))

      val optionalEntries = buildSlipOptionalMeasurementEntries(measurement, isUrdu = false)
      if (optionalEntries.isNotEmpty()) {
        lines.add(divider(cols))
        lines.addAll(formatPairedEntries(optionalEntries, cols, preserveUnicode, alignGrid))
      }

      val useCompact = paperWidth == PaperWidth.MM_58
      val styleEntries = buildSlipStyleEntries(style, isUrdu = false, compact = useCompact)
      if (styleEntries.isNotEmpty()) {
        lines.add(divider(cols))
        lines.add("STITCHING STYLES")
        lines.addAll(formatPairedEntries(styleEntries, cols, preserveUnicode, alignGrid))
      }

      if (measurement.notes.isNotBlank()) {
        lines.add(divider(cols))
        lines.add("ADDITIONAL NOTES")
        lines.addAll(wrapText(measurement.notes.trim(), cols, preserveUnicode))
      }
    }

    lines.add(divider(cols, '='))
    return lines
  }

  fun formatTestPageLines(
    shopName: String,
    printerName: String,
    paperWidth: PaperWidth,
    preserveUnicode: Boolean = true,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): List<String> {
    val cols = paperWidth.columns
    val isUrdu = language == AppLanguage.URDU && preserveUnicode
    val defaultShop = if (isUrdu) "درزی لیجر" else "Darzi Ledger"
    val resolvedShop = shopName.ifBlank { defaultShop }.let { name ->
      if (isUrdu && name == "Darzi Ledger") "درزی لیجر" else name
    }
    val lines = mutableListOf<String>()
    lines.addAll(centerText(resolvedShop, cols, preserveUnicode))
    lines.addAll(centerText(if (isUrdu) "پرنٹر ٹیسٹ پرچی" else "PRINTER TEST SLIP", cols, preserveUnicode))
    lines.add(divider(cols, '='))
    if (isUrdu) {
      lines.addAll(wrapText("پرنٹر: $printerName", cols, preserveUnicode))
      lines.addAll(wrapText("کاغذ کا سائز: ${paperWidth.widthMm}mm ($cols)", cols, preserveUnicode))
      lines.addAll(wrapText("وقت: ${DateFormatUtils.formatDateTime(System.currentTimeMillis(), Locale.forLanguageTag("ur"))}", cols, preserveUnicode))
      lines.add(divider(cols))
      lines.addAll(wrapText("نمونہ: لمبائی 41.5 | تیرا 18.25", cols, preserveUnicode))
      lines.add(divider(cols, '='))
      lines.addAll(centerText("درزی لیجر پرنٹر تیار ہے", cols, preserveUnicode))
    } else {
      lines.addAll(formatKeyValue("Printer:", printerName, cols, preserveUnicode))
      lines.addAll(formatKeyValue("Paper Width:", "${paperWidth.widthMm}mm (${cols} cols)", cols, preserveUnicode))
      lines.addAll(formatKeyValue("Printed:", DateFormatUtils.formatDateTime(System.currentTimeMillis(), Locale.ENGLISH), cols, preserveUnicode))
      lines.add(divider(cols))
      lines.addAll(wrapText("Sample alignment: 1234567890 ABCDEFGHIJKLMNOPQRSTUVWXYZ", cols, preserveUnicode))
      lines.add(divider(cols, '='))
      lines.addAll(centerText("Darzi Ledger Ready", cols, preserveUnicode))
    }
    return lines
  }

  fun formatCustomerReceiptBytes(
    shopName: String,
    shopContact: String,
    orderDetail: OrderWithCustomer,
    payments: List<PaymentEntity>,
    paperWidth: PaperWidth,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): ByteArray {
    if (language == AppLanguage.URDU) {
      val unicodeLines = formatCustomerReceiptLines(
        shopName = shopName,
        shopContact = shopContact,
        orderDetail = orderDetail,
        payments = payments,
        paperWidth = paperWidth,
        preserveUnicode = true,
        language = AppLanguage.URDU,
      )
      encodeEscPosRasterDocument(
        lines = unicodeLines,
        paperWidth = paperWidth,
        isRtl = true,
      )?.let { return it }
    }
    return encodeEscPosDocument(
      headerTitle = shopName.ifBlank { "Darzi Ledger" },
      highlightLine = orderDetail.order.orderNumber?.let { "ORDER #$it" } ?: "NUMBER PENDING",
      bodyLines = formatCustomerReceiptLines(
        shopName = shopName,
        shopContact = shopContact,
        orderDetail = orderDetail,
        payments = payments,
        paperWidth = paperWidth,
        preserveUnicode = false,
        language = AppLanguage.ENGLISH,
      ),
    )
  }

  fun formatKarigarSlipBytes(
    shopName: String,
    orderDetail: OrderWithCustomer,
    measurement: MeasurementSnapshot,
    style: StyleSnapshot,
    paperWidth: PaperWidth,
    language: AppLanguage = AppLanguage.ENGLISH,
    customerDisplayId: String? = null,
  ): ByteArray {
    val parchiData = buildKarigarParchiData(
      shopName = shopName,
      orderDetail = orderDetail,
      measurement = measurement,
      style = style,
      language = language,
      customerDisplayId = customerDisplayId,
    )
    encodeParchiRasterDocument(
      data = parchiData,
      paperWidth = paperWidth,
    )?.let { return it }

    return encodeEscPosDocument(
      headerTitle = "KARIGAR SLIP",
      highlightLine = orderDetail.order.orderNumber?.let { "ORDER #$it" } ?: "NUMBER PENDING",
      bodyLines = formatKarigarSlipLines(
        shopName = shopName,
        orderDetail = orderDetail,
        measurement = measurement,
        style = style,
        paperWidth = paperWidth,
        preserveUnicode = false,
        language = AppLanguage.ENGLISH,
        customerDisplayId = customerDisplayId,
      ),
    )
  }

  fun formatMeasurementSlipBytes(
    shopName: String,
    customerName: String,
    customerPhone: String,
    customerDisplayId: String?,
    measurementLabel: String,
    measurement: MeasurementSnapshot,
    style: StyleSnapshot,
    recordedAtMillis: Long,
    paperWidth: PaperWidth,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): ByteArray {
    val parchiData = buildMeasurementParchiData(
      shopName = shopName,
      customerName = customerName,
      customerPhone = customerPhone,
      customerDisplayId = customerDisplayId,
      measurementLabel = measurementLabel,
      measurement = measurement,
      style = style,
      recordedAtMillis = recordedAtMillis,
      language = language,
    )
    encodeParchiRasterDocument(
      data = parchiData,
      paperWidth = paperWidth,
    )?.let { return it }

    return encodeEscPosDocument(
      headerTitle = "MEASUREMENT SLIP",
      highlightLine = customerDisplayId?.let { "$customerName (ID: $it)" } ?: customerName,
      bodyLines = formatMeasurementSlipLines(
        shopName = shopName,
        customerName = customerName,
        customerPhone = customerPhone,
        customerDisplayId = customerDisplayId,
        measurementLabel = measurementLabel,
        measurement = measurement,
        style = style,
        recordedAtMillis = recordedAtMillis,
        paperWidth = paperWidth,
        preserveUnicode = false,
        language = AppLanguage.ENGLISH,
      ),
    )
  }

  fun formatTestPageBytes(
    shopName: String,
    printerName: String,
    paperWidth: PaperWidth,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): ByteArray {
    if (language == AppLanguage.URDU) {
      val unicodeLines = formatTestPageLines(
        shopName = shopName,
        printerName = printerName,
        paperWidth = paperWidth,
        preserveUnicode = true,
        language = AppLanguage.URDU,
      )
      encodeEscPosRasterDocument(
        lines = unicodeLines,
        paperWidth = paperWidth,
        isRtl = true,
      )?.let { return it }
    }
    return encodeEscPosDocument(
      headerTitle = shopName.ifBlank { "Darzi Ledger" },
      highlightLine = "TEST PRINT OK",
      bodyLines = formatTestPageLines(
        shopName = shopName,
        printerName = printerName,
        paperWidth = paperWidth,
        preserveUnicode = false,
        language = AppLanguage.ENGLISH,
      ),
    )
  }

  private fun encodeParchiRasterDocument(
    data: ParchiSlipData,
    paperWidth: PaperWidth,
  ): ByteArray? = runCatching {
    val widthPx = if (paperWidth == PaperWidth.MM_80) 576 else 384
    val pad = 10
    val contentWidth = widthPx - pad * 2
    val isRtl = data.isUrdu
    val direction = if (isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
    val alignNormal = Layout.Alignment.ALIGN_NORMAL

    val bannerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = if (paperWidth == PaperWidth.MM_80) 32f else 26f
      typeface = Typeface.DEFAULT_BOLD
    }
    val infoBoldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = if (paperWidth == PaperWidth.MM_80) 27f else 23f
      typeface = Typeface.DEFAULT_BOLD
    }
    val infoBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = if (paperWidth == PaperWidth.MM_80) 25f else 21f
      typeface = Typeface.DEFAULT
    }
    val shopPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = if (paperWidth == PaperWidth.MM_80) 26f else 22f
      typeface = Typeface.DEFAULT_BOLD
    }
    val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = if (paperWidth == PaperWidth.MM_80) 22f else 19f
      typeface = Typeface.DEFAULT_BOLD
    }
    val cellLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = if (paperWidth == PaperWidth.MM_80) 24f else 20f
      typeface = Typeface.DEFAULT_BOLD
    }
    val cellValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = if (paperWidth == PaperWidth.MM_80) 40f else 32f
      typeface = Typeface.DEFAULT_BOLD
    }
    val notesTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = if (paperWidth == PaperWidth.MM_80) 28f else 24f
      typeface = Typeface.DEFAULT_BOLD
    }
    val notesBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = if (paperWidth == PaperWidth.MM_80) 25f else 21f
      typeface = Typeface.DEFAULT_BOLD
    }

    fun makeLayout(
      text: String,
      paint: TextPaint,
      maxWidth: Int,
      alignment: Layout.Alignment = alignNormal,
    ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth.coerceAtLeast(32))
      .setAlignment(alignment)
      .setTextDirection(direction)
      .setLineSpacing(4f, 1.0f)
      .setIncludePad(true)
      .build()

    // 1. Customer Info Card Layouts
    val bannerLayout = makeLayout(data.customerInfoTitle, bannerPaint, contentWidth - 20, Layout.Alignment.ALIGN_CENTER)
    val infoInnerWidth = contentWidth - 20
    val infoLines = buildList {
      if (!data.orderIdLabel.isNullOrBlank() && !data.orderIdValue.isNullOrBlank()) {
        add(makeLayout("${data.orderIdLabel}: ${data.orderIdValue}", infoBoldPaint, infoInnerWidth))
      }
      if (!data.customerIdLabel.isNullOrBlank() && !data.customerIdValue.isNullOrBlank()) {
        add(makeLayout("${data.customerIdLabel}: ${data.customerIdValue}", infoBoldPaint, infoInnerWidth))
      }
      add(makeLayout("${data.nameLabel}: ${data.nameValue}", infoBoldPaint, infoInnerWidth))
      if (data.mobileValue.isNotBlank()) {
        add(makeLayout("${data.mobileLabel}: ${data.mobileValue}", infoBodyPaint, infoInnerWidth))
      } else {
        add(makeLayout("${data.mobileLabel}:", infoBodyPaint, infoInnerWidth))
      }
      if (!data.addressLabel.isNullOrBlank() && !data.addressValue.isNullOrBlank()) {
        add(makeLayout("${data.addressLabel}: ${data.addressValue}", infoBodyPaint, infoInnerWidth))
      }
      if (!data.suitsLabel.isNullOrBlank() && !data.suitsValue.isNullOrBlank() &&
        !data.dueLabel.isNullOrBlank() && !data.dueValue.isNullOrBlank()
      ) {
        add(
          makeLayout(
            "${data.suitsLabel}: ${data.suitsValue}   |   ${data.dueLabel}: ${data.dueValue}",
            infoBoldPaint,
            infoInnerWidth,
          ),
        )
      }
    }
    val bannerHeight = bannerLayout.height + 16
    val infoBodyHeight = infoLines.sumOf { it.height + 6 } + 14
    val infoCardHeight = bannerHeight + infoBodyHeight

    // 2. Shop + Garment + Date Row
    val dateWidth = (contentWidth * 0.34f).toInt()
    val shopWidth = contentWidth - dateWidth - 8
    val shopLayout = makeLayout(data.headerBannerText, shopPaint, shopWidth)
    val dateLayout = makeLayout(data.dateText, datePaint, dateWidth, Layout.Alignment.ALIGN_OPPOSITE)
    val shopRowHeight = maxOf(shopLayout.height, dateLayout.height) + 14

    // 3. Measurement Grid Rows (2 columns per row, NO cloth icons)
    val colWidth = contentWidth / 2
    val cellTextWidth = colWidth - 12

    data class RasterCell(val label: StaticLayout, val value: StaticLayout, val cellHeight: Int)
    data class RasterRow(val startCell: RasterCell, val endCell: RasterCell?, val rowHeight: Int)

    fun buildGridRows(cells: List<ParchiMetricCell>): List<RasterRow> {
      val rows = mutableListOf<RasterRow>()
      var idx = 0
      while (idx < cells.size) {
        val c1 = cells[idx]
        val l1 = makeLayout(c1.label, cellLabelPaint, cellTextWidth, Layout.Alignment.ALIGN_CENTER)
        val v1 = makeLayout(c1.value, cellValuePaint, cellTextWidth, Layout.Alignment.ALIGN_CENTER)
        val r1 = RasterCell(l1, v1, l1.height + v1.height + 18)

        val r2 = if (idx + 1 < cells.size) {
          val c2 = cells[idx + 1]
          val l2 = makeLayout(c2.label, cellLabelPaint, cellTextWidth, Layout.Alignment.ALIGN_CENTER)
          val v2 = makeLayout(c2.value, cellValuePaint, cellTextWidth, Layout.Alignment.ALIGN_CENTER)
          RasterCell(l2, v2, l2.height + v2.height + 18)
        } else {
          null
        }
        rows.add(RasterRow(r1, r2, maxOf(r1.cellHeight, r2?.cellHeight ?: 0)))
        idx += 2
      }
      return rows
    }

    val compulsoryRows = buildGridRows(data.compulsoryMetrics)
    val optionalRows = buildGridRows(data.optionalMetrics)

    // 4. Additional Notes
    val notesTitleLayout = if (data.additionalNoteLines.isNotEmpty()) {
      makeLayout(data.additionalNotesTitle, notesTitlePaint, contentWidth)
    } else {
      null
    }
    val notesLayouts = data.additionalNoteLines.map { line ->
      makeLayout(line, notesBodyPaint, contentWidth)
    }

    var totalHeight = 10 + infoCardHeight + 14 + shopRowHeight + 10
    totalHeight += compulsoryRows.sumOf { it.rowHeight }
    if (optionalRows.isNotEmpty()) {
      totalHeight += 12 + optionalRows.sumOf { it.rowHeight }
    }
    if (notesTitleLayout != null) {
      totalHeight += 16 + notesTitleLayout.height + 8 + notesLayouts.sumOf { it.height + 6 }
    }
    totalHeight = (totalHeight + 24).coerceAtLeast(64)

    val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
    try {
      val canvas = Canvas(bitmap)
      canvas.drawColor(Color.WHITE)
      val blackFill = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
      val borderStroke = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2.5f }
      val gridStroke = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }

      var y = 10f

      // Draw Highlighted Customer Info Box
      canvas.drawRect(pad.toFloat(), y, (widthPx - pad).toFloat(), y + bannerHeight, blackFill)
      canvas.save()
      canvas.translate((pad + 10).toFloat(), y + 8f)
      bannerLayout.draw(canvas)
      canvas.restore()

      canvas.drawRect(pad.toFloat(), y, (widthPx - pad).toFloat(), y + infoCardHeight, borderStroke)
      var iy = y + bannerHeight + 8f
      for (infoLayout in infoLines) {
        canvas.save()
        canvas.translate((pad + 10).toFloat(), iy)
        infoLayout.draw(canvas)
        canvas.restore()
        iy += infoLayout.height + 6f
      }
      y += infoCardHeight + 14f

      // Draw Shop + Garment & Date Header
      val startX = if (isRtl) (widthPx - pad - shopWidth).toFloat() else pad.toFloat()
      val endX = if (isRtl) pad.toFloat() else (widthPx - pad - dateWidth).toFloat()
      canvas.save()
      canvas.translate(startX, y)
      shopLayout.draw(canvas)
      canvas.restore()
      canvas.save()
      canvas.translate(endX, y + 2f)
      dateLayout.draw(canvas)
      canvas.restore()
      y += shopRowHeight - 6f
      canvas.drawRect(pad.toFloat(), y, (widthPx - pad).toFloat(), y + 3f, blackFill)
      y += 10f

      fun drawGrid(rows: List<RasterRow>) {
        for (row in rows) {
          val rowTop = y
          val rowBottom = y + row.rowHeight
          canvas.drawRect(pad.toFloat(), rowTop, (widthPx - pad).toFloat(), rowBottom, gridStroke)
          val midX = pad + colWidth.toFloat()
          canvas.drawLine(midX, rowTop, midX, rowBottom, gridStroke)

          val startCellX = if (isRtl) midX + 6f else pad + 6f
          val endCellX = if (isRtl) pad + 6f else midX + 6f

          canvas.save()
          canvas.translate(startCellX, rowTop + 8f)
          row.startCell.label.draw(canvas)
          canvas.translate(0f, row.startCell.label.height + 2f)
          row.startCell.value.draw(canvas)
          canvas.restore()

          row.endCell?.let { endCell ->
            canvas.save()
            canvas.translate(endCellX, rowTop + 8f)
            endCell.label.draw(canvas)
            canvas.translate(0f, endCell.label.height + 2f)
            endCell.value.draw(canvas)
            canvas.restore()
          }
          y = rowBottom
        }
      }

      drawGrid(compulsoryRows)
      if (optionalRows.isNotEmpty()) {
        y += 10f
        drawGrid(optionalRows)
      }

      if (notesTitleLayout != null) {
        y += 14f
        canvas.drawRect(pad.toFloat(), y, (widthPx - pad).toFloat(), y + 2f, blackFill)
        y += 10f
        canvas.save()
        canvas.translate(pad.toFloat(), y)
        notesTitleLayout.draw(canvas)
        canvas.restore()
        y += notesTitleLayout.height + 6f
        for (nl in notesLayouts) {
          canvas.save()
          canvas.translate(pad.toFloat(), y)
          nl.draw(canvas)
          canvas.restore()
          y += nl.height + 6f
        }
      }

      bitmapToEscPosRaster(bitmap)
    } finally {
      bitmap.recycle()
    }
  }.getOrNull()

  private fun bitmapToEscPosRaster(bitmap: Bitmap): ByteArray {
    val widthPx = bitmap.width
    val totalHeight = bitmap.height
    val out = ByteArrayOutputStream()
    out.write(EscPosCommands.INIT)
    val bytesPerRow = widthPx / 8
    val xL = (bytesPerRow and 0xFF).toByte()
    val xH = ((bytesPerRow shr 8) and 0xFF).toByte()
    val maxSliceHeight = 256
    var startY = 0
    val pixels = IntArray(widthPx)

    while (startY < totalHeight) {
      val sliceHeight = (totalHeight - startY).coerceAtMost(maxSliceHeight)
      val yL = (sliceHeight and 0xFF).toByte()
      val yH = ((sliceHeight shr 8) and 0xFF).toByte()
      out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))

      for (rowOffset in 0 until sliceHeight) {
        bitmap.getPixels(pixels, 0, widthPx, 0, startY + rowOffset, widthPx, 1)
        for (byteIdx in 0 until bytesPerRow) {
          var packed = 0
          val baseX = byteIdx * 8
          for (bit in 0 until 8) {
            val pixel = pixels[baseX + bit]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val luma = (r * 30 + g * 59 + b * 11) / 100
            if (luma < 160) {
              packed = packed or (1 shl (7 - bit))
            }
          }
          out.write(packed)
        }
      }
      startY += sliceHeight
    }

    out.write(EscPosCommands.FEED_AND_CUT)
    return out.toByteArray()
  }

  private fun encodeEscPosRasterDocument(
    lines: List<String>,
    paperWidth: PaperWidth,
    isRtl: Boolean,
  ): ByteArray? = runCatching {
    val widthPx = if (paperWidth == PaperWidth.MM_80) 576 else 384
    val horizontalPad = 10
    val contentWidth = widthPx - horizontalPad * 2
    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = if (paperWidth == PaperWidth.MM_80) 27f else 24f
      typeface = Typeface.DEFAULT
    }
    val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = if (paperWidth == PaperWidth.MM_80) 29f else 26f
      typeface = Typeface.DEFAULT_BOLD
    }
    val bannerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = if (paperWidth == PaperWidth.MM_80) 34f else 30f
      typeface = Typeface.DEFAULT_BOLD
    }
    val direction = if (isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR

    data class HighlightBannerRow(val layout: StaticLayout)
    data class TextRow(val layout: StaticLayout, val topPad: Int)
    data class DividerRow(val heavy: Boolean)

    val rows = mutableListOf<Any>()
    var totalHeight = 10
    for ((index, rawLine) in lines.withIndex()) {
      val trimmed = rawLine.trim()
      if (trimmed.isNotEmpty() && trimmed.all { it == '=' || it == '-' }) {
        val heavy = trimmed.first() == '='
        rows.add(DividerRow(heavy))
        totalHeight += if (heavy) 14 else 10
        continue
      }
      if (trimmed == "کسٹمر کی معلومات" || trimmed == "CUSTOMER INFORMATION") {
        val layout = StaticLayout.Builder.obtain(trimmed, 0, trimmed.length, bannerPaint, contentWidth - 16)
          .setAlignment(Layout.Alignment.ALIGN_CENTER)
          .setTextDirection(direction)
          .setLineSpacing(4f, 1.0f)
          .setIncludePad(true)
          .build()
        rows.add(HighlightBannerRow(layout))
        totalHeight += layout.height + 20
        continue
      }
      val isCentered = rawLine.startsWith(" ") && trimmed.isNotEmpty()
      val isEmphasized = isCentered || index <= 4 ||
        trimmed.startsWith("آرڈر آئی ڈی:") ||
        trimmed.startsWith("کسٹمر آئی ڈی:") ||
        trimmed.startsWith("نام:") ||
        trimmed.startsWith("Order ID:") ||
        trimmed.startsWith("Customer ID:") ||
        trimmed.startsWith("Name:") ||
        trimmed == "ناپ (انچ)" ||
        trimmed == "سلائی و ڈیزائن" ||
        trimmed == "اضافی نوٹ" ||
        trimmed == "کاریگر نوٹس" ||
        trimmed == "MEASUREMENTS (Inches)" ||
        trimmed == "STITCHING STYLES" ||
        trimmed == "ADDITIONAL NOTES"
      val paint = if (isEmphasized) boldPaint else bodyPaint
      val text = if (isCentered) trimmed else rawLine
      val alignment = if (isCentered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
      val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
        .setAlignment(alignment)
        .setTextDirection(direction)
        .setLineSpacing(6f, 1.0f)
        .setIncludePad(true)
        .build()
      val topPad = if (isEmphasized) 3 else 1
      rows.add(TextRow(layout, topPad))
      totalHeight += layout.height + topPad + 5
    }
    totalHeight = (totalHeight + 14).coerceAtLeast(32)

    val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
    try {
      val canvas = Canvas(bitmap)
      canvas.drawColor(Color.WHITE)
      val linePaint = Paint().apply { color = Color.BLACK }
      var y = 8f
      for (row in rows) {
        when (row) {
          is DividerRow -> {
            val stroke = if (row.heavy) 3f else 1.5f
            canvas.drawRect(horizontalPad.toFloat(), y + 4f, (widthPx - horizontalPad).toFloat(), y + 4f + stroke, linePaint)
            y += if (row.heavy) 14f else 10f
          }
          is HighlightBannerRow -> {
            val bannerHeight = row.layout.height + 16f
            canvas.drawRect(horizontalPad.toFloat(), y, (widthPx - horizontalPad).toFloat(), y + bannerHeight, linePaint)
            canvas.save()
            canvas.translate((horizontalPad + 8).toFloat(), y + 8f)
            row.layout.draw(canvas)
            canvas.restore()
            y += bannerHeight + 4f
          }
          is TextRow -> {
            y += row.topPad
            canvas.save()
            canvas.translate(horizontalPad.toFloat(), y)
            row.layout.draw(canvas)
            canvas.restore()
            y += row.layout.height + 5f
          }
        }
      }

      bitmapToEscPosRaster(bitmap)
    } finally {
      bitmap.recycle()
    }
  }.getOrNull()

  private fun encodeEscPosDocument(
    headerTitle: String,
    highlightLine: String,
    bodyLines: List<String>,
  ): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(EscPosCommands.INIT)

    out.write(EscPosCommands.ALIGN_CENTER)
    out.write(EscPosCommands.BOLD_ON)
    out.write(EscPosCommands.TEXT_DOUBLE_HEIGHT)
    out.write(lineBytes(sanitizeEscPosAscii(headerTitle).ifBlank { "Darzi Ledger" }))
    out.write(EscPosCommands.TEXT_NORMAL)
    out.write(lineBytes(sanitizeEscPosAscii(highlightLine)))
    out.write(EscPosCommands.BOLD_OFF)
    out.write(EscPosCommands.ALIGN_LEFT)

    bodyLines.forEach { line ->
      val trimmed = line.trim()
      val isBanner = trimmed == "CUSTOMER INFORMATION"
      val isBoldSection = isBanner ||
        trimmed.startsWith("Order ID:") ||
        trimmed.startsWith("Customer ID:") ||
        trimmed.startsWith("Name:") ||
        trimmed == "MEASUREMENTS (Inches)" ||
        trimmed == "STITCHING STYLES" ||
        trimmed == "ADDITIONAL NOTES"
      if (isBanner) {
        out.write(EscPosCommands.BOLD_ON)
        out.write(EscPosCommands.TEXT_DOUBLE_HEIGHT)
        out.write(lineBytes(line))
        out.write(EscPosCommands.TEXT_NORMAL)
        out.write(EscPosCommands.BOLD_OFF)
      } else if (isBoldSection) {
        out.write(EscPosCommands.BOLD_ON)
        out.write(lineBytes(line))
        out.write(EscPosCommands.BOLD_OFF)
      } else {
        out.write(lineBytes(line))
      }
    }

    out.write(EscPosCommands.FEED_AND_CUT)
    return out.toByteArray()
  }

  private fun lineBytes(line: String): ByteArray =
    (line + "\n").toByteArray(StandardCharsets.US_ASCII)

  private fun formatUrduPkr(amountPkr: Long): String = "${commaNumberFormat.format(amountPkr)} روپے"

  private fun formatCompactUrduPkr(amountPkr: Long): String = commaNumberFormat.format(amountPkr)

  private fun formatUrduInches(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) {
      rounded.toInt().toString()
    } else {
      rounded.toString().trimEnd('0').trimEnd('.')
    }
  }

  private fun orderStatusLabel(status: OrderStatus, isUrdu: Boolean): String =
    if (!isUrdu) status.displayName else when (status) {
      OrderStatus.PENDING -> "زیر التوا"
      OrderStatus.READY -> "تیار"
      OrderStatus.DELIVERED -> "حوالے"
      OrderStatus.CANCELLED -> "منسوخ"
    }

  private fun paymentStatusLabel(status: PaymentStatus, isUrdu: Boolean): String =
    if (!isUrdu) status.displayName else when (status) {
      PaymentStatus.UNPAID -> "بقایا"
      PaymentStatus.PARTIAL -> "جزوی"
      PaymentStatus.PAID -> "مکمل ادا"
    }

  private fun paymentMethodLabel(method: PaymentMethod, isUrdu: Boolean): String =
    if (!isUrdu) method.displayName else when (method) {
      PaymentMethod.CASH -> "نقد"
      PaymentMethod.JAZZCASH -> "جاز کیش"
      PaymentMethod.EASYPAISA -> "ایزی پیسہ"
      PaymentMethod.BANK_TRANSFER -> "بینک"
      PaymentMethod.OTHER -> "دیگر"
    }

  private fun buildParchiCompulsoryCells(
    measurement: MeasurementSnapshot,
    isUrdu: Boolean,
  ): List<ParchiMetricCell> = if (isUrdu) {
    listOf(
      ParchiMetricCell("لمبائی", formatUrduInches(measurement.kameezLambai)),
      ParchiMetricCell("تیرا", formatUrduInches(measurement.tera)),
      ParchiMetricCell("آستین", formatUrduInches(measurement.bazu)),
      ParchiMetricCell("گلہ", formatUrduInches(measurement.galaCollar)),
      ParchiMetricCell("سینہ", formatUrduInches(measurement.chati)),
      ParchiMetricCell("گھیرا دامن", formatUrduInches(measurement.gheraDaman)),
      ParchiMetricCell("شلوار", formatUrduInches(measurement.shalwarLambai)),
      ParchiMetricCell("پونچہ", formatUrduInches(measurement.paincha)),
    )
  } else {
    listOf(
      ParchiMetricCell("Kameez Lambai", DateFormatUtils.formatInches(measurement.kameezLambai)),
      ParchiMetricCell("Teera", DateFormatUtils.formatInches(measurement.tera)),
      ParchiMetricCell("Asteen", DateFormatUtils.formatInches(measurement.bazu)),
      ParchiMetricCell("Gala", DateFormatUtils.formatInches(measurement.galaCollar)),
      ParchiMetricCell("Seena", DateFormatUtils.formatInches(measurement.chati)),
      ParchiMetricCell("Ghera / Daman", DateFormatUtils.formatInches(measurement.gheraDaman)),
      ParchiMetricCell("Shalwar", DateFormatUtils.formatInches(measurement.shalwarLambai)),
      ParchiMetricCell("Poncha", DateFormatUtils.formatInches(measurement.paincha)),
    )
  }

  private fun buildParchiOptionalCells(
    measurement: MeasurementSnapshot,
    isUrdu: Boolean,
  ): List<ParchiMetricCell> = if (isUrdu) {
    buildList {
      if (measurement.kamar > 0.0) add(ParchiMetricCell("کمر", formatUrduInches(measurement.kamar)))
      if (measurement.cuff > 0.0) add(ParchiMetricCell("کف", formatUrduInches(measurement.cuff)))
      if (measurement.gheraAsan > 0.0) add(ParchiMetricCell("آسن", formatUrduInches(measurement.gheraAsan)))
    }
  } else {
    buildList {
      if (measurement.kamar > 0.0) add(ParchiMetricCell("Kamar", DateFormatUtils.formatInches(measurement.kamar)))
      if (measurement.cuff > 0.0) add(ParchiMetricCell("Cuff", DateFormatUtils.formatInches(measurement.cuff)))
      if (measurement.gheraAsan > 0.0) add(ParchiMetricCell("Ghera / Asan", DateFormatUtils.formatInches(measurement.gheraAsan)))
    }
  }

  private fun buildSlipCompulsoryMeasurementEntries(
    measurement: MeasurementSnapshot,
    isUrdu: Boolean,
  ): List<String> = if (isUrdu) {
    listOf(
      "لمبائی: ${formatUrduInches(measurement.kameezLambai)}",
      "تیرا: ${formatUrduInches(measurement.tera)}",
      "آستین: ${formatUrduInches(measurement.bazu)}",
      "گلہ: ${formatUrduInches(measurement.galaCollar)}",
      "سینہ: ${formatUrduInches(measurement.chati)}",
      "گھیرا دامن: ${formatUrduInches(measurement.gheraDaman)}",
      "شلوار: ${formatUrduInches(measurement.shalwarLambai)}",
      "پونچہ: ${formatUrduInches(measurement.paincha)}",
    )
  } else {
    listOf(
      "Lambai: ${DateFormatUtils.formatInches(measurement.kameezLambai)}",
      "Tera: ${DateFormatUtils.formatInches(measurement.tera)}",
      "Asteen: ${DateFormatUtils.formatInches(measurement.bazu)}",
      "Gala: ${DateFormatUtils.formatInches(measurement.galaCollar)}",
      "Seena: ${DateFormatUtils.formatInches(measurement.chati)}",
      "Ghera: ${DateFormatUtils.formatInches(measurement.gheraDaman)}",
      "Shalwar: ${DateFormatUtils.formatInches(measurement.shalwarLambai)}",
      "Poncha: ${DateFormatUtils.formatInches(measurement.paincha)}",
    )
  }

  private fun buildSlipOptionalMeasurementEntries(
    measurement: MeasurementSnapshot,
    isUrdu: Boolean,
  ): List<String> = if (isUrdu) {
    buildList {
      if (measurement.kamar > 0.0) add("کمر: ${formatUrduInches(measurement.kamar)}")
      if (measurement.cuff > 0.0) add("کف: ${formatUrduInches(measurement.cuff)}")
      if (measurement.gheraAsan > 0.0) add("آسن: ${formatUrduInches(measurement.gheraAsan)}")
    }
  } else {
    buildList {
      if (measurement.kamar > 0.0) add("Kamar: ${DateFormatUtils.formatInches(measurement.kamar)}")
      if (measurement.cuff > 0.0) add("Cuff: ${DateFormatUtils.formatInches(measurement.cuff)}")
      if (measurement.gheraAsan > 0.0) add("Asan: ${DateFormatUtils.formatInches(measurement.gheraAsan)}")
    }
  }

  private fun buildSlipStyleEntries(
    style: StyleSnapshot,
    isUrdu: Boolean,
    compact: Boolean,
  ): List<String> {
    val pockets = style.effectivePocketStyles()
    return if (isUrdu) {
      buildList {
        style.neckStyle?.let { add("گلا: ${neckStyleLabel(it, true)}") }
        style.damanStyle?.let { add("دامن: ${damanStyleLabel(it, true)}") }
        if (pockets.isNotEmpty()) add("جیب: ${pocketStylesLabel(pockets, true)}")
        style.sleeveStyle?.let { add("آستین: ${sleeveStyleLabel(it, true)}") }
        style.silaiStyle?.let { add("سلائی: ${silaiStyleLabel(it, true)}") }
      }
    } else {
      buildList {
        style.neckStyle?.let { add("Neck: ${neckStyleLabel(it, false)}") }
        style.damanStyle?.let { add("Daman: ${damanStyleLabel(it, false, compact)}") }
        if (pockets.isNotEmpty()) {
          add("${if (compact) "Pkts" else "Pockets"}: ${pocketStylesLabel(pockets, false, compact)}")
        }
        style.sleeveStyle?.let { add("${if (compact) "Slv" else "Sleeves"}: ${sleeveStyleLabel(it, false, compact)}") }
        style.silaiStyle?.let { add("Silai: ${silaiStyleLabel(it, false, compact)}") }
      }
    }
  }

  private fun neckStyleLabel(style: NeckStyle, isUrdu: Boolean): String =
    if (!isUrdu) style.displayName else when (style) {
      NeckStyle.COLLAR -> "کالر"
      NeckStyle.BAN -> "بین"
      NeckStyle.HALF_BAN -> "ہاف بین"
      NeckStyle.ROUND -> "گول"
    }

  private fun damanStyleLabel(style: DamanStyle, isUrdu: Boolean, compact: Boolean = false): String =
    if (!isUrdu) {
      if (!compact) style.displayName else when (style) {
        DamanStyle.GOL -> "Gol"
        DamanStyle.CHORASI -> "Chorasi"
      }
    } else when (style) {
      DamanStyle.GOL -> "گول"
      DamanStyle.CHORASI -> "چورس"
    }

  private fun pocketStylesLabel(styles: List<PocketStyle>, isUrdu: Boolean, compact: Boolean = false): String {
    if (styles.isEmpty()) return if (isUrdu) "کوئی نہیں" else "None"
    val separator = if (isUrdu) "، " else ", "
    return styles.joinToString(separator) { pocketStyleLabel(it, isUrdu, compact) }
  }

  private fun pocketStyleLabel(style: PocketStyle, isUrdu: Boolean, compact: Boolean = false): String =
    if (!isUrdu) {
      if (!compact) style.displayName else when (style) {
        PocketStyle.FRONT -> "Front"
        PocketStyle.ONE_SIDE -> "1 Side"
        PocketStyle.TWO_SIDE -> "2 Side"
        PocketStyle.SHALWAR_POCKET -> "Shalwar"
      }
    } else when (style) {
      PocketStyle.FRONT -> "سامنے"
      PocketStyle.ONE_SIDE -> "1 سائیڈ"
      PocketStyle.TWO_SIDE -> "2 سائیڈ"
      PocketStyle.SHALWAR_POCKET -> "شلوار"
    }

  private fun sleeveStyleLabel(style: SleeveStyle, isUrdu: Boolean, compact: Boolean = false): String =
    if (!isUrdu) {
      if (!compact) style.displayName else when (style) {
        SleeveStyle.PLAIN -> "Plain"
        SleeveStyle.SINGLE_CUFF -> "1-Cuff"
        SleeveStyle.DOUBLE_CUFF -> "2-Cuff"
        SleeveStyle.STUDS -> "Studs"
      }
    } else when (style) {
      SleeveStyle.PLAIN -> "سادہ"
      SleeveStyle.SINGLE_CUFF -> "سنگل کف"
      SleeveStyle.DOUBLE_CUFF -> "ڈبل کف"
      SleeveStyle.STUDS -> "اسٹڈ"
    }

  private fun silaiStyleLabel(style: SilaiStyle, isUrdu: Boolean, compact: Boolean = false): String =
    if (!isUrdu) {
      if (!compact) style.displayName else when (style) {
        SilaiStyle.SINGLE -> "Single"
        SilaiStyle.DOUBLE -> "Double"
        SilaiStyle.CHAMAK_DHAGA -> "Chamak"
      }
    } else when (style) {
      SilaiStyle.SINGLE -> "سنگل"
      SilaiStyle.DOUBLE -> "ڈبل"
      SilaiStyle.CHAMAK_DHAGA -> "چمک دھاگہ"
    }

  private fun sanitize(input: String, preserveUnicode: Boolean): String =
    if (preserveUnicode) sanitizePreview(input) else sanitizeEscPosAscii(input)

  private fun sanitizeSingleLine(input: String, preserveUnicode: Boolean): String =
    sanitize(input, preserveUnicode).replace(Regex("\\s+"), " ").trim()

  private fun sanitizePreview(input: String): String =
    buildString(input.length) {
      for (ch in input) {
        when {
          ch == '\r' -> Unit
          ch == '\u00A0' || ch == '\t' -> append(' ')
          ch == '\n' -> append('\n')
          ch.code < 0x20 || ch.code == 0x7F -> Unit
          else -> append(ch)
        }
      }
    }

  private fun sanitizeEscPosAscii(input: String): String {
    val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
    return buildString(normalized.length) {
      for (ch in normalized) {
        val type = Character.getType(ch)
        if (type == Character.NON_SPACING_MARK.toInt() ||
          type == Character.ENCLOSING_MARK.toInt() ||
          type == Character.COMBINING_SPACING_MARK.toInt()
        ) {
          continue
        }
        when (ch) {
          in '\u0020'..'\u007E', '\n' -> append(ch)
          '\r' -> Unit
          '\u00A0', '\t' -> append(' ')
          '—', '–' -> append('-')
          '•' -> append('*')
          '“', '”' -> append('"')
          '’', '‘' -> append('\'')
          '،' -> append(',')
          '؛' -> append(';')
          '؟' -> append('?')
          in '\u0660'..'\u0669' -> append(('0'.code + (ch.code - '\u0660'.code)).toChar())
          in '\u06F0'..'\u06F9' -> append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
          '\u0640', '\u200C', '\u200D', '\u200E', '\u200F', '\uFEFF' -> Unit
          else -> {
            val roman = urduCharToAscii(ch)
            if (roman != null) {
              append(roman)
            } else if (Character.isWhitespace(ch)) {
              append(' ')
            }
          }
        }
      }
    }.replace(Regex(" {2,}"), " ")
  }

  private fun urduCharToAscii(ch: Char): String? = when (ch) {
    'آ', 'ا', 'أ', 'إ', 'ٱ' -> "a"
    'ب' -> "b"
    'پ' -> "p"
    'ت', 'ٹ', 'ط' -> "t"
    'ث', 'س', 'ص' -> "s"
    'ج' -> "j"
    'چ' -> "ch"
    'ح', 'ہ', 'ھ', 'ۂ', 'ة' -> "h"
    'خ' -> "kh"
    'د', 'ڈ' -> "d"
    'ذ', 'ز', 'ض', 'ظ' -> "z"
    'ر', 'ڑ' -> "r"
    'ژ' -> "zh"
    'ش' -> "sh"
    'ع' -> "a"
    'غ' -> "gh"
    'ف' -> "f"
    'ق' -> "q"
    'ک', 'ك' -> "k"
    'گ' -> "g"
    'ل' -> "l"
    'م' -> "m"
    'ن', 'ں' -> "n"
    'و', 'ؤ' -> "o"
    'ی', 'ي', 'ئ', 'ے', 'ۓ' -> "y"
    'ء' -> "'"
    else -> null
  }
}
