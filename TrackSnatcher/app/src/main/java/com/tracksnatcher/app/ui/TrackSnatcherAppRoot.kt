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

/**
 * App root + navigation graph. The capture flow is always the start destination — the whole
 * product is built around landing there in one tap; manual mode is a side route.
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
                )
            }
            composable(Routes.MANUAL) {
                ManualScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
