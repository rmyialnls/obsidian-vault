package com.tracksnatcher.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tracksnatcher.app.MainActivity
import androidx.compose.ui.graphics.Color

/**
 * 1×1 home-screen widget. A single tap fires the [MainActivity.ACTION_CAPTURE] intent,
 * launching straight into ambient listening — the fastest possible entry to the 2-tap flow.
 */
class CaptureWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }

    @Composable
    private fun WidgetContent() {
        val launchIntent = Intent(MainActivity.ACTION_CAPTURE).apply {
            setClassName("com.tracksnatcher.app", "com.tracksnatcher.app.MainActivity")
        }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF1DB954)))
                .clickable(actionStartActivity(launchIntent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "♪",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF0E0F13)),
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

class CaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CaptureWidget()
}
