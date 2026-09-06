package com.tracksnatcher.app.ui.capture

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracksnatcher.app.billing.Entitlements
import com.tracksnatcher.app.billing.TierLimits
import com.tracksnatcher.app.domain.model.AppError
import com.tracksnatcher.app.domain.model.MusicService
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.ui.navigation.CaptureLaunch
import com.tracksnatcher.app.ui.share.ShareableMemoryCard
import com.tracksnatcher.app.ui.theme.TileHues
import com.tracksnatcher.app.ui.theme.TrackSnatcherTheme
import kotlinx.coroutines.delay

private const val SUCCESS_DISMISS_MS = 2_600L // leave time to tap Undo before auto-close

@SuppressLint("MissingPermission") // mic permission is checked below; lint can't trace it via the VM
@Composable
fun CaptureScreen(
    initialLaunch: CaptureLaunch,
    onOpenManualMode: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entitlements by viewModel.entitlements.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.startCapture() else viewModel.mikePermissionDenied() }

    LaunchedEffect(Unit) {
        (initialLaunch as? CaptureLaunch.Immediate)?.let { viewModel.setTargetPlaylist(it.targetPlaylistName) }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startCapture() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Auto-dismiss only when there's no shareable memory to linger on.
    LaunchedEffect(state) {
        val added = state as? CaptureUiState.Added
        if (added != null && added.memory == null) {
            delay(SUCCESS_DISMISS_MS)
            (context as? Activity)?.finish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(targetState = state, label = "capture-state") { current ->
            when (current) {
                CaptureUiState.Idle, CaptureUiState.Listening -> ListeningContent()

                CaptureUiState.LimitReached ->
                    LimitReachedContent(onUpgrade = onOpenPaywall, onClose = { (context as? Activity)?.finish() })

                is CaptureUiState.Matched ->
                    MatchedContent(
                        track = current.track,
                        playlists = current.playlists,
                        entitlements = entitlements,
                        onPick = { viewModel.chooseTarget(current.track, it, current.playlists) },
                        onOpenManualMode = onOpenManualMode,
                        onUpgrade = onOpenPaywall,
                    )

                is CaptureUiState.DuplicateWarning -> {
                    // Grid stays behind the sheet so "Choose another" is one tap away.
                    MatchedContent(
                        track = current.track,
                        playlists = current.gridPlaylists,
                        entitlements = entitlements,
                        onPick = { viewModel.chooseTarget(current.track, it, current.gridPlaylists) },
                        onOpenManualMode = onOpenManualMode,
                        onUpgrade = onOpenPaywall,
                    )
                    DuplicateBottomSheet(
                        track = current.track,
                        duplicate = current.duplicate,
                        onAddAnyway = { viewModel.addAnyway(current.track, current.playlist) },
                        onChooseAnother = { viewModel.chooseAnother(current.track, current.gridPlaylists) },
                        onDismiss = { viewModel.chooseAnother(current.track, current.gridPlaylists) },
                    )
                }

                is CaptureUiState.Adding -> AddingContent(current.track, current.playlist)

                is CaptureUiState.Added ->
                    AddedContent(
                        track = current.track,
                        playlist = current.playlist,
                        memory = current.memory,
                        onUndo = {
                            viewModel.undo(current.track, current.playlist)
                            (context as? Activity)?.finish()
                        },
                        onDone = { (context as? Activity)?.finish() },
                    )

                is CaptureUiState.Error -> ErrorContent(current.error, onRetry = viewModel::retry)
            }
        }
    }
}

// --- Step 1: Listening ---------------------------------------------------------------

@Composable
private fun ListeningContent() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "scale",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Listening…", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(
            "Point at the music for a few seconds",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// --- Free cap reached ----------------------------------------------------------------

@Composable
private fun LimitReachedContent(onUpgrade: () -> Unit, onClose: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("You've hit your free limit", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Free includes ${TierLimits.FREE_MONTHLY_SNATCH_CAP} snatches a month. Go Pro for unlimited.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) { Text("Upgrade to Pro") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClose) { Text("Maybe later") }
    }
}

// --- Step 2: Quick-target grid -------------------------------------------------------

@Composable
private fun MatchedContent(
    track: Track,
    playlists: List<Playlist>,
    entitlements: Entitlements,
    onPick: (Playlist) -> Unit,
    onOpenManualMode: () -> Unit,
    onUpgrade: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TrackHeader(track)
        Spacer(Modifier.height(24.dp))
        Text("Add to", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (playlists.size == 1) 1 else 2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(playlists, key = { _, p -> p.id }) { index, playlist ->
                PlaylistTile(playlist, TileHues[index % TileHues.size], onClick = { onPick(playlist) })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!entitlements.isPro) {
            TextButton(onClick = onUpgrade) {
                Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Unlock 4 one-tap playlists with Pro")
            }
        }
        TextButton(onClick = onOpenManualMode) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("More playlists / manual add")
        }
    }
}

@Composable
private fun PlaylistTile(playlist: Playlist, hue: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(20.dp))
            .background(hue.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(18.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column {
            Text(playlist.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${playlist.trackCount} tracks", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// --- Adding + Success ----------------------------------------------------------------

@Composable
private fun AddingContent(track: Track, playlist: Playlist) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text("Adding to ${playlist.name}…", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("${track.title} · ${track.artist}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddedContent(
    track: Track,
    playlist: Playlist,
    memory: com.tracksnatcher.app.domain.model.SonicMemory?,
    onUndo: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = "Added", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Added to ${playlist.name}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)

        // Fat Undo — a wrong add is one tap to reverse, and it refunds the snatch count.
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onUndo) {
            Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Wrong song? Undo")
        }

        if (memory != null) {
            Spacer(Modifier.height(14.dp))
            ShareableMemoryCard(memory = memory)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

// --- Error ---------------------------------------------------------------------------

@Composable
private fun ErrorContent(error: AppError, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(error.userMessage(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(error.userHint(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Try again")
        }
    }
}

@Composable
private fun TrackHeader(track: Track) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(track.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(track.artist, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun AppError.userMessage(): String = when (this) {
    is AppError.NoMatch -> "No match found"
    is AppError.MicPermissionDenied -> "Microphone needed"
    is AppError.NotAuthorized -> "Sign-in required"
    is AppError.Network -> "You're offline"
    is AppError.Unknown -> "Something went wrong"
}

private fun AppError.userHint(): String = when (this) {
    is AppError.NoMatch -> "Move closer to the speaker and try again"
    is AppError.MicPermissionDenied -> "Enable microphone access in Settings"
    is AppError.NotAuthorized -> "Reconnect your music service to keep adding tracks"
    is AppError.Network -> "Check your connection and retry"
    is AppError.Unknown -> "Give it another go"
}

// --- Previews ------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun MatchedPreview() {
    TrackSnatcherTheme {
        MatchedContent(
            track = Track("rec", "Midnight City", "M83"),
            playlists = listOf(
                Playlist("1", "Favorites", MusicService.SPOTIFY, 214, pinned = true),
                Playlist("2", "Driving", MusicService.SPOTIFY, 88, pinned = true),
                Playlist("3", "Gym", MusicService.SPOTIFY, 132, pinned = true),
                Playlist("4", "Country", MusicService.SPOTIFY, 57, pinned = true),
            ),
            entitlements = Entitlements(),
            onPick = {},
            onOpenManualMode = {},
            onUpgrade = {},
        )
    }
}
