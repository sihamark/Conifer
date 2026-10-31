package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.update_action_dismiss
import conifer.shared.generated.resources.update_action_open
import conifer.shared.generated.resources.update_hint_label
import eu.heha.conifer.update.Release
import org.jetbrains.compose.resources.stringResource

/**
 * The top bar's note that [update] is out: a small chip that opens a menu to read about the release
 * or to stop being reminded of it.
 */
@Composable
internal fun UpdateHint(
    update: Release,
    onClickOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { isMenuOpen = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                stringResource(Res.string.update_hint_label, update.version),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.update_action_open, update.version)) },
                onClick = {
                    isMenuOpen = false
                    onClickOpen()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.update_action_dismiss)) },
                onClick = {
                    isMenuOpen = false
                    onDismiss()
                }
            )
        }
    }
}
