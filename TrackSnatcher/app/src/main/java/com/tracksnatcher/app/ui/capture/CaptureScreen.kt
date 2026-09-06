package com.tracksnatcher.app.ui.capture

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.alpha
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
import com.tracksnatcher.app.domain.model.AppError
import com.tracksnatcher.app.domain.model.MusicService
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.ui.navigation.CaptureLaunch
import com.tracksnatcher.app.ui.theme.TileHues
import com.tracksnatcher.app.ui.theme.TrackSnatcherTheme
import kotlinx.coroutines.delay

private const val SUCCESS_DISMISS_MS = 1_100L

// Mic permission is enforced explicitly below (checkSelfPermission + the request launcher);
// lint can't trace that guard through the ViewModel, so we suppress the false positive.
@SuppressLint("MissingPermission")
@Composable
fun CaptureScreen(
    initialLaunch: CaptureLaunch,
    onOpenManualMode: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startCapture() else viewModel.mikePermissionDenied()
    }

    // Kick off the flow once: register any target playlist, then ensure mic permission.
    LaunchedEffect(Unit) {
        (initialLaunch as? CaptureLaunch.Immediate)?.let { viewModel.setTargetPlaylist(it.targetPlaylistName) }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startCapture() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Auto-dismiss the overlay shortly after a successful add.
    LaunchedEffect(state) {
        if (state is CaptureUiState.Added) {
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
                CaptureUiState.Idle, CaptureUiState.Listening ->
                    ListeningContent()

                is CaptureUiState.Matched ->
                    MatchedContent(
                        track = current.track,
                        playlists = current.playlists,
                        onPick = { viewModel.addToPlaylist(current.track, it) },
                        onOpenManualMode = onOpenManualMode,
                    )

                is CaptureUiState.Adding ->
                    AddingContent(track = current.track, playlist = current.playlist)

                is CaptureUiState.Added ->
                    AddedContent(track = current.track, playlist = current.playlist)

                is CaptureUiState.Error ->
                    ErrorContent(error = current.error, onRetry = viewModel::retry)
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
        animationSpec = infiniteRepeatable(tween(750), androidx.compose.animation.core.RepeatMode.Reverse),
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
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Listening…",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Point at the music for a few seconds",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// --- Step 2: Quick-target grid -------------------------------------------------------

@Composable
private fun MatchedContent(
    track: Track,
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onOpenManualMode: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TrackHeader(track)
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Add to",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(playlists.take(4), key = { _, p -> p.id }) { index, playlist ->
                PlaylistTile(
                    playlist = playlist,
                    hue = TileHues[index % TileHues.size],
                    onClick = { onPick(playlist) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onOpenManualMode) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("More playlists / manual add")
        }
    }
}

@Composable
private fun PlaylistTile(
    playlist: Playlist,
    hue: Color,
    onClick: () -> Unit,
) {
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
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.trackCount} tracks",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Adding + Success ----------------------------------------------------------------

@Composable
private fun AddingContent(track: Track, playlist: Playlist) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Adding to ${playlist.name}…",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${track.title} · ${track.artist}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddedContent(track: Track, playlist: Playlist) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Added",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Added to ${playlist.name}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${track.title} · ${track.artist}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Error ---------------------------------------------------------------------------

@Composable
private fun ErrorContent(error: AppError, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = error.userMessage(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = error.userHint(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(0.98f)) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            onPick = {},
            onOpenManualMode = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0F13)
@Composable
private fun ListeningPreview() {
    TrackSnatcherTheme { ListeningContent() }
}
