package eu.heha.conifer.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.heha.conifer.ConiferApp

@Composable
fun BitsRoute(permissionHandler: ConiferApp.PermissionHandler? = null) {
    val model = viewModel { BitsViewModel(permissionHandler) }
    BitsPane(
        state = model.state,
        actions = BitsPaneActions(
            onClickAdd = { model.onClickAdd() },
            onNewBitTextChange = { model.onNewBitTextChange(it) },
            onClickRequestPermission = { permissionHandler?.requestPermission() }
        )
    )
}