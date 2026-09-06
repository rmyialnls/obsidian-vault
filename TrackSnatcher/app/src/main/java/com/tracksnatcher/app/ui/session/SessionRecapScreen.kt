package com.tracksnatcher.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracksnatcher.app.session.TapeItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Replay a session another day: the ordered tape, who added each track, who snatched it and
 * into which playlist, and where. Tap a person to open their shareable library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionRecapScreen(
    onBack: () -> Unit,
    onOpenPerson: (userId: String) -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val tape by viewModel.tape.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session recap") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text(session?.name ?: "Session", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { /* opens the official streaming playlist in production */ }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(" Play this session")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(tape.sortedBy { it.addedAtEpochMs }, key = { it.id }) { item ->
                RecapRow(item = item, onOpenPerson = onOpenPerson)
            }
        }
    }
}

@Composable
private fun RecapRow(item: TapeItem, onOpenPerson: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(item.track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(item.track.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val place = item.coarsePlace?.let { " · $it" } ?: ""
        Text(
            "added by ${item.addedByName} · ${item.addedAtEpochMs.asTime()}$place",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        item.snatches.forEach { snatch ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clickable { onOpenPerson(snatch.byUserId) },
            ) {
                Text(
                    "↳ ${snatch.byName} snatched → ${snatch.destinationPlaylistName}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun Long.asTime(): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(this))
