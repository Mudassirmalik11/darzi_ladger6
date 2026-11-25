package com.example.data.sync

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.app.TailorApp
import com.example.core.model.DamanStyle
import com.example.core.model.DeviceRegistrationStatus
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentMethod
import com.example.core.model.PaymentStatus
import com.example.core.model.PocketStyle
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.StyleSnapshot
import com.example.core.model.SyncEntityType
import com.example.core.model.SyncOperation
import com.example.core.model.SyncState
import com.example.data.local.database.DarziDatabase
import com.example.data.local.entities.OrderSyncLogEntity
import com.example.data.local.entities.OrderSyncLogOperation
import com.example.data.local.entities.OrderSyncLogStatus
import com.example.data.local.entities.SyncMetadataEntity
import com.example.data.local.entities.SyncOutboxEntity
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.OrderRepository
import com.example.data.local.repository.ShopRepository
import com.example.testing.AuthorizedShopTestRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OrderSyncLoggingAndPropagationTest {

  private lateinit var context: Context
  private lateinit var db: DarziDatabase
  private lateinit var shopRepo: ShopRepository
  private lateinit var customerRepo: CustomerRepository
  private lateinit var orderRepo: OrderRepository
  private lateinit var deviceSyncRepo: DeviceSyncRepository

  private val sampleMeasurements = MeasurementSnapshot(
    kameezLambai = 41.0,
    tera = 18.0,
    bazu = 24.0,
    chati = 42.0,
    kamar = 38.0,
    gheraDaman = 43.0,
    galaCollar = 16.0,
    cuff = 9.0,
    shalwarLambai = 40.0,
    paincha = 7.5,
    gheraAsan = 16.0,
  )

  private val sampleStyle = StyleSnapshot(
    neckStyle = NeckStyle.BAN,
    damanStyle = DamanStyle.GOL,
    pocketStyle = PocketStyle.TWO_SIDE,
    sleeveStyle = SleeveStyle.KAF,
    silaiStyle = SilaiStyle.DOUBLE,
  )

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, DarziDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    shopRepo = ShopRepository(db)
    customerRepo = CustomerRepository(db, shopRepo)
    orderRepo = OrderRepository(db, shopRepo)
    deviceSyncRepo = DeviceSyncRepository(db)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `order payments, status updates, and soft deletes queue sync events and log full audit trail`() = runBlocking {
    val cust = customerRepo.createCustomer(
      name = "Usman Farooq",
      rawPhone = "0300-1234567",
      deviceId = "phone-a",
    ).getOrThrow()

    deviceSyncRepo.addOrderNumberReservation(
      deviceId = "phone-a",
      startNumber = 1101,
      endNumber = 1150,
    )

    val order1 = orderRepo.createOrder(
      customerId = cust.customerId,
      suitCount = 2,
      deliveryDateMillis = 1_800_000_000_000L,
      totalAmountPkr = 4000L,
      initialAdvancePkr = 1000L,
      measurementSnapshot = sampleMeasurements,
      styleSnapshot = sampleStyle,
      deviceId = "phone-a",
      nowMillis = 10_000L,
    ).getOrThrow()

    // Mark first order PAID via full settlement
    val settled = orderRepo.recordRemainingBalancePayment(
      orderId = order1.orderId,
      paymentMethod = PaymentMethod.CASH,
      deviceId = "phone-a",
      nowMillis = 11_000L,
    ).getOrThrow()
    assertEquals(PaymentStatus.PAID, settled.paymentStatus)
    assertEquals(0L, settled.remainingAmountPkr)

    // Transition status PENDING -> READY -> CANCELLED and soft-delete 3 orders across different statuses
    val order2 = orderRepo.createOrder(
      customerId = cust.customerId,
      suitCount = 1,
      deliveryDateMillis = 1_800_000_000_000L,
      totalAmountPkr = 2000L,
      initialAdvancePkr = 0L,
      measurementSnapshot = sampleMeasurements,
      styleSnapshot = sampleStyle,
      deviceId = "phone-a",
      nowMillis = 12_000L,
    ).getOrThrow()
    orderRepo.updateOrderStatus(order2.orderId, OrderStatus.READY, "phone-a", nowMillis = 12_500L).getOrThrow()

    val order3 = orderRepo.createOrder(
      customerId = cust.customerId,
      suitCount = 1,
      deliveryDateMillis = 1_800_000_000_000L,
      totalAmountPkr = 2000L,
      initialAdvancePkr = 0L,
      measurementSnapshot = sampleMeasurements,
      styleSnapshot = sampleStyle,
      deviceId = "phone-a",
      nowMillis = 13_000L,
    ).getOrThrow()
    orderRepo.updateOrderStatus(order3.orderId, OrderStatus.CANCELLED, "phone-a", nowMillis = 13_500L).getOrThrow()

    orderRepo.softDeleteOrder(order1.orderId, "phone-a", nowMillis = 14_000L).getOrThrow()
    orderRepo.softDeleteOrder(order2.orderId, "phone-a", nowMillis = 14_100L).getOrThrow()
    orderRepo.softDeleteOrder(order3.orderId, "phone-a", nowMillis = 14_200L).getOrThrow()

    // Verify all 3 orders are excluded from active queries
    val activeOrders = orderRepo.observeAllOrders().first()
    assertTrue(activeOrders.isEmpty())

    // Verify orderPaymentUpdateMap & orderDetailsUpdateMap shapes
    val payMap = SyncMappers.orderPaymentUpdateMap(settled)
    assertEquals("PAID", payMap["paymentStatus"])
    assertEquals(0L, payMap["remainingAmountPkr"])
    assertEquals(4000L, payMap["paidAmountPkr"])

    val cancelledDeleted = db.orderDao().getById(order3.orderId)!!
    assertTrue(cancelledDeleted.isDeleted)
    assertEquals(OrderStatus.CANCELLED, cancelledDeleted.orderStatus)
    val delMap = SyncMappers.orderDetailsUpdateMap(cancelledDeleted, includeOrderNumber = false)
    assertEquals(true, delMap["deleted"])
    assertEquals("CANCELLED", delMap["orderStatus"])

    // Verify OrderSyncLog recorded QUEUED logs for CREATE, PAYMENT, STATUS, DELETE
    val logsForOrder1 = db.orderSyncLogDao().getLogsForOrder(order1.orderId)
    assertEquals(
      listOf(OrderSyncLogOperation.DELETE, OrderSyncLogOperation.PAYMENT, OrderSyncLogOperation.CREATE),
      logsForOrder1.map { it.operation },
    )
    assertTrue(logsForOrder1.all { it.status == OrderSyncLogStatus.QUEUED })
    assertEquals(listOf(OrderSyncLogOperation.DELETE, OrderSyncLogOperation.STATUS, OrderSyncLogOperation.CREATE),
      db.orderSyncLogDao().getLogsForOrder(order3.orderId).map { it.operation })
  }

  @Test
  fun `remote order PAID settlement and 3 soft-deletions propagate cleanly and log REMOTE_APPLIED or SKIPPED`() = runBlocking {
    val applier = SyncRemoteApplier(db)
    val cust = customerRepo.createCustomer(
      name = "Salman Raza",
      rawPhone = "0321-5550001",
      deviceId = "phone-b",
      nowMillis = 5_000L,
    ).getOrThrow()

    // Seed 3 active orders initially synced from Phone A (v1)
    val baseOrders = (1..3).map { idx ->
      com.example.data.local.entities.OrderEntity(
        orderId = "remote-order-$idx",
        shopId = cust.shopId,
        customerId = cust.customerId,
        orderNumber = 1200 + idx,
        suitCount = 1,
        deliveryDate = 1_800_000_000_000L,
        totalAmountPkr = 3000L,
        paidAmountPkr = 0L,
        remainingAmountPkr = 3000L,
        orderStatus = if (idx == 3) OrderStatus.CANCELLED else OrderStatus.PENDING,
        paymentStatus = PaymentStatus.UNPAID,
        measurementSnapshotJson = com.example.core.model.SnapshotJson.encodeMeasurement(sampleMeasurements),
        styleSnapshotJson = com.example.core.model.SnapshotJson.encodeStyle(sampleStyle),
        notes = "",
        createdAt = 6_000L + idx,
        updatedAt = 6_000L + idx,
        version = 1L,
        lastModifiedByDevice = "phone-a",
      )
    }
    applier.applyRemoteOrders(baseOrders)
    assertEquals(3, orderRepo.observeAllOrders().first().size)

    // Remote device marks order 1 PAID (v2)
    val paidOrder1 = baseOrders[0].copy(
      paidAmountPkr = 3000L,
      remainingAmountPkr = 0L,
      paymentStatus = PaymentStatus.PAID,
      version = 2L,
      updatedAt = 8_000L,
    )
    applier.applyRemoteOrders(listOf(paidOrder1))
    val localAfterPaid = db.orderDao().getById("remote-order-1")!!
    assertEquals(PaymentStatus.PAID, localAfterPaid.paymentStatus)
    assertEquals(0L, localAfterPaid.remainingAmountPkr)

    val paidLogs = db.orderSyncLogDao().getLogsForOrder("remote-order-1")
    assertTrue(paidLogs.any { it.status == OrderSyncLogStatus.REMOTE_APPLIED && it.summary.contains("PAID") })

    // Local device creates a pending outbox edit on order 2; remote v2 update should be logged SKIPPED (held)
    db.syncOutboxDao().enqueue(
      SyncOutboxEntity(
        eventId = "local-pending-2",
        entityType = SyncEntityType.ORDER,
        entityId = "remote-order-2",
        operation = SyncOperation.UPDATE,
        payloadJson = "{}",
        createdAt = 8_500L,
      ),
    )
    val remoteOrder2Updated = baseOrders[1].copy(
      orderStatus = OrderStatus.READY,
      version = 2L,
      updatedAt = 9_000L,
    )
    applier.applyRemoteOrders(listOf(remoteOrder2Updated))
    assertEquals(OrderStatus.PENDING, db.orderDao().getById("remote-order-2")!!.orderStatus)
    assertTrue(
      db.orderSyncLogDao().getLogsForOrder("remote-order-2")
        .any { it.status == OrderSyncLogStatus.SKIPPED && it.summary.contains("outbox") },
    )

    // Clear active outbox for order 2, then propagate soft-delete for all 3 orders from Phone A
    db.syncOutboxDao().markSyncedBatch(listOf("local-pending-2"))
    val deletedRemoteOrders = listOf(
      paidOrder1.copy(isDeleted = true, version = 3L, updatedAt = 10_000L),
      remoteOrder2Updated.copy(isDeleted = true, version = 3L, updatedAt = 10_100L),
      baseOrders[2].copy(isDeleted = true, version = 2L, updatedAt = 10_200L),
    )
    val appliedDeletes = applier.applyRemoteOrders(deletedRemoteOrders)
    assertEquals(3, appliedDeletes)
    assertTrue(orderRepo.observeAllOrders().first().isEmpty())
    assertTrue(
      db.orderSyncLogDao().getLogsForOrder("remote-order-3")
        .any { it.status == OrderSyncLogStatus.REMOTE_APPLIED && it.summary.contains("deleted remotely") },
    )
  }

  @Test
  fun `MIGRATION_6_7 creates order_sync_logs table and indexes cleanly`() {
    val dbName = "migration_6_7_test.db"
    context.deleteDatabase(dbName)
    val factory = FrameworkSQLiteOpenHelperFactory()
    val helper = factory.create(
      SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(dbName)
        .callback(
          object : SupportSQLiteOpenHelper.Callback(6) {
            override fun onCreate(db: SupportSQLiteDatabase) {
              db.execSQL("CREATE TABLE IF NOT EXISTS `orders` (`orderId` TEXT NOT NULL PRIMARY KEY)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
          },
        )
        .build(),
    )
    val sqliteDb = helper.writableDatabase
    DarziDatabase.MIGRATION_6_7.migrate(sqliteDb)

    sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='order_sync_logs'").use { cursor ->
      assertTrue(cursor.moveToFirst())
    }
    val indexes = mutableSetOf<String>()
    sqliteDb.query("PRAGMA index_list(`order_sync_logs`)").use { cursor ->
      val nameIdx = cursor.getColumnIndex("name")
      while (cursor.moveToNext()) {
        if (nameIdx >= 0) indexes.add(cursor.getString(nameIdx))
      }
    }
    assertTrue("index_order_sync_logs_orderId" in indexes)
    assertTrue("index_order_sync_logs_timestamp" in indexes)
    assertTrue("index_order_sync_logs_status" in indexes)
    sqliteDb.close()
    helper.close()
    context.deleteDatabase(dbName)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OrderSyncStatusUiRobolectricTest {

  private val authRule = AuthorizedShopTestRule(initialSignedIn = true, authorizeLocalDevice = true, initialOnline = false)
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `topbar surfaces Sync Error when outbox fails and Sync Center displays and clears Order Sync Logs`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val failedLogId = "log-failed-1"
    val syncedLogId = "log-synced-2"

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("topbar_sync_pill").fetchSemanticsNodes().isNotEmpty()
    }
    // Cancel active background sync listeners before simulating online state so Robolectric does not hang on Firestore network timeouts.
    composeTestRule.runOnUiThread {
      app.container.syncEngine.stopRealtimeSync()
    }

    runBlocking {
      val device = app.container.deviceSyncRepository.ensureLocalDeviceConfig()
      app.container.database.deviceSyncDao().upsertSyncMetadata(
        SyncMetadataEntity(
          deviceId = device.deviceId,
          syncStatus = SyncState.ERROR,
          lastSyncAttemptAt = System.currentTimeMillis(),
          lastError = "[PERMISSION_DENIED] Missing permissions on order payment update",
        ),
      )
      app.container.database.syncOutboxDao().enqueue(
        SyncOutboxEntity(
          eventId = "failed-order-ev-1",
          entityType = SyncEntityType.ORDER,
          entityId = "order-failed-1",
          operation = SyncOperation.UPDATE,
          payloadJson = "{}",
          createdAt = 1_000L,
        ),
      )
      app.container.database.syncOutboxDao().markRetry(
        eventId = "failed-order-ev-1",
        nextRetryAt = Long.MAX_VALUE / 2,
        error = "[PERMISSION_DENIED] Missing permissions on order payment update",
      )
      app.container.database.orderSyncLogDao().insertAll(
        listOf(
          OrderSyncLogEntity(
            logId = failedLogId,
            orderId = "order-failed-1",
            orderNumber = 1101,
            operation = OrderSyncLogOperation.PAYMENT,
            status = OrderSyncLogStatus.FAILED,
            summary = "Cloud push failed (retry #1) for PENDING/PAID",
            errorMessage = "[PERMISSION_DENIED] Missing permissions on order payment update",
            deviceId = device.deviceId,
            orderVersion = 2L,
            timestamp = 2_000_000L,
          ),
          OrderSyncLogEntity(
            logId = syncedLogId,
            orderId = "order-ok-2",
            orderNumber = 1102,
            operation = OrderSyncLogOperation.DELETE,
            status = OrderSyncLogStatus.SUCCEEDED,
            summary = "Synced to cloud: status=CANCELLED, deleted=true",
            deviceId = device.deviceId,
            orderVersion = 3L,
            timestamp = 2_001_000L,
          ),
        ),
      )
    }

    authRule.fakeConnectivity.setOnline(true)
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      val nodes = composeTestRule.onAllNodesWithTag("topbar_sync_pill").fetchSemanticsNodes()
      nodes.firstOrNull()?.config?.let { cfg ->
        cfg.toString().contains("Sync Error")
      } == true
    }
    // Topbar must surface "Sync Error" rather than hiding behind "Pending"
    composeTestRule.onNodeWithTag("topbar_sync_pill").assertTextEquals("Sync Error")
    composeTestRule.onNodeWithTag("topbar_sync_pill").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("sync_status_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sync_overview_pill").assert(hasAnyDescendant(hasText("Sync Error")))

    // Scroll to Order Sync Status Log section and verify both logs appear
    composeTestRule.onNodeWithTag("sync_status_list")
      .performScrollToNode(hasTestTag("sync_order_logs_header"))
    composeTestRule.onNodeWithTag("sync_order_logs_header").assertIsDisplayed()

    composeTestRule.onNodeWithTag("sync_status_list")
      .performScrollToNode(hasTestTag("order_sync_log_item_$failedLogId"))
    composeTestRule.onNodeWithTag("order_sync_log_item_$failedLogId").assertIsDisplayed()

    composeTestRule.onNodeWithTag("sync_status_list")
      .performScrollToNode(hasTestTag("order_sync_log_item_$syncedLogId"))
    composeTestRule.onNodeWithTag("order_sync_log_item_$syncedLogId").assertIsDisplayed()

    // Clear logs action works
    composeTestRule.onNodeWithTag("sync_status_list")
      .performScrollToNode(hasTestTag("clear_order_sync_logs_button"))
    composeTestRule.onNodeWithTag("clear_order_sync_logs_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("sync_order_logs_empty").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("sync_order_logs_empty").assertIsDisplayed()
  }
}
