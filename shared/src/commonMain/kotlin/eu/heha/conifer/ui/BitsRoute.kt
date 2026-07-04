package eu.heha.conifer.ui

import androidx.compose.runtime.Composable
import eu.heha.conifer.PermissionHandler
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BitsRoute(permissionHandler: PermissionHandler? = null) {
    val model = koinViewModel<BitsViewModel> { parametersOf(permissionHandler) }
    BitsPane(
        state = model.state,
        actions = BitsPaneActions(
            onClickAdd = { model.onClickAdd() },
            onNewBitTextChange = { model.onNewBitTextChange(it) },
            onClickRequestPermission = { permissionHandler?.requestPermission() },
            onClickDate = model::selectDate,
            onClickCopyBitsOfDateToClipboard = model::copyBitsOfDateToClipboard,
        )
    )
}