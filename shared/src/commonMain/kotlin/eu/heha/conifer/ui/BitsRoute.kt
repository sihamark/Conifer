package eu.heha.conifer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import eu.heha.conifer.PermissionHandler
import eu.heha.conifer.Platform
import eu.heha.conifer.ui.bits.BitsPane
import eu.heha.conifer.ui.bits.BitsPaneActions
import eu.heha.conifer.ui.bits.ShortcutChord
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BitsRoute(permissionHandler: PermissionHandler? = null) {
    val model = koinViewModel<BitsViewModel>()
    val syncModel = koinViewModel<SyncViewModel>()
    val platform = koinInject<Platform>()
    // Rebound whenever the platform hands over a new handler, which on Android is after every
    // recreation of the screen — the model outlives those, the handler does not.
    LaunchedEffect(permissionHandler) { model.bindPermissionHandler(permissionHandler) }
    BitsPane(
        state = model.state,
        actions = BitsPaneActions(
            onClickAdd = { model.onClickAdd() },
            onNewBitTextChange = { model.onNewBitTextChange(it) },
            onClickRequestPermission = { permissionHandler?.requestPermission() },
            onClickDate = model::selectDate,
            onClickAllDays = model::selectAllDays,
            onShiftDate = model::shiftDate,
            onSkipToDateWithBits = model::skipToDateWithBits,
            onSelectToday = model::selectToday,
            onResetSelection = model::resetSelection,
            onSelectTime = model::selectTime,
            onResetToNow = model::resetToNow,
            onClickCopyBitsOfDateToClipboard = model::copyBitsOfDateToClipboard,
            onClickCopyCrashReport = model::copyCrashReportToClipboard,
            onClickShareCrashReport = model::shareCrashReport,
            onDismissCrashReport = model::dismissCrashReport,
            onClickEditBit = model::startEditing,
            onCancelEdit = model::cancelEdit,
            onDeleteBit = model::deleteBit,
            onScrolledToBit = model::onScrolledToBit,
        ),
        syncState = syncModel.state,
        syncActions = SyncPaneActions(
            onClickSyncIcon = syncModel::onClickSyncIcon,
            onCloseSync = syncModel::onCloseSync,
            onToggleDebugDetails = syncModel::onToggleDebugDetails,
            onOpenSettings = syncModel::onOpenSettings,
            onServerUrlChange = syncModel::onServerUrlChange,
            onClickConnect = syncModel::onClickConnect,
            onClickConnectAnyway = syncModel::onClickConnectAnyway,
            onCancelInsecureKeyWarning = syncModel::onCancelInsecureKeyWarning,
            onAppRootChange = syncModel::onAppRootChange,
            onClickSaveAppRoot = syncModel::onClickSaveAppRoot,
            onClickCancelConnect = syncModel::onClickCancelConnect,
            onClickCopyLoginUrl = syncModel::onClickCopyLoginUrl,
            onClickOpenLoginUrl = syncModel::onClickOpenLoginUrl,
            onClickSyncNow = syncModel::onClickSyncNow,
            onClickDisconnect = syncModel::onClickDisconnect,
        ),
        // The only place that knows what kind of device this is; the pane takes these as plain
        // arguments so that it stays a composable a preview or a test can simply call.
        hasHardwareKeyboard = platform.hasHardwareKeyboard,
        // Where ⌥ is the platform's own word-jump, the screen asks for ⌃⌥ instead and leaves the
        // words alone; see ShortcutChord.
        shortcutChord = if (platform.usesOptionForWordJump) {
            ShortcutChord.CtrlAlt
        } else {
            ShortcutChord.Alt
        }
    )
}