package com.tracksnatcher.app.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tracksnatcher.app.domain.model.SonicMemory
import com.tracksnatcher.app.ui.theme.Accent
import com.tracksnatcher.app.ui.theme.Ink
import com.tracksnatcher.app.ui.theme.OnDark
import com.tracksnatcher.app.ui.theme.OnDarkMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The stylized, social-ready Sonic Memory card. Rendered on-screen and captured to a Bitmap
 * for sharing. Colors are hard-coded (not theme tokens) so the exported image looks identical
 * regardless of the viewer's light/dark setting.
 */
@Composable
fun MemoryCard(memory: SonicMemory, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(320.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(listOf(Ink, Color(0xFF1B1E27))),
            )
            .padding(24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2A2E3A)),
            contentAlignment = Alignment.Center,
        ) {
            if (memory.artworkUrl != null) {
                AsyncImage(
                    model = memory.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(72.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = memory.title,
            color = OnDark,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = memory.artist,
            color = OnDarkMuted,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(18.dp))
        LocationBadge(memory)

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Snatched with TrackSnatcher", color = OnDarkMuted, fontSize = 12.sp)
            Text("♬", color = Accent, fontSize = 18.sp)
        }
    }
}

@Composable
private fun LocationBadge(memory: SonicMemory) {
    val place = memory.locationName
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(memory.timestampMs))
    val label = if (place != null) "Heard in $place • $time" else "Heard on ${memory.timestampMs.asDay()} • $time"

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Accent.copy(alpha = 0.18f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = OnDark, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun Long.asDay(): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(this))
