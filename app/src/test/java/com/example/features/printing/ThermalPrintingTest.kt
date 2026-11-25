package com.example.features.printing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.app.TailorApp
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentMethod
import com.example.core.model.PaymentStatus
import com.example.core.model.PocketStyle
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.SnapshotJson
import com.example.core.model.StyleSnapshot
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.AutoCutterMode
import com.example.core.preferences.PrinterCompatibilityMode
import com.example.data.local.daos.OrderWithCustomer
import com.example.data.local.entities.OrderEntity
import com.example.data.local.entities.PaymentEntity
import com.example.testing.AuthorizedShopTestRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class RecordingThermalPrinterClient(
  var pairedDevices: List<DiscoveredPrinter> = listOf(
    DiscoveredPrinter("RPP02N-58mm", "00:11:22:33:44:55", isBonded = true),
  ),
  var shouldFailPrint: Boolean = false,
) : ThermalPrinterClient {
  val sentPayloads = mutableListOf<Pair<String, ByteArray>>()
  val sentPacingModes = mutableListOf<PrinterPacingMode>()

  override fun isBluetoothSupported(): Boolean = true
  override fun isBluetoothEnabled(): Boolean = true
  override fun hasConnectPermission(): Boolean = true
  override fun hasScanPermission(): Boolean = true

  override suspend fun getPairedPrinters(): Result<List<DiscoveredPrinter>> =
    Result.success(pairedDevices)

  override fun discoverNearbyPrinters(): Flow<DiscoveredPrinter> = emptyFlow()

  override suspend fun printBytes(printerAddress: String, bytes: ByteArray): Result<Unit> =
    printBytes(printerAddress, bytes, PrinterPacingMode.FAST)

  override suspend fun printBytes(
    printerAddress: String,
    bytes: ByteArray,
    pacingMode: PrinterPacingMode,
  ): Result<Unit> {
    if (shouldFailPrint) {
      return Result.failure(IllegalStateException("Printer offline"))
    }
    sentPayloads.add(printerAddress to bytes)
    sentPacingModes.add(pacingMode)
    return Result.success(Unit)
  }
}

class ThermalReceiptFormatterTest {

  private fun sampleOrder(
    orderNumber: Int? = 1007,
    customerName: String = "Muhammad Tariq Mahmood Qureshi Al-Siddiqui",
  ): Pair<OrderWithCustomer, MeasurementSnapshot> {
    val meas = MeasurementSnapshot(
      kameezLambai = 41.5,
      tera = 18.25,
      bazu = 24.0,
      chati = 42.0,
      kamar = 38.0,
      gheraDaman = 43.0,
      galaCollar = 16.5,
      cuff = 9.5,
      shalwarLambai = 39.5,
      paincha = 7.75,
      gheraAsan = 16.0,
      notes = "Keep collar stiff with double buckram",
    )
    val style = StyleSnapshot(
      neckStyle = NeckStyle.BAN,
      damanStyle = DamanStyle.CHORASI,
      pocketStyle = PocketStyle.TWO_SIDE,
      sleeveStyle = SleeveStyle.SINGLE_CUFF,
      silaiStyle = SilaiStyle.DOUBLE,
    )
    val order = OrderEntity(
      orderId = "ord-1",
      shopId = "shop-1",
      customerId = "cust-1",
      orderNumber = orderNumber,
      suitCount = 2,
      deliveryDate = 1_764_000_000_000L,
      totalAmountPkr = 8000L,
      paidAmountPkr = 3000L,
      remainingAmountPkr = 5000L,
      orderStatus = OrderStatus.PENDING,
      paymentStatus = PaymentStatus.PARTIAL,
      measurementSnapshotJson = SnapshotJson.encodeMeasurement(meas),
      styleSnapshotJson = SnapshotJson.encodeStyle(style),
      notes = "Urgent Eid delivery before Jumma",
      createdAt = 1_763_500_000_000L,
      updatedAt = 1_763_500_000_000L,
      version = 1L,
      lastModifiedByDevice = "phone-1",
    )
    return OrderWithCustomer(
      order = order,
      customerName = customerName,
      customerPhone = "0300-5551234",
      customerCustomId = "429",
      customerAddress = "Dher, Rehmana Abad",
    ) to meas
  }

  @Test
  fun `58mm and 80mm customer receipt lines respect column widths and include PKR balances`() {
    val (detail, _) = sampleOrder(orderNumber = 1007)
    val payments = listOf(
      PaymentEntity("p1", "ord-1", 3000L, PaymentMethod.CASH, "Advance", 1_763_500_000_000L, "phone-1"),
    )

    val lines58 = ThermalReceiptFormatter.formatCustomerReceiptLines(
      shopName = "Al-Noor Bespoke Tailors Lahore",
      shopContact = "0300-1234567",
      orderDetail = detail,
      payments = payments,
      paperWidth = PaperWidth.MM_58,
    )
    assertTrue(lines58.all { it.length <= 32 })
    val joined58 = lines58.joinToString("\n")
    assertTrue(joined58.contains("ORDER #1007"))
    assertTrue(joined58.contains("Rs. 8,000"))
    assertTrue(joined58.contains("Rs. 3,000"))
    assertTrue(joined58.contains("Rs. 5,000"))

    val lines80 = ThermalReceiptFormatter.formatCustomerReceiptLines(
      shopName = "Al-Noor Bespoke Tailors Lahore",
      shopContact = "0300-1234567",
      orderDetail = detail,
      payments = payments,
      paperWidth = PaperWidth.MM_80,
    )
    assertTrue(lines80.all { it.length <= 48 })
  }

  @Test
  fun `offline pending order formats as NUMBER PENDING and karigar slip includes all 11 measurements`() {
    val (detail, meas) = sampleOrder(orderNumber = null, customerName = "Bilal Ahmed")
    val style = SnapshotJson.decodeStyle(detail.order.styleSnapshotJson)

    val karigar58 = ThermalReceiptFormatter.formatKarigarSlipLines(
      shopName = "Al-Noor Tailors",
      orderDetail = detail,
      measurement = meas,
      style = style,
      paperWidth = PaperWidth.MM_58,
    )
    assertTrue(karigar58.all { it.length <= 32 })
    val text = karigar58.joinToString("\n")
    assertTrue(text.contains("CUSTOMER INFORMATION"))
    assertTrue(text.contains("Order ID:") && text.contains("NUMBER PENDING"))
    assertTrue(text.contains("Customer ID:") && text.contains("429"))
    assertTrue(text.contains("Dher, Rehmana Abad"))
    assertTrue(text.contains("ADDITIONAL NOTES"))
    assertTrue(
      "Lambai and Tera should be paired on the same line to reduce paper length",
      karigar58.any { it.contains("Lambai: 41.5\"") && it.contains("Tera: 18.25\"") },
    )
    assertTrue(
      "Shalwar and Poncha should be paired on the same line",
      karigar58.any { it.contains("Shalwar: 39.5\"") && it.contains("Poncha: 7.75\"") },
    )
    assertTrue(text.contains("Ban"))
    assertTrue(text.contains("Chorasi"))
    assertTrue(text.contains("Double"))

    val karigar80 = ThermalReceiptFormatter.formatKarigarSlipLines(
      shopName = "Al-Noor Tailors",
      orderDetail = detail,
      measurement = meas,
      style = style,
      paperWidth = PaperWidth.MM_80,
    )
    assertTrue(karigar80.all { it.length <= 48 })
    assertTrue(karigar80.any { it.contains("Lambai: 41.5\"") && it.contains("Tera: 18.25\"") })
  }

  @Test
  fun `ESC POS byte encoding starts with INIT and ends with FEED_AND_CUT`() {
    val (detail, meas) = sampleOrder(orderNumber = 1012)
    val style = SnapshotJson.decodeStyle(detail.order.styleSnapshotJson)
    val bytes = ThermalReceiptFormatter.formatKarigarSlipBytes(
      shopName = "Al-Noor",
      orderDetail = detail,
      measurement = meas,
      style = style,
      paperWidth = PaperWidth.MM_58,
    )
    assertEquals(EscPosCommands.INIT[0], bytes[0])
    assertEquals(EscPosCommands.INIT[1], bytes[1])
    val tail = bytes.copyOfRange(bytes.size - EscPosCommands.FEED_AND_CUT.size, bytes.size)
    assertTrue(tail.contentEquals(EscPosCommands.FEED_AND_CUT))
  }

  @Test
  fun `long customer name wraps cleanly without truncation on 58mm receipt`() {
    val (detail, _) = sampleOrder(
      orderNumber = 1015,
      customerName = "Muhammad Tariq Mahmood Qureshi Al-Siddiqui",
    )
    val lines58 = ThermalReceiptFormatter.formatCustomerReceiptLines(
      shopName = "Al-Noor Tailors",
      shopContact = "0300-1234567",
      orderDetail = detail,
      payments = emptyList(),
      paperWidth = PaperWidth.MM_58,
      preserveUnicode = true,
    )
    assertTrue(lines58.all { it.length <= 32 })
    val joined = lines58.joinToString("\n")
    assertTrue(joined.contains("Muhammad Tariq Mahmood Qureshi"))
    assertTrue(joined.contains("Al-Siddiqui"))
  }

  @Test
  fun `urdu customer name shop name and notes render intact in preview and transliterate without question marks in bytes`() {
    val (baseDetail, meas) = sampleOrder(
      orderNumber = 1020,
      customerName = "محمد عاصم ملک",
    )
    val urduDetail = baseDetail.copy(
      order = baseDetail.order.copy(notes = "عید سے پہلے تیار کریں"),
    )
    val style = SnapshotJson.decodeStyle(urduDetail.order.styleSnapshotJson)

    val customerPreviewLines = ThermalReceiptFormatter.formatCustomerReceiptLines(
      shopName = "مدینہ ٹیلرز",
      shopContact = "0300-1234567",
      orderDetail = urduDetail,
      payments = emptyList(),
      paperWidth = PaperWidth.MM_58,
      preserveUnicode = true,
    )
    val customerPreview = customerPreviewLines.joinToString("\n")
    assertTrue(customerPreview.contains("محمد عاصم ملک"))
    assertTrue(customerPreview.contains("مدینہ ٹیلرز"))
    assertTrue(customerPreview.contains("عید سے پہلے تیار کریں"))
    assertFalse("Preview must never replace Urdu characters with '?'", customerPreview.contains("?"))

    val karigarPreviewLines = ThermalReceiptFormatter.formatKarigarSlipLines(
      shopName = "مدینہ ٹیلرز",
      orderDetail = urduDetail,
      measurement = meas,
      style = style,
      paperWidth = PaperWidth.MM_58,
      preserveUnicode = true,
    )
    val karigarPreview = karigarPreviewLines.joinToString("\n")
    assertTrue(karigarPreview.contains("محمد عاصم ملک"))
    assertTrue(karigarPreview.contains("مدینہ ٹیلرز"))
    assertFalse("Karigar preview must never replace Urdu characters with '?'", karigarPreview.contains("?"))

    val escPosBytes = ThermalReceiptFormatter.formatCustomerReceiptBytes(
      shopName = "مدینہ ٹیلرز",
      shopContact = "0300-1234567",
      orderDetail = urduDetail,
      payments = emptyList(),
      paperWidth = PaperWidth.MM_58,
    )
    val asciiText = escPosBytes.toString(Charsets.US_ASCII)
    assertFalse("ESC/POS output must not emit '?' placeholders", asciiText.contains("?"))
    assertTrue(asciiText.contains("Customer:"))
    assertTrue(asciiText.contains("mhmd aasm mlk"))
  }

  @Test
  fun `urdu language preference generates full urdu customer receipt and compact paired karigar slip`() {
    val (baseDetail, meas) = sampleOrder(
      orderNumber = 1025,
      customerName = "محمد عاصم ملک",
    )
    val urduDetail = baseDetail.copy(
      order = baseDetail.order.copy(notes = "عید سے پہلے تیار کریں"),
    )
    val style = SnapshotJson.decodeStyle(urduDetail.order.styleSnapshotJson)

    val customerLines58 = ThermalReceiptFormatter.formatCustomerReceiptLines(
      shopName = "مدینہ ٹیلرز",
      shopContact = "0300-1234567",
      orderDetail = urduDetail,
      payments = emptyList(),
      paperWidth = PaperWidth.MM_58,
      preserveUnicode = true,
      language = AppLanguage.URDU,
    )
    assertTrue(customerLines58.all { it.length <= 32 })
    val customerText = customerLines58.joinToString("\n")
    assertTrue(customerText.contains("گاہک کی رسید"))
    assertTrue(customerText.contains("گاہک: محمد عاصم ملک"))
    assertTrue(customerLines58.any { it.contains("سوٹ:") && it.contains("حالت:") })
    assertTrue(customerLines58.any { it.contains("بل:") && it.contains("ادا:") })
    assertTrue(customerText.contains("شکریہ!"))
    assertFalse(customerText.contains("?"))

    val karigarLines58 = ThermalReceiptFormatter.formatKarigarSlipLines(
      shopName = "مدینہ ٹیلرز",
      orderDetail = urduDetail,
      measurement = meas,
      style = style,
      paperWidth = PaperWidth.MM_58,
      preserveUnicode = true,
      language = AppLanguage.URDU,
    )
    assertTrue(karigarLines58.all { it.length <= 32 })
    val karigarText = karigarLines58.joinToString("\n")
    assertTrue(karigarText.contains("کسٹمر کی معلومات"))
    assertTrue(karigarText.contains("آرڈر آئی ڈی: \u2066#1025\u2069"))
    assertTrue(karigarText.contains("کسٹمر آئی ڈی: \u2066429\u2069"))
    assertTrue(karigarText.contains("کاریگر ورکشاپ پرچی"))
    assertTrue(karigarText.contains("ناپ (انچ)"))
    assertTrue(karigarText.contains("سلائی و ڈیزائن"))
    assertTrue(karigarText.contains("اضافی نوٹ"))
    assertTrue(
      "Lambai and Tera must be on same line in Urdu karigar slip",
      karigarLines58.any { it.contains("قمیض لمبائی: 41.5") && it.contains("تیرا: 18.25") },
    )
    assertTrue(karigarLines58.any { it.contains("آستین: 24") && it.contains("گلا: 16.5") })
    assertTrue(karigarLines58.any { it.contains("سینہ: 42") && it.contains("گھیرا / دامن: 43") })
    assertTrue(karigarLines58.any { it.contains("شلوار: 39.5") && it.contains("پانچہ: 7.75") })
    assertTrue(karigarLines58.any { it.contains("گلا: بین") && it.contains("دامن: چورس") })
    // Verify compulsory 1..8 lines come BEFORE optional Kamar line
    val ponchaIdx = karigarLines58.indexOfFirst { it.contains("شلوار: 39.5") && it.contains("پانچہ: 7.75") }
    val kamarIdx = karigarLines58.indexOfFirst { it.contains("کمر: 38") }
    assertTrue(ponchaIdx in 0 until kamarIdx)
    assertFalse(karigarText.contains("?"))
  }

  @Test
  fun `buildKarigarParchiData structures customer info, 1 to 8 cells, optionals, and additional notes for Urdu and English`() {
    val (detail, meas) = sampleOrder(orderNumber = 1042, customerName = "Junaid")
    val style = SnapshotJson.decodeStyle(detail.order.styleSnapshotJson).copy(
      sleeveStyle = SleeveStyle.KAF,
      shoulderType = com.example.core.model.ShoulderType.FULL_DOWN_SHOULDER,
      shalwarType = com.example.core.model.ShalwarType.BALOCHI,
      silaiStyle = SilaiStyle.KAMDO_DHAGA,
    )
    val measWithDualAndGhair = meas.copy(
      shalwarGhair = 92.0,
      rawValues = mapOf("BAZU" to "23/12"),
    )

    val urduParchi = ThermalReceiptFormatter.buildKarigarParchiData(
      shopName = "الرحمان ٹیلرز",
      orderDetail = detail,
      measurement = measWithDualAndGhair,
      style = style,
      language = AppLanguage.URDU,
    )
    assertEquals("کسٹمر کی معلومات", urduParchi.customerInfoTitle)
    assertEquals("#1042", urduParchi.orderIdValue)
    assertEquals("429", urduParchi.customerIdValue)
    assertEquals(
      listOf("قمیض لمبائی", "تیرا", "آستین", "گلا", "سینہ", "گھیرا / دامن", "شلوار", "پانچہ"),
      urduParchi.compulsoryMetrics.map { it.label },
    )
    assertEquals("23/12", urduParchi.compulsoryMetrics.first { it.label == "آستین" }.value)
    assertEquals(listOf("کمر", "کف", "آسن", "شلوار گھیرا"), urduParchi.optionalMetrics.map { it.label })
    assertEquals("اضافی نوٹ", urduParchi.additionalNotesTitle)
    assertTrue(urduParchi.additionalNoteLines.any { it.contains("بین") })
    assertTrue(urduParchi.additionalNoteLines.any { it.contains("کف") })
    assertTrue(urduParchi.additionalNoteLines.any { it.contains("فل ڈاؤن شولڈر") })
    assertTrue(urduParchi.additionalNoteLines.any { it.contains("بلوچی") })
    assertTrue(urduParchi.additionalNoteLines.any { it.contains("کمدو دھاگہ") })

    val englishMinimalParchi = ThermalReceiptFormatter.buildKarigarParchiData(
      shopName = "Al-Rehman Tailors",
      orderDetail = detail,
      measurement = meas.copy(
        kamar = 0.0,
        cuff = 0.0,
        gheraAsan = 0.0,
        shalwarGhair = 92.0,
        rawValues = mapOf("BAZU" to "23/12"),
      ),
      style = StyleSnapshot(
        sleeveStyle = SleeveStyle.KURTA,
        shoulderType = com.example.core.model.ShoulderType.HANGER_SHOULDER,
        shalwarType = com.example.core.model.ShalwarType.SMART,
        silaiStyle = SilaiStyle.KAMDO_DHAGA,
      ),
      language = AppLanguage.ENGLISH,
    )
    assertEquals("CUSTOMER INFORMATION", englishMinimalParchi.customerInfoTitle)
    assertEquals("#1042", englishMinimalParchi.orderIdValue)
    assertEquals("429", englishMinimalParchi.customerIdValue)
    assertEquals(
      listOf("Kameez Lambai", "Teera", "Asteen", "Gala", "Seena", "Ghera / Daman", "Shalwar", "Poncha"),
      englishMinimalParchi.compulsoryMetrics.map { it.label },
    )
    assertEquals("23/12\"", englishMinimalParchi.compulsoryMetrics.first { it.label == "Asteen" }.value)
    assertEquals(listOf("Shalwar Ghair"), englishMinimalParchi.optionalMetrics.map { it.label })
    assertTrue(englishMinimalParchi.additionalNoteLines.any { it.contains("Kurta") })
    assertTrue(englishMinimalParchi.additionalNoteLines.any { it.contains("Hanger Shoulder") })
    assertTrue(englishMinimalParchi.additionalNoteLines.any { it.contains("Smart") })
    assertTrue(englishMinimalParchi.additionalNoteLines.any { it.contains("Kamdo Dhaga") })
  }

  @Test
  fun `slips omit unentered optional measurements and unselected styles while keeping 1 to 8 order`() {
    val (detail, _) = sampleOrder(orderNumber = 1030, customerName = "Bilal Ahmed")
    val minimalMeas = MeasurementSnapshot(
      kameezLambai = 40.0,
      tera = 18.0,
      bazu = 24.0,
      chati = 41.0,
      kamar = 0.0,
      gheraDaman = 42.0,
      galaCollar = 16.0,
      cuff = 0.0,
      shalwarLambai = 39.0,
      paincha = 7.5,
      gheraAsan = 0.0,
    )
    val emptyStyle = StyleSnapshot()

    val karigarLines = ThermalReceiptFormatter.formatKarigarSlipLines(
      shopName = "Al-Noor Tailors",
      orderDetail = detail,
      measurement = minimalMeas,
      style = emptyStyle,
      paperWidth = PaperWidth.MM_58,
    )
    val karigarText = karigarLines.joinToString("\n")
    assertFalse(karigarText.contains("Kamar"))
    assertFalse(karigarText.contains("Cuff"))
    assertFalse(karigarText.contains("Asan"))
    assertFalse(karigarText.contains("Shalwar Ghair"))
    assertFalse(karigarText.contains("STITCHING STYLES"))
    assertFalse(karigarText.contains("None"))

    // Verify 1..8 unified pairing order: (Lambai, Tera), (Asteen, Gala), (Seena, Ghera), (Shalwar, Poncha)
    val lambaiIdx = karigarLines.indexOfFirst { it.contains("Lambai:") && it.contains("Tera:") }
    val asteenIdx = karigarLines.indexOfFirst { it.contains("Asteen:") && it.contains("Gala:") }
    val seenaIdx = karigarLines.indexOfFirst { it.contains("Seena:") && it.contains("Ghera:") }
    val shalwarIdx = karigarLines.indexOfFirst { it.contains("Shalwar:") && it.contains("Poncha:") }
    assertTrue(lambaiIdx in 0 until asteenIdx)
    assertTrue(asteenIdx < seenaIdx)
    assertTrue(seenaIdx < shalwarIdx)

    val measSlipUrdu = ThermalReceiptFormatter.formatMeasurementSlipLines(
      shopName = "مدینہ ٹیلرز",
      customerName = "بلال احمد",
      customerPhone = "0300-1234567",
      customerDisplayId = "5",
      measurementLabel = "Standard",
      measurement = minimalMeas,
      style = emptyStyle,
      recordedAtMillis = detail.order.deliveryDate,
      paperWidth = PaperWidth.MM_58,
      preserveUnicode = true,
      language = AppLanguage.URDU,
    ).joinToString("\n")
    assertFalse(measSlipUrdu.contains("کمر:"))
    assertFalse(measSlipUrdu.contains("کف:"))
    assertFalse(measSlipUrdu.contains("آسن:"))
    assertFalse(measSlipUrdu.contains("شلوار گھیرا:"))
    assertFalse(measSlipUrdu.contains("سلائی و ڈیزائن"))
    assertFalse(measSlipUrdu.contains("کوئی نہیں"))
  }

  @Test
  fun `urdu dates and phone numbers preserve BiDi visual ordering in both LTR and RTL paragraphs`() {
    val sampleMillis = 1_795_200_000_000L // Nov 2026
    val urLocale = java.util.Locale.forLanguageTag("ur")
    val formattedDate = com.example.core.ui.DateFormatUtils.formatDate(sampleMillis, urLocale)
    val formattedDateTime = com.example.core.ui.DateFormatUtils.formatDateTime(sampleMillis, urLocale)

    // Strip isolate marks to inspect logical components, and check visual order via java.text.Bidi under LTR
    assertTrue("Urdu date must use RLI/PDI isolation", formattedDate.startsWith("\u2067") && formattedDate.endsWith("\u2069"))
    val ltrBidi = java.text.Bidi(formattedDate, java.text.Bidi.DIRECTION_LEFT_TO_RIGHT)
    val visualTokens = buildString {
      val visual = java.lang.StringBuilder()
      val runs = ltrBidi.runCount
      val runLevels = ByteArray(runs) { ltrBidi.getRunLevel(it).toByte() }
      val objs = Array<Any>(runs) { it }
      java.text.Bidi.reorderVisually(runLevels, 0, objs, 0, runs)
      for (obj in objs) {
        val r = obj as Int
        val sub = formattedDate.substring(ltrBidi.getRunStart(r), ltrBidi.getRunLimit(r))
        val text = if (ltrBidi.getRunLevel(r) % 2 == 1) sub.reversed() else sub
        visual.append(text)
      }
      append(visual.toString())
    }.replace(Regex("[\u2066-\u2069]"), "").trim()
    // Ensure day digits and year digits are separated by the month name, never adjacent like "21 2026"
    assertFalse(
      "Urdu date must never place day and year adjacent: '$visualTokens'",
      Regex("\\d+\\s+\\d+").containsMatchIn(visualTokens),
    )
    assertTrue(
      "Urdu date must visually place Arabic month between digits: '$visualTokens'",
      Regex("\\d+\\s+[^\\d\\s]+\\s+\\d+").containsMatchIn(visualTokens),
    )
    assertTrue("Urdu dateTime must include clean LTR time isolate", formattedDateTime.contains("\u2066"))

    val (detail, meas) = sampleOrder(orderNumber = 1055, customerName = "محمد اظہر حسین")
    val phoneDetail = detail.copy(customerPhone = "0301-2823184")
    val style = SnapshotJson.decodeStyle(phoneDetail.order.styleSnapshotJson)
    val karigarUrdu = ThermalReceiptFormatter.formatKarigarSlipLines(
      shopName = "الرحمان ٹیلرز",
      orderDetail = phoneDetail,
      measurement = meas,
      style = style,
      paperWidth = PaperWidth.MM_58,
      preserveUnicode = true,
      language = AppLanguage.URDU,
    )
    assertTrue(karigarUrdu.all { it.length <= 32 })
    val mobileLine = karigarUrdu.first { it.contains("موبائل:") }
    assertTrue(
      "Phone line must isolate 0301-2823184 in LRI/PDI so it never reverses to 2823184-0301",
      mobileLine.contains("\u20660301-2823184\u2069"),
    )

    val escPosAscii = ThermalReceiptFormatter.formatCustomerReceiptBytes(
      shopName = "الرحمان ٹیلرز",
      shopContact = "0301-2823184",
      orderDetail = phoneDetail,
      payments = emptyList(),
      paperWidth = PaperWidth.MM_58,
      language = AppLanguage.ENGLISH,
    ).toString(Charsets.US_ASCII)
    assertTrue(escPosAscii.contains("0301-2823184"))
    assertFalse(escPosAscii.contains("?"))
  }

  @Test
  fun `auto cutter modes emit Function A GS V 0, GS V 1, or feed-only without GS V B`() {
    val (detail, _) = sampleOrder(orderNumber = 1099)

    val fullCut = ThermalReceiptFormatter.formatCustomerReceiptBytes(
      shopName = "Al-Noor",
      shopContact = "0300-1234567",
      orderDetail = detail,
      payments = emptyList(),
      paperWidth = PaperWidth.MM_58,
      language = AppLanguage.ENGLISH,
      autoCutterMode = AutoCutterMode.FULL_CUT,
    )
    assertTrue(
      fullCut.copyOfRange(fullCut.size - EscPosCommands.FEED_AND_CUT.size, fullCut.size)
        .contentEquals(EscPosCommands.FEED_AND_CUT),
    )
    assertEquals(0x00.toByte(), fullCut.last())
    assertEquals(0x56.toByte(), fullCut[fullCut.size - 2])

    val partialCut = ThermalReceiptFormatter.formatCustomerReceiptBytes(
      shopName = "Al-Noor",
      shopContact = "0300-1234567",
      orderDetail = detail,
      payments = emptyList(),
      paperWidth = PaperWidth.MM_58,
      language = AppLanguage.ENGLISH,
      autoCutterMode = AutoCutterMode.PARTIAL_CUT,
    )
    assertTrue(
      partialCut.copyOfRange(partialCut.size - EscPosCommands.FEED_AND_PARTIAL_CUT.size, partialCut.size)
        .contentEquals(EscPosCommands.FEED_AND_PARTIAL_CUT),
    )

    val cutterOff = ThermalReceiptFormatter.formatCustomerReceiptBytes(
      shopName = "Al-Noor",
      shopContact = "0300-1234567",
      orderDetail = detail,
      payments = emptyList(),
      paperWidth = PaperWidth.MM_58,
      language = AppLanguage.ENGLISH,
      autoCutterMode = AutoCutterMode.OFF,
    )
    assertTrue(
      cutterOff.copyOfRange(cutterOff.size - EscPosCommands.FEED_ONLY.size, cutterOff.size)
        .contentEquals(EscPosCommands.FEED_ONLY),
    )
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BluetoothPrintingUiRobolectricTest {

  private val recordingClient = RecordingThermalPrinterClient()
  private val authRule = AuthorizedShopTestRule(
    initialSignedIn = true,
    authorizeLocalDevice = true,
    initialOnline = false,
    thermalPrinterClientOverride = recordingClient,
  )
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `settings selects bluetooth printer, runs test print, and order detail previews karigar slip`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val orderId = runBlocking {
      val cust = app.container.customerRepository.createCustomer(
        name = "Salman Raza",
        rawPhone = "0300-4455667",
        deviceId = "phone-1",
      ).getOrThrow()
      app.container.orderRepository.createOrder(
        customerId = cust.customerId,
        suitCount = 1,
        deliveryDateMillis = System.currentTimeMillis() + 86_400_000L,
        totalAmountPkr = 3500L,
        initialAdvancePkr = 1000L,
        measurementSnapshot = MeasurementSnapshot(
          kameezLambai = 40.0,
          tera = 18.0,
          bazu = 24.0,
          chati = 41.0,
          kamar = 37.0,
          gheraDaman = 42.0,
          galaCollar = 16.0,
          cuff = 9.0,
          shalwarLambai = 39.0,
          paincha = 7.5,
          gheraAsan = 15.5,
        ),
        styleSnapshot = StyleSnapshot(
          neckStyle = NeckStyle.COLLAR,
          damanStyle = DamanStyle.GOL,
          pocketStyle = PocketStyle.TWO_SIDE,
          sleeveStyle = SleeveStyle.PLAIN,
          silaiStyle = SilaiStyle.SINGLE,
        ),
        deviceId = "phone-1",
      ).getOrThrow().orderId
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    // Select printer in Settings via drawer & run test print
    composeTestRule.onNodeWithTag("dashboard_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drawer_item_settings").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_screen")
      .performScrollToNode(hasTestTag("settings_select_printer_button"))
    composeTestRule.onNodeWithTag("settings_select_printer_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("printer_picker_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("printer_item_00:11:22:33:44:55").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_screen")
      .performScrollToNode(hasTestTag("settings_configured_printer_text"))
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      runCatching {
        composeTestRule.onNodeWithTag("settings_configured_printer_text")
          .assertTextContains("RPP02N-58mm", substring = true)
      }.isSuccess
    }

    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      composeTestRule.onAllNodesWithText("Saved printer", substring = true)
        .fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag("settings_screen")
      .performScrollToNode(hasTestTag("settings_test_print_button"))
    composeTestRule.onNodeWithTag("settings_screen").performTouchInput {
      swipeUp(startY = centerY, endY = top + 40f)
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_test_print_button")
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      recordingClient.sentPayloads.isNotEmpty() ||
        composeTestRule.onAllNodesWithTag("settings_printer_error_text")
          .fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals(
      "Unexpected printer error shown in UI",
      0,
      composeTestRule.onAllNodesWithTag("settings_printer_error_text").fetchSemanticsNodes().size,
    )
    assertEquals(1, recordingClient.sentPayloads.size)
    assertEquals("00:11:22:33:44:55", recordingClient.sentPayloads.first().first)

    // Navigate back to Dashboard -> Orders -> open order -> Preview Karigar Slip
    composeTestRule.onNodeWithTag("topbar_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("dashboard_tile_orders").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("order_card_$orderId").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_card_$orderId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("order_detail_content").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_preview_karigar_button"))
    composeTestRule.onNodeWithTag("order_preview_karigar_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("slip_preview_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("slip_preview_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("slip_preview_text")
      .assertTextContains("CUSTOMER INFORMATION", substring = true)
      .assertTextContains("Order ID:", substring = true)
      .assertTextContains("Customer ID:", substring = true)
      .assertTextContains("Kameez Lambai", substring = true)
      .assertTextContains("Teera", substring = true)
      .assertTextContains("Poncha", substring = true)
  }

  @Test
  fun `order detail previews customer receipt and karigar slip with Urdu customer name without question marks`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val urduName = "محمد عاصم ملک"
    val orderId = runBlocking {
      val cust = app.container.customerRepository.createCustomer(
        name = urduName,
        rawPhone = "0300-9988776",
        deviceId = "phone-1",
      ).getOrThrow()
      app.container.orderRepository.createOrder(
        customerId = cust.customerId,
        suitCount = 1,
        deliveryDateMillis = System.currentTimeMillis() + 86_400_000L,
        totalAmountPkr = 4200L,
        initialAdvancePkr = 1200L,
        measurementSnapshot = MeasurementSnapshot(
          kameezLambai = 40.0,
          tera = 18.0,
          bazu = 24.0,
          chati = 41.0,
          kamar = 37.0,
          gheraDaman = 42.0,
          galaCollar = 16.0,
          cuff = 9.0,
          shalwarLambai = 39.0,
          paincha = 7.5,
          gheraAsan = 15.5,
        ),
        styleSnapshot = StyleSnapshot(
          neckStyle = NeckStyle.BAN,
          damanStyle = DamanStyle.GOL,
          pocketStyle = PocketStyle.TWO_SIDE,
          sleeveStyle = SleeveStyle.PLAIN,
          silaiStyle = SilaiStyle.SINGLE,
        ),
        notes = "عید آرڈر",
        deviceId = "phone-1",
      ).getOrThrow().orderId
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_tile_orders").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("order_card_$orderId").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_card_$orderId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("order_detail_content").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_preview_customer_button"))
    composeTestRule.onNodeWithTag("order_preview_customer_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("slip_preview_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("slip_preview_text")
      .assertTextContains(urduName, substring = true)
    assertTrue(
      composeTestRule.onAllNodesWithText("???", substring = true).fetchSemanticsNodes().isEmpty(),
    )

    composeTestRule.onNodeWithText("Close").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_preview_karigar_button"))
    composeTestRule.onNodeWithTag("order_preview_karigar_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("slip_preview_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("slip_preview_text")
      .assertTextContains(urduName, substring = true)
      .assertTextContains("CUSTOMER INFORMATION", substring = true)
      .assertTextContains("Kameez Lambai", substring = true)
    assertTrue(
      composeTestRule.onAllNodesWithText("???", substring = true).fetchSemanticsNodes().isEmpty(),
    )
  }

  @Test
  fun `urdu language preference previews slips in Urdu with paired measurements and prints Urdu raster bytes`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val urduName = "محمد عاصم ملک"
    val orderId = runBlocking {
      app.container.shopPreferencesRepository.updateLanguage(AppLanguage.URDU)
      val cust = app.container.customerRepository.createCustomer(
        name = urduName,
        rawPhone = "0300-7766554",
        deviceId = "phone-1",
      ).getOrThrow()
      app.container.orderRepository.createOrder(
        customerId = cust.customerId,
        suitCount = 2,
        deliveryDateMillis = System.currentTimeMillis() + 86_400_000L,
        totalAmountPkr = 6000L,
        initialAdvancePkr = 2000L,
        measurementSnapshot = MeasurementSnapshot(
          kameezLambai = 40.0,
          tera = 18.0,
          bazu = 24.0,
          chati = 41.0,
          kamar = 37.0,
          gheraDaman = 42.0,
          galaCollar = 16.0,
          cuff = 9.0,
          shalwarLambai = 39.0,
          paincha = 7.5,
          gheraAsan = 15.5,
        ),
        styleSnapshot = StyleSnapshot(
          neckStyle = NeckStyle.BAN,
          damanStyle = DamanStyle.GOL,
          pocketStyle = PocketStyle.TWO_SIDE,
          sleeveStyle = SleeveStyle.PLAIN,
          silaiStyle = SilaiStyle.DOUBLE,
        ),
        deviceId = "phone-1",
      ).getOrThrow().orderId
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_tile_orders").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("order_card_$orderId").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_card_$orderId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("order_detail_content").fetchSemanticsNodes().isNotEmpty()
    }

    // Preview customer receipt in Urdu
    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_preview_customer_button"))
    composeTestRule.onNodeWithTag("order_preview_customer_button")
      .assertTextContains("گاہک رسید دیکھیں", substring = true)
      .performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("slip_preview_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("slip_preview_text")
      .assertTextContains("گاہک کی رسید", substring = true)
      .assertTextContains(urduName, substring = true)
      .assertTextContains("بقایا: 4,000 روپے", substring = true)

    composeTestRule.onNodeWithTag("slip_preview_close_button")
      .assertTextContains("بند کریں", substring = true)
      .performClick()
    composeTestRule.waitForIdle()

    // Preview karigar slip in Urdu & verify Lambai + Tera appear on the same line
    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_preview_karigar_button"))
    composeTestRule.onNodeWithTag("order_preview_karigar_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("slip_preview_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("slip_preview_text")
      .assertTextContains("کسٹمر کی معلومات", substring = true)
      .assertTextContains("آرڈر آئی ڈی:", substring = true)
      .assertTextContains("کسٹمر آئی ڈی:", substring = true)
      .assertTextContains("0300-7766554", substring = true)
      .assertTextContains("قمیض لمبائی", substring = true)
      .assertTextContains("40", substring = true)
      .assertTextContains("تیرا", substring = true)
      .assertTextContains("18", substring = true)
      .assertTextContains("پانچہ", substring = true)
      .assertTextContains("اضافی نوٹ", substring = true)

    // Verify Parchi 2-column grid keeps Kameez Lambai on the LEFT and Teera on the RIGHT (matching saved measurements)
    composeTestRule.onNodeWithTag("parchi_compulsory_0", useUnmergedTree = true).performScrollTo()
    composeTestRule.waitForIdle()
    val lambaiBounds = composeTestRule.onNodeWithTag("parchi_compulsory_0", useUnmergedTree = true).getBoundsInRoot()
    val teraBounds = composeTestRule.onNodeWithTag("parchi_compulsory_1", useUnmergedTree = true).getBoundsInRoot()
    assertTrue(
      "Kameez Lambai (${lambaiBounds.left}..${lambaiBounds.right}) must appear on the left of Teera (${teraBounds.left}..${teraBounds.right})",
      lambaiBounds.right > lambaiBounds.left && lambaiBounds.left < teraBounds.left,
    )

    // Verify Urdu ESC/POS raster bytes for all slip types use INIT + ALIGN_LEFT + small <=24-dot GS v 0 bands + FEED_AND_CUT
    val previewedOrderDetail = runBlocking {
      app.container.orderRepository.observeOrderDetail(orderId).first()!!
    }
    val measSnapshot = MeasurementSnapshot(
      kameezLambai = 40.0,
      tera = 18.0,
      bazu = 24.0,
      chati = 41.0,
      kamar = 37.0,
      gheraDaman = 42.0,
      galaCollar = 16.0,
      cuff = 9.0,
      shalwarLambai = 39.0,
      paincha = 7.5,
      gheraAsan = 15.5,
    )
    val styleSnapshot = StyleSnapshot(
      neckStyle = NeckStyle.BAN,
      damanStyle = DamanStyle.GOL,
      pocketStyle = PocketStyle.TWO_SIDE,
      sleeveStyle = SleeveStyle.PLAIN,
      silaiStyle = SilaiStyle.DOUBLE,
    )

    fun assertValidRasterBytes(bytes: ByteArray, widthMm: Int) {
      assertEquals(EscPosCommands.INIT[0], bytes[0])
      assertEquals(EscPosCommands.INIT[1], bytes[1])
      assertEquals(EscPosCommands.ALIGN_LEFT[0], bytes[2])
      assertEquals(EscPosCommands.ALIGN_LEFT[1], bytes[3])
      assertEquals(EscPosCommands.ALIGN_LEFT[2], bytes[4])

      var idx = 5
      val rasterEnd = bytes.size - EscPosCommands.FEED_AND_CUT.size
      val expectedBytesPerRow = if (widthMm == 80) 72 else 48
      var bandCount = 0
      var nonZeroRasterBytes = 0
      while (idx < rasterEnd) {
        assertEquals(0x1D.toByte(), bytes[idx])
        assertEquals(0x76.toByte(), bytes[idx + 1])
        assertEquals(0x30.toByte(), bytes[idx + 2])
        assertEquals(0x00.toByte(), bytes[idx + 3])
        val xL = bytes[idx + 4].toInt() and 0xFF
        val xH = bytes[idx + 5].toInt() and 0xFF
        val yL = bytes[idx + 6].toInt() and 0xFF
        val yH = bytes[idx + 7].toInt() and 0xFF
        val xBytes = xL + (xH shl 8)
        val yRows = yL + (yH shl 8)
        assertEquals(expectedBytesPerRow, xBytes)
        assertTrue("GS v 0 band height ($yRows) must stay <= 24 rows to prevent printer buffer overflow", yRows in 1..24)
        val bandPayloadLen = xBytes * yRows
        for (p in idx + 8 until idx + 8 + bandPayloadLen) {
          if (bytes[p].toInt() != 0) nonZeroRasterBytes++
        }
        idx += 8 + bandPayloadLen
        bandCount++
      }
      assertEquals("GS v 0 band stream must align cleanly to FEED_AND_CUT", rasterEnd, idx)
      assertTrue("Expected multiple raster bands, saw $bandCount", bandCount >= 4)
      assertTrue("Rendered Urdu raster must contain non-zero ink dots", nonZeroRasterBytes > 50)
      val tail = bytes.copyOfRange(rasterEnd, bytes.size)
      assertTrue(tail.contentEquals(EscPosCommands.FEED_AND_CUT))
    }

    val karigarBytes58 = ThermalReceiptFormatter.formatKarigarSlipBytes(
      shopName = "درزی لیجر",
      orderDetail = previewedOrderDetail,
      measurement = measSnapshot,
      style = styleSnapshot,
      paperWidth = PaperWidth.MM_58,
      language = AppLanguage.URDU,
    )
    assertValidRasterBytes(karigarBytes58, 58)

    val customerBytes58 = ThermalReceiptFormatter.formatCustomerReceiptBytes(
      shopName = "درزی لیجر",
      shopContact = "0300-1234567",
      orderDetail = previewedOrderDetail,
      payments = emptyList(),
      paperWidth = PaperWidth.MM_58,
      language = AppLanguage.URDU,
    )
    assertValidRasterBytes(customerBytes58, 58)

    val testBytes80 = ThermalReceiptFormatter.formatTestPageBytes(
      shopName = "درزی لیجر",
      printerName = "Xprinter-80",
      paperWidth = PaperWidth.MM_80,
      language = AppLanguage.URDU,
    )
    assertValidRasterBytes(testBytes80, 80)

    // Verify legacy ESC * 33 compatibility mode emits 24-dot column-major bands + Partial Cut
    val legacyBytes58 = ThermalReceiptFormatter.formatKarigarSlipBytes(
      shopName = "درزی لیجر",
      orderDetail = previewedOrderDetail,
      measurement = measSnapshot,
      style = styleSnapshot,
      paperWidth = PaperWidth.MM_58,
      language = AppLanguage.URDU,
      autoCutterMode = AutoCutterMode.PARTIAL_CUT,
      useLegacyBitImage = true,
    )
    assertEquals(EscPosCommands.INIT[0], legacyBytes58[0])
    assertEquals(EscPosCommands.INIT[1], legacyBytes58[1])
    // Bytes 5..7 should be LINE_SPACING_24_DOTS (1B 33 18), followed by ESC * 33 (1B 2A 21)
    assertEquals(0x1B.toByte(), legacyBytes58[5])
    assertEquals(0x33.toByte(), legacyBytes58[6])
    assertEquals(0x18.toByte(), legacyBytes58[7])
    assertEquals(0x1B.toByte(), legacyBytes58[8])
    assertEquals(0x2A.toByte(), legacyBytes58[9])
    assertEquals(0x21.toByte(), legacyBytes58[10])
    val legacyTail = legacyBytes58.copyOfRange(
      legacyBytes58.size - EscPosCommands.FEED_AND_PARTIAL_CUT.size,
      legacyBytes58.size,
    )
    assertTrue(legacyTail.contentEquals(EscPosCommands.FEED_AND_PARTIAL_CUT))

    // Verify ThermalPrintManager honors SAFE pacing and AutoCutterMode.OFF from preferences
    runBlocking {
      app.container.deviceSyncRepository.savePrinterConfig(
        printerAddress = "00:11:22:33:44:55",
        printerName = "RPP02N-58mm",
        paperWidthMm = 58,
      )
      app.container.shopPreferencesRepository.updatePrinterCompatibilityMode(PrinterCompatibilityMode.COMPATIBLE_BITIMAGE)
      app.container.shopPreferencesRepository.updateAutoCutterMode(AutoCutterMode.OFF)
      recordingClient.sentPayloads.clear()
      recordingClient.sentPacingModes.clear()
      val result = app.container.thermalPrintManager.printTestPage()
      assertTrue(result.isSuccess)
      assertEquals(listOf(PrinterPacingMode.SAFE), recordingClient.sentPacingModes)
      val sent = recordingClient.sentPayloads.single().second
      assertTrue(
        sent.copyOfRange(sent.size - EscPosCommands.FEED_ONLY.size, sent.size)
          .contentEquals(EscPosCommands.FEED_ONLY),
      )
    }
  }
}
