package eu.heha.conifer.ui

import androidx.compose.runtime.Composable
import eu.heha.conifer.PermissionHandler
import eu.heha.conifer.ui.bits.BitsPane
import eu.heha.conifer.ui.bits.BitsPaneActions
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BitsRoute(permissionHandler: PermissionHandler? = null) {
    val model = koinViewModel<BitsViewModel> { parametersOf(permissionHandler) }
    val syncModel = koinViewModel<SyncViewModel>()
    BitsPane(
        state = model.state,
        actions = BitsPaneActions(
            onClickAdd = { model.onClickAdd() },
            onNewBitTextChange = { model.onNewBitTextChange(it) },
            onClickRequestPermission = { permissionHandler?.requestPermission() },
            onClickDate = model::selectDate,
            onClickAllDays = model::selectAllDays,
            onSelectTime = model::selectTime,
            onResetToNow = model::resetToNow,
            onClickCopyBitsOfDateToClipboard = model::copyBitsOfDateToClipboard,
            onClickEditBit = model::startEditing,
            onCancelEdit = model::cancelEdit,
            onDeleteBit = model::deleteBit,
            onScrolledToBit = model::onScrolledToBit,
        ),
        syncState = syncModel.state,
        syncActions = SyncPaneActions(
            onClickSyncIcon = syncModel::onClickSyncIcon,
            onCloseSheet = syncModel::onCloseSheet,
            onCloseDebug = syncModel::onCloseDebug,
            onToggleDebugDetails = syncModel::onToggleDebugDetails,
            onOpenSettingsFromDebug = syncModel::onOpenSettingsFromDebug,
            onServerUrlChange = syncModel::onServerUrlChange,
            onClickConnect = syncModel::onClickConnect,
            onClickConnectAnyway = syncModel::onClickConnectAnyway,
            onCancelInsecureKeyWarning = syncModel::onCancelInsecureKeyWarning,
            onAppRootChange = syncModel::onAppRootChange,
            onClickSaveAppRoot = syncModel::onClickSaveAppRoot,
            onClickCancelConnect = syncModel::onClickCancelConnect,
            onClickSyncNow = syncModel::onClickSyncNow,
            onClickDisconnect = syncModel::onClickDisconnect,
        )
    )
}