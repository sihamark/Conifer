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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.bits_action_cancel
import conifer.shared.generated.resources.sync_action_cancel_connect
import conifer.shared.generated.resources.sync_action_close
import conifer.shared.generated.resources.sync_action_connect
import conifer.shared.generated.resources.sync_action_continue_anyway
import conifer.shared.generated.resources.sync_action_copy_login_url
import conifer.shared.generated.resources.sync_action_disconnect
import conifer.shared.generated.resources.sync_action_retry_open_browser
import conifer.shared.generated.resources.sync_action_save_app_root
import conifer.shared.generated.resources.sync_action_sync_now
import conifer.shared.generated.resources.sync_content_close
import conifer.shared.generated.resources.sync_content_status_icon
import conifer.shared.generated.resources.sync_content_syncing
import conifer.shared.generated.resources.sync_debug_action_hide_details
import conifer.shared.generated.resources.sync_debug_action_settings
import conifer.shared.generated.resources.sync_debug_action_show_details
import conifer.shared.generated.resources.sync_debug_app_root
import conifer.shared.generated.resources.sync_debug_device_id
import conifer.shared.generated.resources.sync_debug_last_error
import conifer.shared.generated.resources.sync_debug_last_gc
import conifer.shared.generated.resources.sync_debug_last_stats
import conifer.shared.generated.resources.sync_debug_last_stats_value
import conifer.shared.generated.resources.sync_debug_last_sync
import conifer.shared.generated.resources.sync_debug_never
import conifer.shared.generated.resources.sync_debug_none
import conifer.shared.generated.resources.sync_debug_note
import conifer.shared.generated.resources.sync_debug_root_etag
import conifer.shared.generated.resources.sync_debug_server
import conifer.shared.generated.resources.sync_debug_status_not_connected
import conifer.shared.generated.resources.sync_debug_title
import conifer.shared.generated.resources.sync_label_app_root
import conifer.shared.generated.resources.sync_label_server_url
import conifer.shared.generated.resources.sync_message_browser_not_opened
import conifer.shared.generated.resources.sync_message_connecting
import conifer.shared.generated.resources.sync_message_insecure_key
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
import conifer.shared.generated.resources.sync_title_insecure_key
import eu.heha.conifer.prefs.SyncPrefs
import eu.heha.conifer.sync.SyncConnectionState
import eu.heha.conifer.sync.SyncDebugInfo
import eu.heha.conifer.sync.SyncStats
import eu.heha.conifer.ui.theme.ConiferTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant


/**
 * The ISO spelling, for the debug details only: those rows are read off the screen into bug reports
 * and compared against server-side timestamps, where one reader's locale would only be in the way.
 * The status line the user actually reads is localized — see [dateAndTimeOf].
 */
private fun Instant.printDateTime() =
    dateTimeInDefaultTz().let { "${it.date.printIso()} ${it.time.printIso()}" }

/**
 * How the sync surface is shown. [SyncUiState.isSyncOpen] only says that the user asked to see
 * sync at all; which surface that turns into is the layout's call — see `BitsPane`, which picks
 * from the window size classes.
 */
enum class SyncPresentation {
    /**
     * Over the bits: [SyncDebugPopover]'s glance once connected, the full [SyncSettingsSheet]
     * otherwise or on request. Anything but the widest windows.
     */
    Sheet,

    /** Beside the bits, as a third pane the bits make room for — see [SyncPane]. */
    Pane
}

/**
 * App bar entry point: a cloud icon, muted while disconnected, tinted [MaterialTheme.colorScheme]
 * `primary` once connected and spinning while a sync round is in flight. Pressing it opens the sync
 * surface, and pressing it again closes it.
 *
 * In [SyncPresentation.Sheet] that surface is hosted here — [SyncDebugPopover] anchored on the
 * icon, or [SyncSettingsSheet] over the whole window. In [SyncPresentation.Pane] the surface is a
 * pane beside the bits that `BitsPane` hosts instead, so the icon is only its toggle.
 */
@Composable
fun SyncStatusIcon(
    state: SyncUiState,
    actions: SyncPaneActions = SyncPaneActions(),
    presentation: SyncPresentation = SyncPresentation.Sheet,
    modifier: Modifier = Modifier
) {
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
        if (presentation == SyncPresentation.Sheet) {
            SyncDebugPopover(state, actions)
        }
    }
    if (presentation == SyncPresentation.Sheet && state.isSyncOpen && state.areSettingsOpen) {
        SyncSettingsSheet(state, actions)
    }
}

/**
 * Quick-glance troubleshooting details, anchored on the app bar's status icon. Skipped while
 * [SyncUiState.areSettingsOpen], where the sheet shows the same status in full instead.
 */
@Composable
private fun SyncDebugPopover(state: SyncUiState, actions: SyncPaneActions) {
    val isOpen = state.isSyncOpen && !state.areSettingsOpen
    DropdownMenu(
        expanded = isOpen,
        onDismissRequest = actions.onCloseSync,
        // Focusable so that the popup is a focus owner in its own right, and the key below is
        // handed to it rather than to the screen behind. On this platform a popup is an in-window
        // layer and the handler is reached either way — a test cannot tell the flag apart — but
        // where a popup is a real window with FLAG_NOT_FOCUSABLE it never sees a key at all, and
        // the press would go to the screen instead, which is precisely what must not happen.
        properties = PopupProperties(focusable = true)
    ) {
        // Esc spelled out rather than left to `dismissOnBackPress`, which is wired to a back
        // gesture and never to this key — the popover used to sit there with Esc doing nothing at
        // all, neither closing it nor reaching the screen. Previewed on a target of its own, so it
        // is caught whichever row inside holds the focus: the rows are buttons and take it as soon
        // as they are there.
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(isOpen) { if (isOpen) focusRequester.requestFocus() }
        Box(
            Modifier
                .onPreviewKeyEvent { event ->
                    val isEscape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                    if (isEscape) actions.onCloseSync()
                    isEscape
                }
                .focusRequester(focusRequester)
                .focusTarget()
        ) {
            SyncDebugContent(state, actions)
        }
    }
}

/**
 * Split out from [SyncDebugPopover] so previews can render the popover's content directly - a
 * `DropdownMenu` draws into a separate `Popup` layer that the preview renderer never captures,
 * leaving previews of the live popover blank.
 *
 * Deliberately a *glance*, not a dump: who's connected, whether/when the last sync happened, and
 * the two things one actually wants from here (sync now, open the settings). The troubleshooting
 * fields are one tap away behind [SyncDebugDetails] rather than in the way every time.
 */
@Composable
private fun SyncDebugContent(state: SyncUiState, actions: SyncPaneActions) {
    val connected = state.connection as? SyncConnectionState.Connected
    Column(Modifier.widthIn(min = 240.dp).padding(start = 16.dp, end = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(Res.string.sync_debug_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (state.debugInfo != null) {
                DetailsToggle(
                    isOpen = state.areDebugDetailsOpen,
                    onClick = actions.onToggleDebugDetails
                )
            }
        }
        if (connected != null) {
            AccountChip(connected.username)
            Spacer(Modifier.height(8.dp))
            SyncStatusRow(isSyncing = connected.isSyncing, lastSyncAt = connected.lastSyncAt)
        } else {
            Text(
                stringResource(Res.string.sync_debug_status_not_connected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (connected != null) {
                TextButton(
                    onClick = actions.onClickSyncNow,
                    enabled = !connected.isSyncing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.sync_action_sync_now))
                }
            }
            TextButton(
                onClick = actions.onOpenSettings,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(Res.string.sync_debug_action_settings))
            }
        }
        if (state.debugInfo != null) {
            AnimatedVisibility(state.areDebugDetailsOpen) {
                SyncDebugDetails(debugInfo = state.debugInfo, server = connected?.server)
            }
        }
    }
}

/**
 * The one control that turns [SyncDebugContent] from a glance into the full troubleshooting dump.
 * An icon button in the header row rather than a labelled button in the body: the label would sit
 * out of line with the popover's real actions ("Sync now", "Sync settings…") and read like a third
 * one, when all it does is expand what's already on screen.
 */
@Composable
private fun DetailsToggle(isOpen: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = stringResource(
                if (isOpen) Res.string.sync_debug_action_hide_details
                else Res.string.sync_debug_action_show_details
            )
        )
    }
}

/**
 * Everything a bug report would want and nobody needs at a glance - see [SyncDebugInfo]. [server]
 * comes from the connection rather than [SyncDebugInfo] (which doesn't carry it) and is simply
 * omitted while disconnected.
 */
@Composable
private fun SyncDebugDetails(debugInfo: SyncDebugInfo, server: String?) {
    Column {
        HorizontalDivider(Modifier.padding(bottom = 8.dp))
        if (server != null) {
            DebugRow(stringResource(Res.string.sync_debug_server), server)
        }
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
        val stats = debugInfo.lastStats
        if (stats != null) {
            DebugRow(
                stringResource(Res.string.sync_debug_last_stats),
                stringResource(
                    Res.string.sync_debug_last_stats_value,
                    stats.pushed,
                    stats.pulled,
                    stats.merged,
                )
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.sync_debug_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            LocalDateTimeFormats.current.dateAndTimeOf(lastSyncAt)
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
 * The sync surface as a pane of its own, for windows wide enough that `BitsPane` can put a third
 * pane beside the bits (see [SyncPresentation]). It shows in one place what
 * [SyncPresentation.Sheet] splits between the glance and the sheet - status, the actions and the
 * settings fields - since a pane that is already on screen has nothing to gain from keeping half of
 * itself behind a "Sync settings…" button. The troubleshooting fields do stay behind the header's
 * toggle, exactly as they are in the popover.
 */
@Composable
fun SyncPane(
    state: SyncUiState,
    actions: SyncPaneActions = SyncPaneActions(),
    // Follows the main pane's top bar, so the pane's title lines up with the bits beside it; see
    // the spacer below.
    isTopBarVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val connected = state.connection as? SyncConnectionState.Connected
    Column(
        modifier
            .width(SYNC_PANE_WIDTH)
            .padding(horizontal = 16.dp)
            // Connected the pane is short, but the connect form - and the troubleshooting fields
            // above all - can outgrow even a large window's height.
            .verticalScroll(rememberScrollState())
    ) {
        // Lines the pane's content up with the list's, below the top bar floating over the main
        // pane, and follows the bar out of the way while the IME is open - as the day sidebar on
        // the other side of the bits does.
        AnimatedVisibility(isTopBarVisible) {
            Spacer(Modifier.height(TopAppBarDefaults.TopAppBarExpandedHeight))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(Res.string.sync_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (state.debugInfo != null) {
                DetailsToggle(
                    isOpen = state.areDebugDetailsOpen,
                    onClick = actions.onToggleDebugDetails
                )
            }
            IconButton(onClick = actions.onCloseSync) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.sync_content_close)
                )
            }
        }
        // Only worth the room while sync is still something the user might switch on: once
        // connected, "nothing leaves this device until you connect an account" is behind them.
        if (connected == null) {
            Text(
                stringResource(Res.string.sync_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))
        SyncBody(
            state = state,
            actions = actions,
            // The hint points at the app bar's icon for a status glance, which is what this pane
            // already is.
            isDebugHintVisible = false
        )
        if (state.debugInfo != null) {
            AnimatedVisibility(state.areDebugDetailsOpen) {
                SyncDebugDetails(debugInfo = state.debugInfo, server = connected?.server)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * The full sync settings sheet: connect/disconnect and a manual "sync now", one of three bodies
 * depending on [SyncUiState.connection] - mirrors the mockup's disconnected/connecting/connected
 * sections of the same sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsSheet(state: SyncUiState, actions: SyncPaneActions = SyncPaneActions()) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Buttons inside the sheet have to animate it away themselves. A drag, the scrim and the back
    // gesture all run the sheet's own hide animation and only report `onDismissRequest` afterwards,
    // but an action that flips `isSheetOpen` right away takes the sheet out of the composition
    // while it is still on screen, leaving nothing to animate. `onHidden` runs only once the sheet
    // really is hidden, so a hide the user interrupts (by dragging the sheet back up) keeps it open.
    fun hideThen(onHidden: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onHidden()
        }
    }

    ModalBottomSheet(
        onDismissRequest = actions.onCloseSync,
        sheetState = sheetState
    ) {
        SyncSettingsSheetContent(
            state = state,
            actions = actions,
            onClickClose = { hideThen(actions.onCloseSync) },
            // Disconnecting leaves this sheet with nothing but the connect form, so here it closes
            // the sheet too - with the same animated exit. (The pane keeps the form instead: it is
            // not in the way of anything.) Which is why the closing is done here rather than in
            // SyncViewModel.onClickDisconnect, where the pane would inherit it.
            onClickDisconnect = {
                hideThen {
                    actions.onClickDisconnect()
                    actions.onCloseSync()
                }
            }
        )
    }
}

/**
 * Split out from [SyncSettingsSheet] so previews can render the sheet's content directly -
 * previewing the live `ModalBottomSheet` hangs forever, since its `SheetState` never settles
 * without a real window to animate against.
 */
@Composable
private fun SyncSettingsSheetContent(
    state: SyncUiState,
    actions: SyncPaneActions = SyncPaneActions(),
    // The sheet-closing actions come in separately so [SyncSettingsSheet] can animate the sheet out
    // first; previews render this content on its own, where the plain actions are what's wanted.
    onClickClose: () -> Unit = actions.onCloseSync,
    onClickDisconnect: () -> Unit = actions.onClickDisconnect
) {
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
        SyncBody(
            state = state,
            actions = actions,
            onClickDisconnect = onClickDisconnect
        )
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onClickClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.sync_action_close))
        }
    }
}

/**
 * What the sheet and [SyncPane] both show: where the bits go, and one of three sections for the
 * current [SyncUiState.connection] - mirroring the mockup's disconnected/connecting/connected
 * sections.
 */
@Composable
private fun SyncBody(
    state: SyncUiState,
    actions: SyncPaneActions,
    onClickDisconnect: () -> Unit = actions.onClickDisconnect,
    isDebugHintVisible: Boolean = true
) {
    AppRootField(state, actions)
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
    when (val connection = state.connection) {
        is SyncConnectionState.Disconnected -> DisconnectedContent(state, actions)
        is SyncConnectionState.Connecting -> ConnectingContent(connection, actions)
        is SyncConnectionState.Connected -> ConnectedContent(
            connection = connection,
            actions = actions,
            onClickDisconnect = onClickDisconnect,
            isDebugHintVisible = isDebugHintVisible
        )
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
        val insecureKeyCustody = state.insecureKeyCustody
        if (insecureKeyCustody != null) {
            InsecureKeyWarning(
                custody = insecureKeyCustody,
                onConnectAnyway = actions.onClickConnectAnyway,
                onCancel = actions.onCancelInsecureKeyWarning
            )
        } else {
            Button(
                onClick = actions.onClickConnect,
                enabled = state.serverUrlInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.sync_action_connect))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.sync_note_login_flow),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Gates [SyncCoordinator.connect][eu.heha.conifer.sync.SyncCoordinator.connect] behind an explicit
 * confirmation when the credentials it's about to write would land in a weaker key custody than
 * usual - shown inline in place of the "Connect" button, *before* the Login Flow v2 dance starts,
 * since once credentials are actually stored there's nothing left to warn about.
 */
@Composable
private fun InsecureKeyWarning(
    custody: String,
    onConnectAnyway: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp)
    ) {
        Text(
            stringResource(Res.string.sync_title_insecure_key),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.sync_message_insecure_key, custody),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.bits_action_cancel))
            }
            TextButton(
                onClick = onConnectAnyway,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(Res.string.sync_action_continue_anyway))
            }
        }
    }
}

@Composable
private fun ConnectingContent(
    connection: SyncConnectionState.Connecting,
    actions: SyncPaneActions
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(
                    if (connection.didOpenBrowser) {
                        Res.string.sync_message_connecting
                    } else {
                        Res.string.sync_message_browser_not_opened
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(max = 260.dp)
            )
        }
        // The poll keeps running while this is up, so the login still completes the moment the
        // user reaches the URL by whatever means.
        if (!connection.didOpenBrowser) {
            Spacer(Modifier.height(12.dp))
            SelectionContainer {
                Text(
                    connection.loginUrl,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 260.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { actions.onClickCopyLoginUrl(connection.loginUrl) }) {
                    Text(stringResource(Res.string.sync_action_copy_login_url))
                }
                TextButton(onClick = actions.onClickOpenLoginUrl) {
                    Text(stringResource(Res.string.sync_action_retry_open_browser))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = actions.onClickCancelConnect, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.sync_action_cancel_connect))
        }
    }
}

@Composable
private fun ConnectedContent(
    connection: SyncConnectionState.Connected,
    actions: SyncPaneActions,
    onClickDisconnect: () -> Unit = actions.onClickDisconnect,
    /** Off where the status glance the hint points at is already on screen - see [SyncPane]. */
    isDebugHintVisible: Boolean = true
) {
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
                onClick = onClickDisconnect,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(Res.string.sync_action_disconnect))
            }
        }
        if (isDebugHintVisible) {
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
}

data class SyncUiState(
    val connection: SyncConnectionState = SyncConnectionState.Disconnected,
    /**
     * Whether the user asked to see sync at all. What that turns into is up to
     * [SyncPresentation]: the popover glance, the settings sheet, or the third pane.
     */
    val isSyncOpen: Boolean = false,
    /**
     * [SyncPresentation.Sheet] only: the glance has been traded for the full settings sheet - which
     * is also where it starts while there is no connection to glance at. The pane shows both at
     * once and ignores this.
     */
    val areSettingsOpen: Boolean = false,
    /** Whether the surface currently shows [debugInfo] as well as the status. */
    val areDebugDetailsOpen: Boolean = false,
    val serverUrlInput: String = "",
    val debugInfo: SyncDebugInfo? = null,
    /** Text field content; may differ from [savedAppRoot] while the user is still editing it. */
    val appRootInput: String = SyncPrefs.DEFAULT_APP_ROOT,
    val savedAppRoot: String = SyncPrefs.DEFAULT_APP_ROOT,
    /** Non-null while a "connect anyway?" warning is pending - see [eu.heha.conifer.sync.SyncCoordinator.insecureKeyCustody]. */
    val insecureKeyCustody: String? = null,
)

/**
 * Everything the sync feature needs from the UI layer beyond [SyncUiState]: the app bar's status
 * icon (idle/connected/syncing) and whichever surface it opens - popover, settings sheet or
 * [SyncPane] - mirroring `docs/conifer-mockup.html`'s sync entry point.
 */
class SyncPaneActions(
    val onClickSyncIcon: () -> Unit = {},
    val onCloseSync: () -> Unit = {},
    val onToggleDebugDetails: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onServerUrlChange: (String) -> Unit = {},
    val onClickConnect: () -> Unit = {},
    val onClickConnectAnyway: () -> Unit = {},
    val onCancelInsecureKeyWarning: () -> Unit = {},
    val onAppRootChange: (String) -> Unit = {},
    val onClickSaveAppRoot: () -> Unit = {},
    val onClickCancelConnect: () -> Unit = {},
    /** Both only reachable when the browser could not be opened - see [ConnectingContent]. */
    val onClickCopyLoginUrl: (String) -> Unit = {},
    val onClickOpenLoginUrl: () -> Unit = {},
    val onClickSyncNow: () -> Unit = {},
    val onClickDisconnect: () -> Unit = {},
)

/**
 * Width of [SyncPane]. Wide enough for the troubleshooting rows' label-and-value pairs (a device id
 * is 36 characters) without leaving the bits beside it less room than the day sidebar has.
 */
private val SYNC_PANE_WIDTH = 360.dp

//region Previews

private val PREVIEW_LAST_SYNC = Instant.fromEpochMilliseconds(1_753_000_000_000)

private val PREVIEW_LAST_GC = Instant.fromEpochMilliseconds(1_752_500_000_000)

private val previewConnection = SyncConnectionState.Connected(
    server = "https://cloud.example.org",
    username = "alice",
    isSyncing = false,
    lastSyncAt = PREVIEW_LAST_SYNC
)

private val previewSyncState = SyncUiState(
    connection = previewConnection,
    isSyncOpen = true,
    debugInfo = SyncDebugInfo(
        deviceId = "a1b2c3d4-e5f6-7890-aaaa-bbbbccccdddd",
        appRoot = "Conifer",
        lastSyncAt = PREVIEW_LAST_SYNC,
        rootEtag = "\"abcd1234ef56\"",
        lastGcAt = PREVIEW_LAST_GC,
        lastError = null,
        lastStats = SyncStats(pushed = 3, pulled = 5, merged = 1),
    )
)

/**
 * Previews render [SyncSettingsSheetContent] directly rather than the full [SyncSettingsSheet] -
 * the live `ModalBottomSheet` never settles without a real window, which hangs the preview
 * renderer forever. The `Surface` stands in for the sheet's own background.
 */
@Composable
private fun SheetPreviewSurface(content: @Composable () -> Unit) {
    ConiferTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            content()
        }
    }
}

/** Not yet connected - just the server-url field and the "Connect to Nextcloud" button. */
@PreviewLightDark
@Composable
private fun SyncSettingsSheetDisconnectedPreview() {
    SheetPreviewSurface {
        SyncSettingsSheetContent(
            state = previewSyncState.copy(
                connection = SyncConnectionState.Disconnected
            )
        )
    }
}

/** Connecting would land credentials in a weaker key custody - the inline warning gates it. */
@PreviewLightDark
@Composable
private fun SyncSettingsSheetInsecureKeyWarningPreview() {
    SheetPreviewSurface {
        SyncSettingsSheetContent(
            state = previewSyncState.copy(
                connection = SyncConnectionState.Disconnected,
                insecureKeyCustody = "software file store (no OS keychain reachable)"
            )
        )
    }
}

/** Waiting on the user to finish signing in their browser (Login Flow v2). */
@PreviewLightDark
@Composable
private fun SyncSettingsSheetConnectingPreview() {
    SheetPreviewSurface {
        SyncSettingsSheetContent(
            state = previewSyncState.copy(
                connection = SyncConnectionState.Connecting(
                    loginUrl = "https://cloud.example.org/index.php/login/v2/flow/abc123"
                )
            )
        )
    }
}

/** The browser never opened, so the URL is offered for the user to reach by hand. */
@PreviewLightDark
@Composable
private fun SyncSettingsSheetBrowserNotOpenedPreview() {
    SheetPreviewSurface {
        SyncSettingsSheetContent(
            state = previewSyncState.copy(
                connection = SyncConnectionState.Connecting(
                    loginUrl = "https://cloud.example.org/index.php/login/v2/flow/abc123",
                    didOpenBrowser = false
                )
            )
        )
    }
}

/** Connected and idle. */
@PreviewLightDark
@Composable
private fun SyncSettingsSheetConnectedPreview() {
    SheetPreviewSurface {
        SyncSettingsSheetContent(
            previewSyncState.copy(
                connection = previewConnection
            )
        )
    }
}


/** Connected with a sync round actually in flight - the spinner/status row differs. */
@PreviewLightDark
@Composable
private fun SyncSettingsSheetSyncingPreview() {
    SheetPreviewSurface {
        SyncSettingsSheetContent(
            previewSyncState.copy(
                connection = previewConnection.copy(isSyncing = true)
            )
        )
    }
}

/**
 * Pressing the status icon once connected opens this glance instead of the full sheet: status,
 * last sync, and the two actions - the troubleshooting fields stay behind "Details".
 */
@PreviewLightDark
@Composable
private fun SyncDebugPopoverPreview() {
    SheetPreviewSurface {
        SyncDebugContent(
            state = previewSyncState,
            actions = SyncPaneActions()
        )
    }
}

/** The same popover with "Details" expanded - everything a bug report would want. */
@PreviewLightDark
@Composable
private fun SyncDebugPopoverDetailsPreview() {
    SheetPreviewSurface {
        SyncDebugContent(
            state = previewSyncState.copy(areDebugDetailsOpen = true),
            actions = SyncPaneActions()
        )
    }
}

/** The app bar's status icon on its own, muted while disconnected. */
@PreviewLightDark
@Composable
private fun SyncStatusIconDisconnectedPreview() {
    ConiferTheme {
        SyncStatusIcon(state = SyncUiState())
    }
}

/**
 * The third pane on its own, connected: the glance's status and actions and the sheet's settings
 * fields in one column, troubleshooting fields still behind the header's toggle.
 */
@PreviewLightDark
@Composable
private fun SyncPaneConnectedPreview() {
    ConiferTheme {
        Surface {
            SyncPane(state = previewSyncState)
        }
    }
}

/** The same pane while disconnected, where it carries the connect form and the "optional" note. */
@PreviewLightDark
@Composable
private fun SyncPaneDisconnectedPreview() {
    ConiferTheme {
        Surface {
            SyncPane(
                state = previewSyncState.copy(connection = SyncConnectionState.Disconnected)
            )
        }
    }
}

/** The pane with its troubleshooting fields expanded - the tallest it gets. */
@PreviewLightDark
@Composable
private fun SyncPaneDetailsPreview() {
    ConiferTheme {
        Surface {
            SyncPane(state = previewSyncState.copy(areDebugDetailsOpen = true))
        }
    }
}
//endregion
