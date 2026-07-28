package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.bits_content_copy_date_to_clipboard
import conifer.shared.generated.resources.bits_message_beginning
import conifer.shared.generated.resources.bits_message_empty
import conifer.shared.generated.resources.bits_message_empty_filtered
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.print
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * The bits of the (possibly day-filtered) list, oldest first so the newest sit next to the input,
 * grouped under sticky day headers — or the empty state when there is nothing to show. Owns the
 * list's scroll position, including the jump to a bit that was just added or edited.
 */
@Composable
internal fun BitsList(
    state: BitsPaneState,
    visibleBitsByDate: List<DatedBits>,
    actions: BitsPaneActions,
    modifier: Modifier = Modifier
) {
    val permissionRationale = state.permissionRationale
    // The DAO delivers bits newest-first; presenting everything reversed puts the newest bits at
    // the bottom next to the input while sticky day headers still pin to the top.
    val listState = rememberLazyListState()
    // Keyed on the list only: it runs when the database flow delivers the updated bits, at which
    // point a pending scrollToBitId (set right after saving) is already in the state. Clearing the
    // id afterwards must not rerun the effect, or it would jump to the bottom right after
    // scrolling to the bit.
    LaunchedEffect(visibleBitsByDate) {
        if (visibleBitsByDate.isEmpty()) return@LaunchedEffect
        val targetId = state.scrollToBitId
        val targetIndex = targetId?.let { visibleBitsByDate.indexOfBit(it) }
        if (targetIndex != null) {
            val isAlreadyVisible = listState.layoutInfo.visibleItemsInfo
                .any { it.key == targetId }
            if (!isAlreadyVisible) {
                // A negative offset lands the bit a third down the viewport, clear of the floating
                // top bar and the pinned sticky day header.
                listState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = -listState.layoutInfo.viewportSize.height / 3
                )
            }
            actions.onScrolledToBit()
        } else {
            val lastIndex =
                visibleBitsByDate.sumOf { it.bits.size + 1 } + LEADING_LIST_ITEMS
            listState.scrollToItem(lastIndex)
        }
    }
    if (visibleBitsByDate.isEmpty()) {
        Column(modifier) {
            Spacer(Modifier.height(TopAppBarDefaults.TopAppBarExpandedHeight))
            PermissionPromptItem(permissionRationale, actions)
            EmptyState(
                isFilteredByDate = state.selectedDate != null &&
                        state.bitsByDate.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    } else {
        LazyColumn(
            state = listState,
            // Bottom arrangement keeps the bits anchored to the input when the list is shorter
            // than the viewport; it has no effect once the list fills the screen.
            verticalArrangement = Arrangement.Bottom,
            modifier = modifier
        ) {
            // Makes up for the height of the floating top bar so the top of the list can scroll
            // fully out from underneath it.
            item(key = "top-bar-spacer") {
                Spacer(Modifier.height(TopAppBarDefaults.TopAppBarExpandedHeight + 16.dp))
            }
            item(key = "permission-prompt") {
                PermissionPromptItem(permissionRationale, actions)
            }
            item(key = "beginning") {
                BeginningNote()
            }
            visibleBitsByDate.asReversed().forEach { datedBits ->
                stickyHeader(key = datedBits.date.toEpochDays()) {
                    DateHeader(
                        date = datedBits.date,
                        onClickCopy = {
                            actions.onClickCopyBitsOfDateToClipboard(datedBits.date)
                        }
                    )
                }
                items(datedBits.bits.asReversed(), key = { it.id }) { bit ->
                    BitItem(
                        bit = bit,
                        isEditing = state.editingBitId == bit.id,
                        onClickStartEdit = { actions.onClickEditBit(bit) },
                        onClickCancelEdit = actions.onCancelEdit,
                        onClickDelete = { actions.onDeleteBit(bit) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// Items preceding the first day header: top-bar spacer, permission prompt, beginning note.
private const val LEADING_LIST_ITEMS = 3

/**
 * Index of the bit with [bitId] in the LazyColumn, mirroring how the list lays the days out:
 * the leading items, then per day (oldest day first) a sticky header followed by its bits
 * (oldest first). Null when the bit is not in the (possibly filtered) list.
 */
private fun List<DatedBits>.indexOfBit(bitId: String): Int? {
    var index = LEADING_LIST_ITEMS
    for (datedBits in asReversed()) {
        index++ // the day's sticky header
        val bitsOldestFirst = datedBits.bits.asReversed()
        val position = bitsOldestFirst.indexOfFirst { it.id == bitId }
        if (position >= 0) return index + position
        index += bitsOldestFirst.size
    }
    return null
}


@Composable
private fun DateHeader(
    date: LocalDate,
    onClickCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                date.print(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Spacer(Modifier.width(8.dp))
            HorizontalDivider(modifier = Modifier.weight(1f))
            IconButton(onClick = onClickCopy) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(Res.string.bits_content_copy_date_to_clipboard),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


/** Marks the very beginning of the list with a wink, nudging the user to keep adding bits. */
@Composable
private fun BeginningNote(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Text(text = "🌱", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.bits_message_beginning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Shown instead of the list when there is nothing to display — either because no bits exist yet
 * or because the selected day has none.
 */
@Composable
private fun EmptyState(isFilteredByDate: Boolean, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = 32.dp, vertical = 48.dp)
    ) {
        Text(text = "🌲", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (isFilteredByDate) {
                    Res.string.bits_message_empty_filtered
                } else {
                    Res.string.bits_message_empty
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
