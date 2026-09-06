package com.tracksnatcher.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tracksnatcher.app.ui.capture.CaptureScreen
import com.tracksnatcher.app.ui.manual.ManualScreen
import com.tracksnatcher.app.ui.navigation.CaptureLaunch
import com.tracksnatcher.app.ui.navigation.Routes
import com.tracksnatcher.app.ui.paywall.PaywallScreen
import com.tracksnatcher.app.ui.people.NearbyScreen
import com.tracksnatcher.app.ui.people.PersonFolderScreen
import com.tracksnatcher.app.ui.session.SessionRecapScreen
import com.tracksnatcher.app.ui.session.SessionScreen
import com.tracksnatcher.app.ui.settings.SettingsScreen

/**
 * App root + navigation graph. Capture is always the start destination (one-tap landing);
 * manual mode is the in-app hub that reaches sessions, nearby, settings, and the paywall.
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
                    onOpenSession = { navController.navigate(Routes.SESSION) },
                    onOpenNearby = { navController.navigate(Routes.NEARBY) },
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
            composable(Routes.SESSION) {
                SessionScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRecap = { navController.navigate(Routes.RECAP) },
                    onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
                )
            }
            composable(Routes.RECAP) {
                SessionRecapScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPerson = { userId -> navController.navigate(Routes.person(userId)) },
                )
            }
            composable(Routes.NEARBY) {
                NearbyScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPerson = { userId -> navController.navigate(Routes.person(userId)) },
                )
            }
            composable(
                route = Routes.PERSON,
                arguments = listOf(navArgument("userId") { type = NavType.StringType }),
            ) {
                PersonFolderScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
                )
            }
        }
    }
}
