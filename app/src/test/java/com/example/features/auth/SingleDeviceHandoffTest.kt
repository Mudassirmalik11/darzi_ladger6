package com.example.features.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.app.DefaultAppContainer
import com.example.app.TailorApp
import com.example.core.model.DeviceRegistrationStatus
import com.example.core.model.ShopUserRole
import com.example.data.remote.AuthUser
import com.example.data.remote.DeviceAccessState
import com.example.data.remote.SessionStatusUpdate
import com.example.data.remote.ShopCloudRepository
import com.example.testing.AuthorizedShopTestRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SingleDeviceHandoffTest {

  private val authRule = AuthorizedShopTestRule(initialSignedIn = false, authorizeLocalDevice = false)
  private val composeTestRule = createEmptyComposeRule()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `live single device handoff signs out active device immediately to auth screen`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val existingContainer = app.container
    val testUser = AuthUser("test_shop_uid", "Al-Noor Owner", "owner@alnoor.pk")
    val sessionFlow = MutableStateFlow<SessionStatusUpdate>(SessionStatusUpdate.Active)

    val device = runBlocking {
      val ensured = existingContainer.deviceSyncRepository.ensureLocalDeviceConfigForUser(
        userUid = testUser.uid,
        shopId = testUser.uid,
        userRole = ShopUserRole.OWNER,
      )
      existingContainer.deviceSyncRepository.updateDeviceAuthorization(
        deviceId = ensured.deviceId,
        shopId = testUser.uid,
        deviceName = "Counter Phone",
        deviceRole = "PHONE",
        status = DeviceRegistrationStatus.AUTHORIZED,
        registeredAtMillis = System.currentTimeMillis(),
        userUid = testUser.uid,
        userRole = ShopUserRole.OWNER.name,
      )
    }

    val fakeCloudRepo = object : ShopCloudRepository(
      db = existingContainer.firestore,
      authSessionProvider = existingContainer.authSessionProvider,
      shopRepository = existingContainer.shopRepository,
      shopPreferencesRepository = existingContainer.shopPreferencesRepository,
      deviceSyncRepository = existingContainer.deviceSyncRepository,
    ) {
      override fun observeCurrentSessionStatus(
        shopId: String,
        uid: String,
        deviceId: String,
        userRole: ShopUserRole,
      ): Flow<SessionStatusUpdate> = sessionFlow

      override suspend fun ensureShopAndInspectDevice(): Result<DeviceAccessState> =
        Result.success(
          DeviceAccessState.Authorized(
            currentDevice = device,
            allDevices = emptyList(),
            shopId = testUser.uid,
            userRole = ShopUserRole.OWNER,
          ),
        )
    }

    app.container = DefaultAppContainer(
      applicationContext = app,
      authSessionProviderOverride = existingContainer.authSessionProvider,
      firestoreOverride = existingContainer.firestore,
      connectivityMonitorOverride = existingContainer.connectivityMonitor,
      thermalPrinterClientOverride = existingContainer.thermalPrinterClient,
      shopPreferencesRepositoryOverride = existingContainer.shopPreferencesRepository,
      shopCloudRepositoryOverride = fakeCloudRepo,
    )

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().performClick()
      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        app.container.authSessionProvider.currentUser != null
      }
      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
      }

      // Another device signs in and deletes this device's record -> live snapshot emits DeviceRevoked
      sessionFlow.value = SessionStatusUpdate.DeviceRevoked(device.deviceId)

      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        composeTestRule.onAllNodesWithTag("auth_screen").fetchSemanticsNodes().isNotEmpty() &&
          app.container.authSessionProvider.currentUser == null
      }
      composeTestRule.onNodeWithTag("auth_screen").assertIsDisplayed()
      assertNull(app.container.authSessionProvider.currentUser)

      val updatedLocal = runBlocking {
        app.container.deviceSyncRepository.getDeviceConfigForUser(testUser.uid)
      }
      assertEquals(DeviceRegistrationStatus.UNREGISTERED, updatedLocal?.registrationStatus)
    }
  }

  @Test
  fun `startup verification automatically signs out first device when session revoked remotely`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val existingContainer = app.container
    val testUser = AuthUser("test_shop_uid", "Al-Noor Owner", "owner@alnoor.pk")

    val device = runBlocking {
      val ensured = existingContainer.deviceSyncRepository.ensureLocalDeviceConfigForUser(
        userUid = testUser.uid,
        shopId = testUser.uid,
        userRole = ShopUserRole.OWNER,
      )
      existingContainer.deviceSyncRepository.updateDeviceAuthorization(
        deviceId = ensured.deviceId,
        shopId = testUser.uid,
        deviceName = "First Phone",
        deviceRole = "PHONE",
        status = DeviceRegistrationStatus.AUTHORIZED,
        registeredAtMillis = System.currentTimeMillis(),
        userUid = testUser.uid,
        userRole = ShopUserRole.OWNER.name,
      )
    }

    val fakeCloudRepo = object : ShopCloudRepository(
      db = existingContainer.firestore,
      authSessionProvider = existingContainer.authSessionProvider,
      shopRepository = existingContainer.shopRepository,
      shopPreferencesRepository = existingContainer.shopPreferencesRepository,
      deviceSyncRepository = existingContainer.deviceSyncRepository,
    ) {
      override fun observeCurrentSessionStatus(
        shopId: String,
        uid: String,
        deviceId: String,
        userRole: ShopUserRole,
      ): Flow<SessionStatusUpdate> = MutableStateFlow(SessionStatusUpdate.Active)

      override suspend fun ensureShopAndInspectDevice(): Result<DeviceAccessState> =
        Result.success(
          DeviceAccessState.Revoked(
            currentDevice = device,
            allDevices = emptyList(),
            shopId = testUser.uid,
            userRole = ShopUserRole.OWNER,
          ),
        )
    }

    app.container = DefaultAppContainer(
      applicationContext = app,
      authSessionProviderOverride = existingContainer.authSessionProvider,
      firestoreOverride = existingContainer.firestore,
      connectivityMonitorOverride = existingContainer.connectivityMonitor,
      thermalPrinterClientOverride = existingContainer.thermalPrinterClient,
      shopPreferencesRepositoryOverride = existingContainer.shopPreferencesRepository,
      shopCloudRepositoryOverride = fakeCloudRepo,
    )

    authRule.fakeAuth.setSignedInUser(testUser)

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        composeTestRule.onAllNodesWithTag("auth_screen").fetchSemanticsNodes().isNotEmpty() &&
          app.container.authSessionProvider.currentUser == null
      }
      composeTestRule.onNodeWithTag("auth_screen").assertIsDisplayed()
      assertNull(app.container.authSessionProvider.currentUser)

      val updatedLocal = runBlocking {
        app.container.deviceSyncRepository.getDeviceConfigForUser(testUser.uid)
      }
      assertEquals(DeviceRegistrationStatus.UNREGISTERED, updatedLocal?.registrationStatus)
    }
  }
}
