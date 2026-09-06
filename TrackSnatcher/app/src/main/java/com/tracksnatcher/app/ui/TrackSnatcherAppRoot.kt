package com.tracksnatcher.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tracksnatcher.app.ui.capture.CaptureScreen
import com.tracksnatcher.app.ui.manual.ManualScreen
import com.tracksnatcher.app.ui.navigation.CaptureLaunch
import com.tracksnatcher.app.ui.navigation.Routes
import com.tracksnatcher.app.ui.paywall.PaywallScreen
import com.tracksnatcher.app.ui.settings.SettingsScreen

/**
 * App root + navigation graph. The capture flow is always the start destination — the whole
 * product is built around landing there in one tap; manual mode, settings, and the paywall
 * are side routes.
 */
@Composable
fun TrackSnatcherAppRoot(initialLaunch: CaptureLaunch) {
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        NavHost(navController = navController, startDestination = Routes.CAPTURE) {
            composable(Routes.CAPTURE) {
                CaptureScreen(
                    initialLaunch = initialLaunch,
                    onOpenManualMode = { navController.navigate(Routes.MANUAL) },
                    onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
                )
            }
            composable(Routes.MANUAL) {
                ManualScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
                )
            }
            composable(Routes.PAYWALL) {
                PaywallScreen(
                    onDismiss = { navController.popBackStack() },
                    onPurchased = { navController.popBackStack() },
                )
            }
        }
    }
}
