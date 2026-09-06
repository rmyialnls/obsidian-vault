package com.tracksnatcher.app.ui.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracksnatcher.app.session.ApprovalMode
import com.tracksnatcher.app.session.Session
import com.tracksnatcher.app.session.SessionTemplate
import com.tracksnatcher.app.session.TapeItem
import com.tracksnatcher.app.session.sessionJoinUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    onBack: () -> Unit,
    onOpenRecap: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val tape by viewModel.tape.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.paywallRequests.collect { onOpenPaywall() }
    }
    androidx.compose.runtime.LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.name ?: "Session") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (session != null) {
                        IconButton(onClick = onOpenRecap) { Icon(Icons.Filled.History, contentDescription = "Recap") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = session
        if (current == null) {
            StartOrJoin(
                modifier = Modifier.padding(padding),
                onCreate = viewModel::createSession,
                onJoin = viewModel::joinSession,
            )
        } else {
            ActiveSession(
                modifier = Modifier.padding(padding),
                session = current,
                tape = tape,
                isHost = viewModel.isHost,
                onAddToTape = viewModel::addSampleToTape,
                onSnatch = { viewModel.snatch(it) },
                onToggleApproval = viewModel::toggleApproval,
                onToggleLock = viewModel::toggleLock,
                onEndOrLeave = { viewModel.endOrLeave() },
            )
        }
    }
}

@Composable
private fun StartOrJoin(
    modifier: Modifier,
    onCreate: (SessionTemplate, String) -> Unit,
    onJoin: (String) -> Unit,
) {
    var template by remember { mutableStateOf(SessionTemplate.TRIP) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text("Start a session", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Everyone scans one QR and adds what they want to hear — like karaoke for a room.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SessionTemplate.entries.forEach { t ->
                FilterChip(selected = template == t, onClick = { template = t }, label = { Text(t.label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (e.g. Calgary → Fernie)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onCreate(template, name) }, modifier = Modifier.fillMaxWidth()) { Text("Start & show QR") }

        Spacer(Modifier.height(28.dp))
        Text("Join a session", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Enter code (or scan QR)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onJoin(code) }, modifier = Modifier.fillMaxWidth()) { Text("Join") }
    }
}

@Composable
private fun ActiveSession(
    modifier: Modifier,
    session: Session,
    tape: List<TapeItem>,
    isHost: Boolean,
    onAddToTape: () -> Unit,
    onSnatch: (TapeItem) -> Unit,
    onToggleApproval: () -> Unit,
    onToggleLock: () -> Unit,
    onEndOrLeave: () -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { QrJoinCard(session) }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddToTape, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Add to tape")
                }
            }
        }

        if (isHost) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = onToggleApproval,
                        label = { Text(if (session.approvalMode == ApprovalMode.APPROVE) "Approve queue" else "Auto-add") },
                    )
                    AssistChip(onClick = onToggleLock, label = { Text(if (session.locked) "Locked" else "Open") })
                }
            }
        }

        item {
            Text("Tape · ${session.collabPlaylistName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        items(tape, key = { it.id }) { item ->
            TapeRow(item = item, onSnatch = { onSnatch(item) })
        }

        item {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onEndOrLeave) { Text(if (isHost) "End session (playlist stays)" else "Leave session") }
        }
    }
}

@Composable
private fun QrJoinCard(session: Session) {
    val qr = remember(session.code) { QrGenerator().generate(sessionJoinUri(session.code)) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp))
                .padding(10.dp),
        ) {
            Image(bitmap = qr, contentDescription = "Join QR code", modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(8.dp))
        Text("Code: ${session.code}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Hosted by ${session.hostName}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TapeRow(item: TapeItem, onSnatch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(item.track.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val credit = buildString {
                append("added by ${item.addedByName}")
                if (item.snatches.isNotEmpty()) append(" · ${item.snatches.size} snatched")
                if (item.pendingApproval) append(" · pending")
            }
            Text(credit, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Button(onClick = onSnatch) {
            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(" Snatch")
        }
    }
}
