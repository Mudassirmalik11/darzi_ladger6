package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app.DarziLedgerApp
import com.example.app.TailorApp
import com.example.core.model.ShopUserRole
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.ShopPreferences
import com.example.core.preferences.ThemeMode
import com.example.core.ui.ProvideAppLocale
import com.example.ui.theme.DarziLedgerTheme

import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val appContainer = (application as TailorApp).container

    setContent {
      val initialPrefs = (appContainer.shopPreferencesRepository.preferencesFlow as? StateFlow<ShopPreferences>)
        ?.value ?: ShopPreferences()
      val prefs by appContainer.shopPreferencesRepository.preferencesFlow
        .collectAsStateWithLifecycle(initialValue = initialPrefs)
      val currentUser by appContainer.authSessionProvider.authStateFlow
        .collectAsStateWithLifecycle(initialValue = appContainer.authSessionProvider.currentUser)
      val activeDevice by appContainer.deviceSyncRepository.observeDeviceConfig()
        .collectAsStateWithLifecycle(initialValue = null)

      val effectiveLanguage = if (
        currentUser != null && activeDevice?.userRole == ShopUserRole.STAFF.name
      ) {
        AppLanguage.URDU
      } else {
        prefs.language
      }

      val darkTheme = when (prefs.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
      }

      ProvideAppLocale(language = effectiveLanguage) {
        DarziLedgerTheme(darkTheme = darkTheme) {
          DarziLedgerApp(appContainer = appContainer)
        }
      }
    }
  }
}
