package com.tracksnatcher.app.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import android.content.Intent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracksnatcher.app.people.PersonTrack
import com.tracksnatcher.app.people.ShareablePlaylistView

/**
 * The Napster "folder": a person's shareable playlists stacked with multi-select, so you can
 * snatch a batch onto your own playlist. Already-owned tracks are greyed and unselectable.
 * The only actions on a person are: look at lists, snatch, open Spotify. No messaging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonFolderScreen(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: PersonFolderViewModel = hiltViewModel(),
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.paywallRequests.collect { onOpenPaywall() }
    }
    androidx.compose.runtime.LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(library?.displayName ?: "Library") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                library?.spotifyProfileUrl?.let { url ->
                    item {
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Open Spotify profile")
                        }
                    }
                }
                library?.shareablePlaylists?.forEach { view ->
                    item(key = "hdr_${view.playlist.id}") {
                        Text(
                            view.playlist.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(view.tracks, key = { it.track.recognitionId }) { pt ->
                        RaidTrackRow(
                            personTrack = pt,
                            selected = pt.track.recognitionId in selected,
                            onToggle = { if (!pt.alreadyOwned) viewModel.toggle(pt.track.recognitionId) },
                        )
                    }
                }
            }

            if (selected.isNotEmpty()) {
                Button(
                    onClick = viewModel::snatchSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text("Snatch ${selected.size} to my playlist")
                }
            }
        }
    }
}

@Composable
private fun RaidTrackRow(personTrack: PersonTrack, selected: Boolean, onToggle: () -> Unit) {
    val owned = personTrack.alreadyOwned
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable(enabled = !owned, onClick = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (owned) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            else if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                personTrack.track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (owned) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (owned) TextDecoration.LineThrough else null,
            )
            Text(
                if (owned) "${personTrack.track.artist} · already in your library" else personTrack.track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
