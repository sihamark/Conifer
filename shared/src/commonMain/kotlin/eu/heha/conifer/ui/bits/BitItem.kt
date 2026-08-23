package eu.heha.conifer.ui.bits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.bits_action_bit_options
import conifer.shared.generated.resources.bits_action_cancel
import conifer.shared.generated.resources.bits_action_delete
import conifer.shared.generated.resources.bits_action_menu_cancel_edit
import conifer.shared.generated.resources.bits_action_menu_edit
import conifer.shared.generated.resources.bits_message_delete_bit
import conifer.shared.generated.resources.bits_title_delete_bit
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.LocalDateTimeFormats
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BitItem(
    bit: Bit,
    isEditing: Boolean,
    onClickStartEdit: () -> Unit,
    onClickCancelEdit: () -> Unit,
    onClickDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            .takeIf { isEditing },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .combinedClickable(
                onClick = {},
                // Double-clicking a bit starts editing it, mirroring the "Edit" menu action.
                onDoubleClick = onClickStartEdit
            )
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.weight(1f)
                    .align(Alignment.CenterVertically)
                    .padding(vertical = 8.dp)
                    .padding(start = 8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        LocalDateTimeFormats.current.timeOfDay(bit.date.time),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    bit.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            }
            Box {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.bits_action_bit_options)
                    )
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isEditing) {
                                        Res.string.bits_action_menu_cancel_edit
                                    } else {
                                        Res.string.bits_action_menu_edit
                                    }
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            if (isEditing) onClickCancelEdit() else onClickStartEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.bits_action_delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onClickDelete = {
                showDeleteDialog = false
                onClickDelete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    onClickDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.bits_title_delete_bit)) },
        text = { Text(stringResource(Res.string.bits_message_delete_bit)) },
        confirmButton = {
            TextButton(onClick = onClickDelete) {
                Text(stringResource(Res.string.bits_action_delete))
            }
        },
        dismissButton = {
            TextButton(onDismiss) {
                Text(stringResource(Res.string.bits_action_cancel))
            }
        }
    )
}
