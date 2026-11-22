package com.example.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TailorValidationUnitTest {

  @Test
  fun `pakistani phone normalization handles +92, 0092, dashes, and spaces`() {
    assertEquals("03001234567", PhoneNumberUtils.normalizePakistaniPhone("+92 300 1234567"))
    assertEquals("03001234567", PhoneNumberUtils.normalizePakistaniPhone("0092-300-1234567"))
    assertEquals("03001234567", PhoneNumberUtils.normalizePakistaniPhone("923001234567"))
    assertEquals("03001234567", PhoneNumberUtils.normalizePakistaniPhone("0300-1234567"))
    assertEquals("03001234567", PhoneNumberUtils.normalizePakistaniPhone("3001234567"))
    assertEquals("0300-1234567", PhoneNumberUtils.formatPakistaniPhone("+923001234567"))
  }

  @Test
  fun `order status transitions enforce valid workflow`() {
    assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.READY))
    assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED))
    assertFalse(OrderStatus.PENDING.canTransitionTo(OrderStatus.DELIVERED))

    assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.DELIVERED))
    assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.CANCELLED))

    assertFalse(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.PENDING))
    assertFalse(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.READY))
  }

  @Test
  fun `payment validation prevents negative or excess PKR payments`() {
    assertNotNull(TailorValidators.validatePayment(0L, 1500L))
    assertNotNull(TailorValidators.validatePayment(-200L, 1500L))
    assertNotNull(TailorValidators.validatePayment(1501L, 1500L))
    assertNull(TailorValidators.validatePayment(1500L, 1500L))
    assertNull(TailorValidators.validatePayment(500L, 1500L))
  }

  @Test
  fun `measurement validation checks realistic inch bounds and optional fields`() {
    val valid = MeasurementSnapshot(
      kameezLambai = 41.5,
      tera = 18.0,
      bazu = 24.5,
      chati = 42.0,
      kamar = 0.0,
      gheraDaman = 43.0,
      galaCollar = 16.5,
      cuff = 0.0,
      shalwarLambai = 39.5,
      paincha = 7.5,
      gheraAsan = 0.0,
    )
    assertNull(TailorValidators.validateMeasurement(valid))
    assertNotNull(TailorValidators.validateMeasurement(valid.copy(kameezLambai = 5.0)))
    assertNotNull(TailorValidators.validateMeasurement(valid.copy(paincha = 30.0)))
    assertNotNull(TailorValidators.validateMeasurement(valid.copy(galaCollar = 0.0)))
    assertNotNull(TailorValidators.validateMeasurement(valid.copy(cuff = 1.0)))
    assertNotNull(TailorValidators.validateMeasurement(valid.copy(kamar = 8.0)))
    assertNull(TailorValidators.validateMeasurement(valid.copy(gheraAsan = 55.0)))
    assertNotNull(TailorValidators.validateMeasurement(valid.copy(gheraAsan = 65.0)))
    assertNotNull(TailorValidators.validateMeasurement(valid.copy(shalwarGhair = 15.0)))
    assertNotNull(TailorValidators.validateMeasurement(valid.copy(shalwarGhair = 135.0)))
    assertNull(
      TailorValidators.validateMeasurement(
        valid.copy(kamar = 38.5, cuff = 9.5, gheraAsan = 55.0, shalwarGhair = 90.0),
      ),
    )
  }

  @Test
  fun `fraction parser, dual slash parser, and real-time field validation handle ints, floats, and 23 slash 12 format`() {
    assertEquals(41.5, TailorValidators.parseMeasurementInches("41.5")!!, 0.0001)
    assertEquals(41.5, TailorValidators.parseMeasurementInches("41 1/2")!!, 0.0001)
    assertEquals(41.75, TailorValidators.parseMeasurementInches("41-3/4")!!, 0.0001)
    assertEquals(0.25, TailorValidators.parseMeasurementInches("1/4")!!, 0.0001)
    val dual = TailorValidators.parseMeasurementDetails("23/12")!!
    assertTrue(dual.isDualSlash)
    assertEquals(23.0, dual.primary, 0.0001)
    assertEquals(12.0, dual.secondary!!, 0.0001)
    val dualFloat = TailorValidators.parseMeasurementDetails("23.5/12.5")!!
    assertTrue(dualFloat.isDualSlash)
    assertEquals(23.5, dualFloat.primary, 0.0001)
    assertEquals(12.5, dualFloat.secondary!!, 0.0001)
    assertEquals("23/12", TailorValidators.normalizeRawDualValue(" 23/12 "))
    assertNull(TailorValidators.normalizeRawDualValue("1/2"))
    assertNull(TailorValidators.normalizeRawDualValue("41 1/2"))
    assertNull(TailorValidators.parseMeasurementInches(""))
    assertNull(TailorValidators.parseMeasurementInches("41."))
    assertNull(TailorValidators.parseMeasurementInches("41 1/0"))
    assertNull(TailorValidators.parseMeasurementInches("23/"))
    assertNull(TailorValidators.parseMeasurementInches("/12"))
    assertNull(TailorValidators.parseMeasurementInches("1/2/3"))
    assertNull(TailorValidators.parseMeasurementInches("23/0"))
    assertNull(TailorValidators.parseMeasurementInches("abc"))

    // Compulsory field: blank allowed while editing, disallowed on save; out-of-range invalid immediately
    assertNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.KAMEEZ_LAMBAI,
        "",
        allowBlankRequired = true,
      ),
    )
    assertNotNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.KAMEEZ_LAMBAI,
        "",
        allowBlankRequired = false,
      ),
    )
    assertNotNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.KAMEEZ_LAMBAI,
        "10",
        allowBlankRequired = true,
      ),
    )
    assertNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.KAMEEZ_LAMBAI,
        "41 1/2",
        allowBlankRequired = false,
      ),
    )
    assertNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.BAZU,
        "23/12",
        allowBlankRequired = false,
      ),
    )
    assertNotNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.BAZU,
        "5/12",
        allowBlankRequired = true,
      ),
    )
    assertNotNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.BAZU,
        "23/",
        allowBlankRequired = true,
      ),
    )

    // Optional fields: Asan 10..60 and Shalwar Ghair 20..130
    assertNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.KAMAR,
        "",
        allowBlankRequired = false,
      ),
    )
    assertNotNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.KAMAR,
        "5",
        allowBlankRequired = true,
      ),
    )
    assertNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.GHERA_ASAN,
        "55",
        allowBlankRequired = false,
      ),
    )
    assertNotNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.GHERA_ASAN,
        "65",
        allowBlankRequired = true,
      ),
    )
    assertNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.SHALWAR_GHAIR,
        "120",
        allowBlankRequired = false,
      ),
    )
    assertNotNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.SHALWAR_GHAIR,
        "135",
        allowBlankRequired = true,
      ),
    )
    assertNull(
      TailorValidators.validateMeasurementFieldInput(
        MeasurementFieldKey.SHALWAR_GHAIR,
        "48/12",
        allowBlankRequired = false,
      ),
    )
    val rawMap = TailorValidators.buildRawValuesMap(
      listOf(
        MeasurementFieldKey.BAZU to "23/12",
        MeasurementFieldKey.KAMEEZ_LAMBAI to "41 1/2",
        MeasurementFieldKey.SHALWAR_GHAIR to "48.5/14",
      ),
    )
    assertEquals(mapOf("BAZU" to "23/12", "SHALWAR_GHAIR" to "48.5/14"), rawMap)
  }

  @Test
  fun `keypad helper inserts digits, fractions, dash, space, dot, and backspace cleanly`() {
    var tfv = androidx.compose.ui.text.input.TextFieldValue("")
    tfv = com.example.features.measurements.applyMeasurementKeyToTextFieldValue(
      tfv,
      com.example.features.measurements.MeasurementKeypadAction.Digit('4'),
    )
    tfv = com.example.features.measurements.applyMeasurementKeyToTextFieldValue(
      tfv,
      com.example.features.measurements.MeasurementKeypadAction.Digit('1'),
    )
    tfv = com.example.features.measurements.applyMeasurementKeyToTextFieldValue(
      tfv,
      com.example.features.measurements.MeasurementKeypadAction.Fraction("1/2"),
    )
    assertEquals("41 1/2", tfv.text)

    // Replacing fraction swaps 1/2 -> 3/4 cleanly
    tfv = com.example.features.measurements.applyMeasurementKeyToTextFieldValue(
      tfv,
      com.example.features.measurements.MeasurementKeypadAction.Fraction("3/4"),
    )
    assertEquals("41 3/4", tfv.text)

    // Backspace removes last char
    tfv = com.example.features.measurements.applyMeasurementKeyToTextFieldValue(
      tfv,
      com.example.features.measurements.MeasurementKeypadAction.Backspace,
    )
    assertEquals("41 3/", tfv.text)
  }

  @Test
  fun `customer validation requires name and validates phone only when provided`() {
    assertNotNull(TailorValidators.validateCustomer("A", "0300-1234567"))
    assertNull(TailorValidators.validateCustomer("Ali", ""))
    assertNull(TailorValidators.validateCustomer("Ali", "03001234567"))
    assertNotNull(TailorValidators.validateCustomer("Ali", "123"))
  }
}
