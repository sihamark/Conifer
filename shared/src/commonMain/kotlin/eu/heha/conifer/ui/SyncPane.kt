package eu.heha.conifer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.sync_action_cancel_connect
import conifer.shared.generated.resources.sync_action_close
import conifer.shared.generated.resources.sync_action_connect
import conifer.shared.generated.resources.sync_action_disconnect
import conifer.shared.generated.resources.sync_action_save_app_root
import conifer.shared.generated.resources.sync_action_sync_now
import conifer.shared.generated.resources.sync_content_status_icon
import conifer.shared.generated.resources.sync_content_syncing
import conifer.shared.generated.resources.sync_debug_action_settings
import conifer.shared.generated.resources.sync_debug_app_root
import conifer.shared.generated.resources.sync_debug_device_id
import conifer.shared.generated.resources.sync_debug_last_error
import conifer.shared.generated.resources.sync_debug_last_gc
import conifer.shared.generated.resources.sync_debug_last_sync
import conifer.shared.generated.resources.sync_debug_never
import conifer.shared.generated.resources.sync_debug_none
import conifer.shared.generated.resources.sync_debug_note
import conifer.shared.generated.resources.sync_debug_root_etag
import conifer.shared.generated.resources.sync_debug_server
import conifer.shared.generated.resources.sync_debug_title
import conifer.shared.generated.resources.sync_label_app_root
import conifer.shared.generated.resources.sync_label_server_url
import conifer.shared.generated.resources.sync_message_connecting
import conifer.shared.generated.resources.sync_note_app_root
import conifer.shared.generated.resources.sync_note_debug_hint
import conifer.shared.generated.resources.sync_note_login_flow
import conifer.shared.generated.resources.sync_placeholder_app_root
import conifer.shared.generated.resources.sync_placeholder_server_url
import conifer.shared.generated.resources.sync_status_last_synced
import conifer.shared.generated.resources.sync_status_never_synced
import conifer.shared.generated.resources.sync_status_syncing
import conifer.shared.generated.resources.sync_subtitle
import conifer.shared.generated.resources.sync_title
import eu.heha.conifer.sync.SyncConnectionState
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Everything the sync feature needs from the UI layer beyond [SyncUiState]: the app bar's status
 * icon (idle/connected/syncing), its debug popover, and the full settings sheet - mirroring
 * `docs/conifer-mockup.html`'s sync entry point.
 */
class SyncPaneActions(
    val onClickSyncIcon: () -> Unit = {},
    val onCloseSheet: () -> Unit = {},
    val onCloseDebug: () -> Unit = {},
    val onOpenSettingsFromDebug: () -> Unit = {},
    val onServerUrlChange: (String) -> Unit = {},
    val onClickConnect: () -> Unit = {},
    val onAppRootChange: (String) -> Unit = {},
    val onClickSaveAppRoot: () -> Unit = {},
    val onClickCancelConnect: () -> Unit = {},
    val onClickSyncNow: () -> Unit = {},
    val onClickDisconnect: () -> Unit = {},
)

private fun Instant.printDateTime() =
    dateTimeInDefaultTz().let { "${it.date.print()} ${it.time.print()}" }

/**
 * App bar entry point: a cloud icon, muted while disconnected, tinted [MaterialTheme.colorScheme]
 * `primary` once connected and spinning while a sync round is in flight. Pressing it once
 * connected opens [SyncDebugPopover] (a debug glance); otherwise it opens the full [SyncSettingsSheet].
 */
@Composable
fun SyncStatusIcon(state: SyncUiState, actions: SyncPaneActions, modifier: Modifier = Modifier) {
    val connection = state.connection
    val isSyncing = (connection as? SyncConnectionState.Connected)?.isSyncing == true
    Box(modifier) {
        IconButton(onClick = actions.onClickSyncIcon) {
            if (isSyncing) {
                val transition = rememberInfiniteTransition(label = "syncSpin")
                val rotation by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = LinearEasing)
                    ),
                    label = "syncSpin"
                )
                Icon(
                    Icons.Filled.Sync,
                    contentDescription = stringResource(Res.string.sync_content_syncing),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(rotation)
                )
            } else {
                Icon(
                    Icons.Filled.Cloud,
                    contentDescription = stringResource(Res.string.sync_content_status_icon),
                    tint = if (connection is SyncConnectionState.Connected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        SyncDebugPopover(state, actions)
    }
    if (state.isSheetOpen) {
        SyncSettingsSheet(state, actions)
    }
}

/** Quick-glance troubleshooting details, anchored on the app bar's status icon. */
@Composable
private fun SyncDebugPopover(state: SyncUiState, actions: SyncPaneActions) {
    val debugInfo = state.debugInfo
    DropdownMenu(expanded = state.isDebugOpen, onDismissRequest = actions.onCloseDebug) {
        Column(Modifier.widthIn(min = 240.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                stringResource(Res.string.sync_debug_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            val connected = state.connection as? SyncConnectionState.Connected
            if (connected != null) {
                AccountChip(connected.username)
                Spacer(Modifier.height(8.dp))
                SyncStatusRow(isSyncing = connected.isSyncing, lastSyncAt = connected.lastSyncAt)
                Spacer(Modifier.height(8.dp))
                DebugRow(stringResource(Res.string.sync_debug_server), connected.server)
            }
            if (debugInfo != null) {
                DebugRow(stringResource(Res.string.sync_debug_device_id), debugInfo.deviceId)
                DebugRow(stringResource(Res.string.sync_debug_app_root), debugInfo.appRoot)
                DebugRow(
                    stringResource(Res.string.sync_debug_last_sync),
                    debugInfo.lastSyncAt?.printDateTime()
                        ?: stringResource(Res.string.sync_debug_never)
                )
                DebugRow(
                    stringResource(Res.string.sync_debug_root_etag),
                    debugInfo.rootEtag ?: stringResource(Res.string.sync_debug_none)
                )
                DebugRow(
                    stringResource(Res.string.sync_debug_last_gc),
                    debugInfo.lastGcAt?.printDateTime()
                        ?: stringResource(Res.string.sync_debug_never)
                )
                DebugRow(
                    stringResource(Res.string.sync_debug_last_error),
                    debugInfo.lastError ?: stringResource(Res.string.sync_debug_none)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.sync_debug_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = actions.onOpenSettingsFromDebug) {
                Text(stringResource(Res.string.sync_debug_action_settings))
            }
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AccountChip(username: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(username, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SyncStatusRow(isSyncing: Boolean, lastSyncAt: Instant?) {
    val text = when {
        isSyncing -> stringResource(Res.string.sync_status_syncing)
        lastSyncAt != null -> stringResource(
            Res.string.sync_status_last_synced,
            lastSyncAt.printDateTime()
        )

        else -> stringResource(Res.string.sync_status_never_synced)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The full sync settings sheet: connect/disconnect and a manual "sync now", one of three bodies
 * depending on [SyncUiState.connection] - mirrors the mockup's disconnected/connecting/connected
 * sections of the same sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsSheet(state: SyncUiState, actions: SyncPaneActions) {
    ModalBottomSheet(onDismissRequest = actions.onCloseSheet) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(Res.string.sync_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.sync_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            AppRootField(state, actions)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            when (val connection = state.connection) {
                is SyncConnectionState.Disconnected -> DisconnectedContent(state, actions)
                is SyncConnectionState.Connecting -> ConnectingContent(actions)
                is SyncConnectionState.Connected -> ConnectedContent(connection, actions)
            }
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = actions.onCloseSheet, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.sync_action_close))
            }
        }
    }
}

/**
 * Where bits are stored on the Nextcloud instance, independent of the connect/disconnect state -
 * [SyncEngine][eu.heha.conifer.sync.SyncEngine] reads this fresh on every sync round, so it can be
 * changed at any time without reconnecting. The "Save" button only appears once the input differs
 * from what's actually persisted, and blank input can't be saved.
 */
@Composable
private fun AppRootField(state: SyncUiState, actions: SyncPaneActions) {
    Column {
        OutlinedTextField(
            value = state.appRootInput,
            onValueChange = actions.onAppRootChange,
            label = { Text(stringResource(Res.string.sync_label_app_root)) },
            placeholder = { Text(stringResource(Res.string.sync_placeholder_app_root)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.sync_note_app_root),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val trimmedInput = state.appRootInput.trim()
        AnimatedVisibility(trimmedInput.isNotBlank() && trimmedInput != state.savedAppRoot) {
            TextButton(onClick = actions.onClickSaveAppRoot) {
                Text(stringResource(Res.string.sync_action_save_app_root))
            }
        }
    }
}

@Composable
private fun DisconnectedContent(state: SyncUiState, actions: SyncPaneActions) {
    Column {
        OutlinedTextField(
            value = state.serverUrlInput,
            onValueChange = actions.onServerUrlChange,
            label = { Text(stringResource(Res.string.sync_label_server_url)) },
            placeholder = { Text(stringResource(Res.string.sync_placeholder_server_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = actions.onClickConnect,
            enabled = state.serverUrlInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.sync_action_connect))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.sync_note_login_flow),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConnectingContent(actions: SyncPaneActions) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(Res.string.sync_message_connecting),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(max = 260.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = actions.onClickCancelConnect, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.sync_action_cancel_connect))
        }
    }
}

@Composable
private fun ConnectedContent(connection: SyncConnectionState.Connected, actions: SyncPaneActions) {
    Column {
        AccountChip(connection.username)
        Spacer(Modifier.height(8.dp))
        SyncStatusRow(isSyncing = connection.isSyncing, lastSyncAt = connection.lastSyncAt)
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(
                onClick = actions.onClickSyncNow,
                enabled = !connection.isSyncing,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(Res.string.sync_action_sync_now))
            }
            TextButton(
                onClick = actions.onClickDisconnect,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(Res.string.sync_action_disconnect))
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.sync_note_debug_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )
    }
}
