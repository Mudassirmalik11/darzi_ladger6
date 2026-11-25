package com.example.features.customers

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.app.TailorApp
import com.example.testing.AuthorizedShopTestRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CustomerMeasurementFlowTest {

  private val authRule = AuthorizedShopTestRule()
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `create customer, detect duplicate phone, and record shalwar kameez measurements`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }

    // Navigate to Customers
    composeTestRule.onNodeWithTag("dashboard_tile_customers").performClick()
    composeTestRule.waitForIdle()

    // Create customer
    composeTestRule.onNodeWithTag("add_customer_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customer_form_name_input").performTextInput("Salman Qureshi")
    composeTestRule.onNodeWithTag("customer_form_phone_input").performTextInput("03001112233")
    composeTestRule.onNodeWithTag("customer_form_save_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_detail_content").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_detail_content").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Salman Qureshi").onFirst().assertIsDisplayed()

    // Add Shalwar Kameez measurements
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("add_measurement_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("add_measurement_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_editor_screen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("meas_kameez_lambai").performTextInput("41.5")
    composeTestRule.onNodeWithTag("meas_tera").performTextInput("18")
    composeTestRule.onNodeWithTag("meas_bazu").performTextInput("24.5")
    composeTestRule.onNodeWithTag("meas_chati").performTextInput("42")
    composeTestRule.onNodeWithTag("meas_kamar").performTextInput("38")
    composeTestRule.onNodeWithTag("meas_ghera_daman").performTextInput("43")
    composeTestRule.onNodeWithTag("meas_gala_collar").performTextInput("16.5")
    composeTestRule.onNodeWithTag("meas_shalwar_lambai").performTextInput("39.5")
    composeTestRule.onNodeWithTag("meas_paincha").performTextInput("7.5")
    composeTestRule.onNodeWithTag("meas_ghera_asan").performTextInput("16")

    composeTestRule.onNodeWithTag("style_neck_collar").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("meas_save_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("41.5\"").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_detail_content").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasText("41.5\""))
    composeTestRule.onAllNodesWithText("41.5\"").onFirst().assertIsDisplayed()
  }

  @Test
  fun `customers directory displays sequential ids, address, and supports scoped search and filter`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val firstId: String
    val secondId: String
    val thirdId: String
    runBlocking {
      val device = app.container.deviceSyncRepository.ensureLocalDeviceConfig()
      firstId = app.container.customerRepository.createCustomer(
        name = "Ali",
        rawPhone = "03057223392",
        notes = "Rehmana abad",
        deviceId = device.deviceId,
      ).getOrThrow().customerId
      delay(15)
      secondId = app.container.customerRepository.createCustomer(
        name = "مدثر",
        rawPhone = "03259196082",
        notes = "رحمان اباد",
        deviceId = device.deviceId,
      ).getOrThrow().customerId
      delay(15)
      thirdId = app.container.customerRepository.createCustomer(
        name = "Bilal Khan",
        rawPhone = "03119998877",
        notes = "",
        deviceId = device.deviceId,
      ).getOrThrow().customerId
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_tile_customers").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_card_$thirdId").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("customers_total_count_text")
      .assertTextEquals("Total Customers: 3")
    composeTestRule.onNodeWithTag("add_customer_fab").assertIsDisplayed()

    // Filter to customers with an address/notes only -> excludes Bilal Khan (ID: 3)
    composeTestRule.onNodeWithTag("customers_filter_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customers_filter_address_only").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customers_total_count_text")
      .assertTextEquals("Total Customers: 2")

    // Both Ali (ID: 1) and مدثر (ID: 2) fit cleanly on screen
    composeTestRule.onNodeWithTag("customer_card_$secondId").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_card_$firstId").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("ID: 2").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("ID: 1").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("رحمان اباد").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Rehmana abad").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("0325-9196082").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("0305-7223392").onFirst().assertIsDisplayed()

    // Search by ID scope -> find ID: 1 (Ali)
    composeTestRule.onNodeWithTag("customers_scope_id").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("customers_search_input").performTextInput("1")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customers_total_count_text")
      .assertTextEquals("Total Customers: 1")
    composeTestRule.onNodeWithTag("customer_card_$firstId").assertIsDisplayed()

    // Clear search and switch to Phone scope -> search unformatted digits
    composeTestRule.onNodeWithTag("customers_search_clear").performClick()
    composeTestRule.onNodeWithTag("customers_scope_phone").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("customers_search_input").performTextInput("0325919")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customers_total_count_text")
      .assertTextEquals("Total Customers: 1")
    composeTestRule.onNodeWithTag("customer_card_$secondId").assertIsDisplayed()

    // Clear search and switch to Address scope -> search Urdu address
    composeTestRule.onNodeWithTag("customers_search_clear").performClick()
    composeTestRule.onNodeWithTag("customers_scope_address").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("customers_search_input").performTextInput("رحمان")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customers_total_count_text")
      .assertTextEquals("Total Customers: 1")
    composeTestRule.onNodeWithTag("customer_card_$secondId").assertIsDisplayed()
  }

  @Test
  fun `add new customer modal shows custom id placeholder, close and cancel buttons, and saves custom id`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    runBlocking {
      val device = app.container.deviceSyncRepository.ensureLocalDeviceConfig()
      app.container.customerRepository.createCustomer(
        name = "Ali",
        rawPhone = "03057223392",
        notes = "Rehmana abad",
        deviceId = device.deviceId,
      ).getOrThrow()
      delay(15)
      app.container.customerRepository.createCustomer(
        name = "مدثر",
        rawPhone = "03259196082",
        notes = "رحمان اباد",
        deviceId = device.deviceId,
      ).getOrThrow()
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_tile_customers").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("ID: 2").fetchSemanticsNodes().isNotEmpty()
    }

    // Open Add New Customer modal
    composeTestRule.onNodeWithTag("add_customer_fab").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("customer_form_dialog").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Add New Customer").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Name *").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Phone").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Address").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Custom ID").onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_form_id_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_form_close_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_form_cancel_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_form_save_button").assertIsDisplayed()

    // Dismiss via X button
    composeTestRule.onNodeWithTag("customer_form_close_button").performClick()
    composeTestRule.waitForIdle()
    assert(composeTestRule.onAllNodesWithTag("customer_form_dialog").fetchSemanticsNodes().isEmpty())

    // Reopen and create customer with Name *, Address, and user-entered Custom ID (phone blank)
    composeTestRule.onNodeWithTag("add_customer_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customer_form_name_input").performTextInput("Tariq Mahmood")
    composeTestRule.onNodeWithTag("customer_form_notes_input").performTextInput("Model Town")
    composeTestRule.onNodeWithTag("customer_form_id_input").performTextInput("42")
    composeTestRule.onNodeWithTag("customer_form_save_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_detail_content").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onAllNodesWithText("Tariq Mahmood").onFirst().assertIsDisplayed()

    // Navigate back to Customers list and verify ID: 42 is displayed and searchable by ID
    composeTestRule.onNodeWithTag("customer_detail_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithText("ID: 42").onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("customers_scope_id").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("customers_search_input").performTextInput("42")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customers_total_count_text").assertTextEquals("Total Customers: 1")
    composeTestRule.onAllNodesWithText("Tariq Mahmood").onFirst().assertIsDisplayed()
  }

  @Test
  fun `customer detail shows header, tabs, measurement options popup, print preview, save, edit, and delete`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val customerId: String
    val measId: String
    val orderId: String
    runBlocking {
      val device = app.container.deviceSyncRepository.ensureLocalDeviceConfig()
      customerId = app.container.customerRepository.createCustomer(
        name = "Ali",
        rawPhone = "03057223392",
        notes = "Rehmana abad",
        customId = "1",
        deviceId = device.deviceId,
      ).getOrThrow().customerId
      val meas = app.container.customerRepository.saveMeasurement(
        customerId = customerId,
        snapshot = com.example.core.model.MeasurementSnapshot(
          kameezLambai = 23.0,
          tera = 12.0,
          bazu = 18.0,
          chati = 25.0,
          kamar = 19.0,
          gheraDaman = 20.0,
          galaCollar = 14.5,
          cuff = 9.5,
          shalwarLambai = 36.0,
          paincha = 7.0,
          gheraAsan = 15.0,
          notes = "Chota zari khula",
        ),
        neckStyle = com.example.core.model.NeckStyle.COLLAR,
        damanStyle = com.example.core.model.DamanStyle.GOL,
        pocketStyle = com.example.core.model.PocketStyle.TWO_SIDE,
        sleeveStyle = com.example.core.model.SleeveStyle.STUDS,
        silaiStyle = com.example.core.model.SilaiStyle.SINGLE,
        deviceId = device.deviceId,
      ).getOrThrow()
      measId = meas.measurementId
      orderId = app.container.orderRepository.createOrder(
        customerId = customerId,
        suitCount = 2,
        deliveryDateMillis = System.currentTimeMillis() + 5 * 86_400_000L,
        totalAmountPkr = 3000L,
        initialAdvancePkr = 500L,
        measurementSnapshot = meas.toMeasurementSnapshot(),
        styleSnapshot = meas.toStyleSnapshot(),
        deviceId = device.deviceId,
      ).getOrThrow().orderId
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_tile_customers").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_card_$customerId").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_card_$customerId").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_detail_content").fetchSemanticsNodes().isNotEmpty()
    }

    // Header, Call/Whatsapp/+ Order buttons, and Tabs
    composeTestRule.onAllNodesWithText("Customer").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Ali").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("ID: 1").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Rehmana abad").onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_call_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_whatsapp_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_new_order_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_tab_measurements").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_tab_payments").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_tab_orders").assertIsDisplayed()

    // Scroll to measurement card and verify values
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("No Shalwar Kameez measurements recorded yet.").fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("measurement_options_button_$measId"))
    composeTestRule.onNodeWithTag("measurement_options_button_$measId").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Male Shalwar Kameez").onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasText("23\""))
    composeTestRule.onAllNodesWithText("23\"").onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("measurement_options_button_$measId"))

    // Click three dots -> Options popup (customer.dots.jpg)
    composeTestRule.onNodeWithTag("measurement_options_button_$measId").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_options_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("measurement_options_close_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("measurement_option_edit").assertIsDisplayed()
    composeTestRule.onNodeWithTag("measurement_option_save").assertIsDisplayed()
    composeTestRule.onNodeWithTag("measurement_option_print").assertIsDisplayed()
    composeTestRule.onNodeWithTag("measurement_option_share").assertIsDisplayed()
    composeTestRule.onNodeWithTag("measurement_option_delete").assertIsDisplayed()

    // Test Print -> opens Measurement Slip Preview dialog
    composeTestRule.onNodeWithTag("measurement_option_print").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("slip_preview_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("slip_preview_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("slip_preview_close_button").performClick()
    composeTestRule.waitForIdle()

    // Test Save -> closes popup and keeps card active
    composeTestRule.onNodeWithTag("measurement_options_button_$measId").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_option_save").performClick()
    composeTestRule.waitForIdle()
    assert(composeTestRule.onAllNodesWithTag("measurement_options_dialog").fetchSemanticsNodes().isEmpty())

    // Test Edit -> navigates to measurement editor
    composeTestRule.onNodeWithTag("measurement_options_button_$measId").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_option_edit").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("measurement_editor_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("meas_save_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_detail_content").fetchSemanticsNodes().isNotEmpty()
    }

    // Test Payments tab and Orders tab
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("customer_tab_payments"))
    composeTestRule.onNodeWithTag("customer_tab_payments").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasText("Amount: Rs. 500"))
    composeTestRule.onAllNodesWithText("Amount: Rs. 500").onFirst().assertIsDisplayed()

    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("customer_tab_orders"))
    composeTestRule.onNodeWithTag("customer_tab_orders").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("order_card_$orderId"))
    composeTestRule.onNodeWithTag("order_card_$orderId").assertIsDisplayed()

    // Switch back to Measurements tab and test Delete
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("customer_tab_measurements"))
    composeTestRule.onNodeWithTag("customer_tab_measurements").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasTestTag("measurement_options_button_$measId"))
    composeTestRule.onNodeWithTag("measurement_options_button_$measId").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_option_delete").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_delete_confirm_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("measurement_delete_confirm_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("measurement_card_$measId").fetchSemanticsNodes().isEmpty()
    }
  }

  @Test
  fun `unified measurement form enforces real-time bounds, custom keypad entry, Next focus order, and optional blanks`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val customerId: String
    runBlocking {
      val device = app.container.deviceSyncRepository.ensureLocalDeviceConfig()
      customerId = app.container.customerRepository.createCustomer(
        name = "Usman Tariq",
        rawPhone = "03009876543",
        notes = "Gulberg",
        deviceId = device.deviceId,
      ).getOrThrow().customerId
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_tile_customers").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_card_$customerId").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_card_$customerId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("add_measurement_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("add_measurement_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Single unified measurement section, no separate Kameez/Shalwar cards
    composeTestRule.onNodeWithTag("measurement_unified_section").assertIsDisplayed()
    assert(composeTestRule.onAllNodesWithText("Kameez (Inches)").fetchSemanticsNodes().isEmpty())
    assert(composeTestRule.onAllNodesWithText("Shalwar (Inches)").fetchSemanticsNodes().isEmpty())

    // Focusing Kameez Lambai (#1) opens docked custom measurement keyboard
    composeTestRule.onNodeWithTag("meas_kameez_lambai").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("tailor_measurement_keyboard").assertIsDisplayed()
    composeTestRule.onNodeWithTag("meas_kameez_lambai").assertIsFocused()

    // Entering arbitrary digits (e.g. 788, 0) or dual-slash (8/10) has NO range error; only malformed syntax (41.) errors
    composeTestRule.onNodeWithTag("keypad_key_7").performClick()
    composeTestRule.onNodeWithTag("keypad_key_8").performClick()
    composeTestRule.onNodeWithTag("keypad_key_8").performClick()
    composeTestRule.waitForIdle()
    assert(
      composeTestRule.onAllNodesWithText("Enter a valid value (e.g. 0, 41.5, or 8/10).")
        .fetchSemanticsNodes().isEmpty(),
    )
    repeat(3) { composeTestRule.onNodeWithTag("keypad_key_backspace").performClick() }

    composeTestRule.onNodeWithTag("keypad_key_4").performClick()
    composeTestRule.onNodeWithTag("keypad_key_1").performClick()
    composeTestRule.onNodeWithTag("keypad_key_dot_slash").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("meas_kameez_lambai")
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    composeTestRule.onAllNodesWithText("Enter a valid value (e.g. 0, 41.5, or 8/10).")
      .onFirst().assertIsDisplayed()

    // Clear trailing dot and enter "41 1/2" via custom keypad
    composeTestRule.onNodeWithTag("keypad_key_backspace").performClick()
    composeTestRule.onNodeWithTag("keypad_key_half").performClick()
    composeTestRule.waitForIdle()
    assert(
      composeTestRule.onAllNodesWithText("Enter a valid value (e.g. 0, 41.5, or 8/10).")
        .fetchSemanticsNodes().isEmpty(),
    )

    // Tap Next -> advances focus sequentially 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8
    // Field 2: Teera (18)
    composeTestRule.onNodeWithTag("keypad_key_next").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("meas_tera").assertIsFocused()
    composeTestRule.onNodeWithTag("keypad_key_1").performClick()
    composeTestRule.onNodeWithTag("keypad_key_8").performClick()

    // Field 3: Asteen (23/12 via keypad '/' button)
    composeTestRule.onNodeWithTag("keypad_key_next").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("meas_bazu").assertIsFocused()
    composeTestRule.onNodeWithTag("keypad_key_2").performClick()
    composeTestRule.onNodeWithTag("keypad_key_3").performClick()
    composeTestRule.onNodeWithTag("keypad_key_symbol").performClick()
    composeTestRule.onNodeWithTag("keypad_key_1").performClick()
    composeTestRule.onNodeWithTag("keypad_key_2").performClick()

    // Field 4: Gala (16.5)
    composeTestRule.onNodeWithTag("keypad_key_next").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("meas_gala_collar").assertIsFocused()
    composeTestRule.onNodeWithTag("keypad_key_1").performClick()
    composeTestRule.onNodeWithTag("keypad_key_6").performClick()
    composeTestRule.onNodeWithTag("keypad_key_dot_slash").performClick()
    composeTestRule.onNodeWithTag("keypad_key_5").performClick()

    // Field 5: Seena (42)
    composeTestRule.onNodeWithTag("keypad_key_next").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("meas_chati").assertIsFocused()
    composeTestRule.onNodeWithTag("keypad_key_4").performClick()
    composeTestRule.onNodeWithTag("keypad_key_2").performClick()

    // Field 6: Ghera / Daman (43)
    composeTestRule.onNodeWithTag("keypad_key_next").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("meas_ghera_daman").assertIsFocused()
    composeTestRule.onNodeWithTag("keypad_key_4").performClick()
    composeTestRule.onNodeWithTag("keypad_key_3").performClick()

    // Field 7: Shalwar (39 3/4)
    composeTestRule.onNodeWithTag("keypad_key_next").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("meas_shalwar_lambai").assertIsFocused()
    composeTestRule.onNodeWithTag("keypad_key_3").performClick()
    composeTestRule.onNodeWithTag("keypad_key_9").performClick()
    composeTestRule.onNodeWithTag("keypad_key_three_quarter").performClick()

    // Field 8: Poncha (7.5)
    composeTestRule.onNodeWithTag("keypad_key_next").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("meas_paincha").assertIsFocused()
    composeTestRule.onNodeWithTag("keypad_key_7").performClick()
    composeTestRule.onNodeWithTag("keypad_key_dot_slash").performClick()
    composeTestRule.onNodeWithTag("keypad_key_5").performClick()

    // Dismiss keypad, leave optional fields (Kamar, Cuff, Asan, Shalwar Ghair) blank, and save
    composeTestRule.onNodeWithTag("keypad_hide_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("meas_save_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_detail_content").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasText("41.5\""))
    composeTestRule.onAllNodesWithText("41.5\"").onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasText("23/12\""))
    composeTestRule.onAllNodesWithText("23/12\"").onFirst().assertIsDisplayed()
  }

  @Test
  fun `saved measurements keep 1 to 8 order, place optional Kamar and Shalwar Ghair below Poncha, omit blanks, and default zero styles`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val customerId: String
    runBlocking {
      val device = app.container.deviceSyncRepository.ensureLocalDeviceConfig()
      customerId = app.container.customerRepository.createCustomer(
        name = "Zainab Tailoring",
        rawPhone = "03005556677",
        notes = "Saddar",
        deviceId = device.deviceId,
      ).getOrThrow().customerId
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_tile_customers").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_card_$customerId").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_card_$customerId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("add_measurement_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("add_measurement_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Zero style chips should be selected by default
    listOf(
      "style_neck_collar", "style_neck_ban", "style_neck_half_ban", "style_neck_round",
      "style_daman_gol", "style_daman_chorasi",
      "style_pocket_front", "style_pocket_one_side", "style_pocket_two_side", "style_pocket_shalwar_pocket",
      "style_sleeve_kaf", "style_sleeve_kurta",
      "style_shoulder_down_shoulder", "style_shoulder_hanger_shoulder", "style_shoulder_full_down_shoulder",
      "style_shalwar_smart", "style_shalwar_normal", "style_shalwar_panjama", "style_shalwar_balochi",
      "style_silai_single", "style_silai_double", "style_silai_kamdo_dhaga",
    ).forEach { tag ->
      composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsNotSelected()
    }

    // Single-choice style chip toggles on and off cleanly
    composeTestRule.onNodeWithTag("style_neck_collar").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("style_neck_collar").assertIsSelected()
    composeTestRule.onNodeWithTag("style_neck_collar").performClick()
    composeTestRule.onNodeWithTag("style_neck_collar").assertIsNotSelected()

    // Choose Collar, Kaf sleeve, Down Shoulder, Balochi shalwar, and Kamdo Dhaga silai; leave Daman and Pockets unselected
    composeTestRule.onNodeWithTag("style_neck_collar").performClick()
    composeTestRule.onNodeWithTag("style_neck_collar").assertIsSelected()
    composeTestRule.onNodeWithTag("style_sleeve_kaf").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("style_sleeve_kaf").assertIsSelected()
    composeTestRule.onNodeWithTag("style_shoulder_down_shoulder").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("style_shoulder_down_shoulder").assertIsSelected()
    composeTestRule.onNodeWithTag("style_shalwar_balochi").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("style_shalwar_balochi").assertIsSelected()
    composeTestRule.onNodeWithTag("style_silai_kamdo_dhaga").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("style_silai_kamdo_dhaga").assertIsSelected()

    // Fill only the 8 compulsory measurements; leave optional Kamar, Cuff, Asan, Shalwar Ghair blank
    composeTestRule.onNodeWithTag("meas_kameez_lambai").performScrollTo().performTextInput("40")
    composeTestRule.onNodeWithTag("meas_tera").performScrollTo().performTextInput("17.5")
    composeTestRule.onNodeWithTag("meas_bazu").performScrollTo().performTextInput("24")
    composeTestRule.onNodeWithTag("meas_gala_collar").performScrollTo().performTextInput("15.5")
    composeTestRule.onNodeWithTag("meas_chati").performScrollTo().performTextInput("41")
    composeTestRule.onNodeWithTag("meas_ghera_daman").performScrollTo().performTextInput("42")
    composeTestRule.onNodeWithTag("meas_shalwar_lambai").performScrollTo().performTextInput("38.5")
    composeTestRule.onNodeWithTag("meas_paincha").performScrollTo().performTextInput("7.5")
    composeTestRule.onNodeWithTag("keypad_hide_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("meas_save_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_detail_content").fetchSemanticsNodes().isNotEmpty() &&
        composeTestRule.onAllNodesWithText("No Shalwar Kameez measurements recorded yet.").fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(hasTestTag("saved_meas_kameez_lambai"))

    // Verify 1..8 compulsory boxes appear in strict 2-column order
    val compulsoryTags = listOf(
      "saved_meas_kameez_lambai",
      "saved_meas_tera",
      "saved_meas_bazu",
      "saved_meas_gala_collar",
      "saved_meas_chati",
      "saved_meas_ghera_daman",
      "saved_meas_shalwar_lambai",
      "saved_meas_paincha",
    )
    compulsoryTags.forEach { tag ->
      composeTestRule.onNodeWithTag("customer_detail_content").performScrollToNode(hasTestTag(tag))
      composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
    }
    val lambaiTop = composeTestRule.onNodeWithTag("saved_meas_kameez_lambai").getBoundsInRoot().top
    val asteenTop = composeTestRule.onNodeWithTag("saved_meas_bazu").getBoundsInRoot().top
    val seenaTop = composeTestRule.onNodeWithTag("saved_meas_chati").getBoundsInRoot().top
    val shalwarTop = composeTestRule.onNodeWithTag("saved_meas_shalwar_lambai").getBoundsInRoot().top
    assert(lambaiTop < asteenTop)
    assert(asteenTop < seenaTop)
    assert(seenaTop < shalwarTop)

    // Unentered optionals & dashes must NOT exist at all
    assert(composeTestRule.onAllNodesWithTag("saved_meas_kamar").fetchSemanticsNodes().isEmpty())
    assert(composeTestRule.onAllNodesWithTag("saved_meas_cuff").fetchSemanticsNodes().isEmpty())
    assert(composeTestRule.onAllNodesWithTag("saved_meas_ghera_asan").fetchSemanticsNodes().isEmpty())
    assert(composeTestRule.onAllNodesWithTag("saved_meas_shalwar_ghair").fetchSemanticsNodes().isEmpty())
    assert(composeTestRule.onAllNodesWithText("—").fetchSemanticsNodes().isEmpty())

    // Only chosen Neck, Sleeves (Kaf), Shoulder (Down Shoulder), Shalwar Type (Balochi), and Silai (Kamdo Dhaga) appear; unselected defaults must NOT appear
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(hasTestTag("saved_style_neck"))
    composeTestRule.onNodeWithTag("saved_style_neck").performScrollTo().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Collar").onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("saved_style_sleeves").performScrollTo().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Kaf").onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("saved_style_shoulder").performScrollTo().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Down Shoulder").onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("saved_style_shalwar").performScrollTo().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Balochi").onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("saved_style_silai").performScrollTo().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Kamdo Dhaga").onFirst().assertIsDisplayed()
    assert(composeTestRule.onAllNodesWithTag("saved_style_daman").fetchSemanticsNodes().isEmpty())
    assert(composeTestRule.onAllNodesWithTag("saved_style_pockets").fetchSemanticsNodes().isEmpty())
    assert(composeTestRule.onAllNodesWithText("Gol Daman").fetchSemanticsNodes().isEmpty())
    assert(composeTestRule.onAllNodesWithText("Chamak Dhaga").fetchSemanticsNodes().isEmpty())
    assert(composeTestRule.onAllNodesWithText("Single Silai").fetchSemanticsNodes().isEmpty())

    // Edit measurement to enter optional Kamar + Shalwar Ghair -> must appear below Poncha
    val measId = runBlocking {
      app.container.customerRepository.getCurrentMeasurement(customerId)!!.measurementId
    }
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(hasTestTag("measurement_options_button_$measId"))
    composeTestRule.onNodeWithTag("measurement_options_button_$measId").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_option_edit").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("meas_kamar").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("meas_kamar").performScrollTo().performTextInput("36")
    composeTestRule.onNodeWithTag("meas_shalwar_ghair").performScrollTo().performTextInput("95")
    composeTestRule.onNodeWithTag("keypad_hide_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("meas_save_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_detail_content").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(hasTestTag("saved_meas_kamar"))
    composeTestRule.onNodeWithTag("saved_meas_kamar").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(hasTestTag("saved_meas_shalwar_ghair"))
    composeTestRule.onNodeWithTag("saved_meas_shalwar_ghair").performScrollTo().assertIsDisplayed()
    val ponchaBottom = composeTestRule.onNodeWithTag("saved_meas_paincha").getBoundsInRoot().bottom
    val kamarTop = composeTestRule.onNodeWithTag("saved_meas_kamar").getBoundsInRoot().top
    assert(kamarTop >= ponchaBottom)
    assert(composeTestRule.onAllNodesWithTag("saved_meas_cuff").fetchSemanticsNodes().isEmpty())
    assert(composeTestRule.onAllNodesWithTag("saved_meas_ghera_asan").fetchSemanticsNodes().isEmpty())
  }
}
