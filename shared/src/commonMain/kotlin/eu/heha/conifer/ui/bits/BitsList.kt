package eu.heha.conifer.ui.bits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.bits_content_copy_date_to_clipboard
import conifer.shared.generated.resources.bits_message_beginning
import conifer.shared.generated.resources.bits_message_empty
import conifer.shared.generated.resources.bits_message_empty_filtered
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.LocalDateTimeFormats
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * Keeps whatever was at the bottom edge of the list at the bottom edge when the list's height
 * changes — which is what happens every time the keyboard opens: the composer grows by the height of
 * the IME and takes that much off the list from below.
 *
 * Without this the newest bits simply go behind the keyboard. A LazyColumn measured into a shorter
 * viewport holds its *first visible item* still, so the space is taken off the end — the end being
 * where this list keeps the bits worth reading, since it is laid out oldest-first. Scrolling by the
 * same number of pixels the viewport lost hands them back.
 *
 * Growing back is followed the same way, so closing the keyboard puts the list where it was rather
 * than a screenful further on — except when the list is at its end, where the measure pass has
 * already pushed the content down to fill the viewport and scrolling again would take it off the
 * very end it was holding.
 */
@Composable
private fun KeepBottomInView(listState: LazyListState) {
    var previousHeight by remember(listState) { mutableIntStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .collect { height ->
                val previous = previousHeight
                previousHeight = height
                // The first measurement is the height the list starts at: nothing was lost yet, and
                // there is no earlier bottom edge to keep anything at.
                if (previous == 0 || height == 0 || height == previous) return@collect
                val lost = previous - height
                if (lost < 0 && !listState.canScrollForward) return@collect
                listState.scrollBy(lost.toFloat())
            }
    }
}

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
    isTopBarVisible: Boolean,
    /**
     * Inset of the content inside the list. Carries the width limit, because the list itself
     * spans its pane so a drag in the margins beside the bits still scrolls it.
     */
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val permissionRationale = state.permissionRationale
    // The DAO delivers bits newest-first; presenting everything reversed puts the newest bits at
    // the bottom next to the input while sticky day headers still pin to the top.
    val listState = rememberLazyListState()
    // How much of the list's top the floating top bar covers. A bit underneath it is no more in
    // view than one below the bottom edge, so the jump below counts that height as out of view.
    val topBarInset = with(LocalDensity.current) {
        if (isTopBarVisible) TopAppBarDefaults.TopAppBarExpandedHeight.roundToPx() else 0
    }
    KeepBottomInView(listState)
    // Jump to a bit that was just added or edited. Keyed on the list: scrollToBitId is already in
    // the state by the time the bit is saved, but only becomes scrollable once the database flow
    // has delivered it. Clearing the id afterwards must not rerun this.
    LaunchedEffect(visibleBitsByDate) {
        val targetId = state.scrollToBitId ?: return@LaunchedEffect
        val targetIndex = visibleBitsByDate.indexOfBit(targetId)
        if (targetIndex != null) {
            // This effect runs with the composition that brought the bit in, and whether the
            // measure pass that first places it has already happened by then is not something to
            // rely on — it is a matter of how the platform dispatches the frame. Letting one pass
            // makes the layout the question below is put to describe a list that holds the bit.
            withFrameNanos { }
            if (!listState.isFullyVisible(targetId, topBarInset)) {
                // A negative offset lands the bit a third down the viewport, clear of the floating
                // top bar and the pinned sticky day header.
                listState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = -listState.layoutInfo.viewportSize.height / 3
                )
            }
        }
        // Cleared even when the bit isn't in the day-filtered list and there is nothing to scroll
        // to: a request left standing would never be satisfied, and would block the anchor below
        // for the rest of the session.
        actions.onScrolledToBit()
    }
    // Anchor the list to its newest bit — the bottom, since it is laid out oldest-first — when the
    // bits first arrive and whenever the day filter changes.
    //
    // Deliberately not keyed on the bits themselves. A sync rewrites their sync bookkeeping
    // (ETags, dirty flags, modification stamps), which makes the list unequal to the previous one
    // without changing a thing the reader can see; re-anchoring on that yanked the list to the
    // bottom mid-sync, out from under someone reading further up.
    LaunchedEffect(state.filterDate, visibleBitsByDate.isEmpty()) {
        if (visibleBitsByDate.isEmpty() || state.scrollToBitId != null) return@LaunchedEffect
        listState.scrollToItem(visibleBitsByDate.lastListIndex())
    }
    if (visibleBitsByDate.isEmpty()) {
        // Nothing to scroll here, so the inset the list would carry as content padding goes on
        // the column itself.
        Column(modifier.padding(contentPadding)) {
            // Nothing here scrolls, so the space this reserves comes straight off the prompt
            // below it — which on a landscape window with the keyboard open is the difference
            // between the prompt fitting and being squeezed. So it follows the bar out of the
            // way, the same way the sidebar's does.
            AnimatedVisibility(isTopBarVisible) {
                Spacer(Modifier.height(TopAppBarDefaults.TopAppBarExpandedHeight))
            }
            PermissionPromptItem(permissionRationale, actions)
            EmptyState(
                isFilteredByDate = state.filterDate != null &&
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
            contentPadding = contentPadding,
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

/**
 * Whether the item keyed [key] is in view *in full* — not merely laid out somewhere in the viewport,
 * but clear of the bottom edge and of the [topInset] the floating top bar covers.
 *
 * Partial visibility is not enough for the one bit this is asked about, the one just written: added
 * directly below the last item in view it ends up peeking over the bottom edge by a few pixels,
 * which the list's own notion of visible counts as visible — and then it is left half-hidden under
 * the composer, in the one case the jump exists for.
 */
private fun LazyListState.isFullyVisible(key: Any, topInset: Int): Boolean {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
    return item.offset >= layoutInfo.viewportStartOffset + topInset &&
            item.offset + item.size <= layoutInfo.viewportEndOffset
}

// Items preceding the first day header: top-bar spacer, permission prompt, beginning note.
private const val LEADING_LIST_ITEMS = 3

/** Index of the list's last item — a day header plus its bits for every day, after the leading
 * items — i.e. where the newest bit sits. */
private fun List<DatedBits>.lastListIndex(): Int =
    sumOf { it.bits.size + 1 } + LEADING_LIST_ITEMS

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
                LocalDateTimeFormats.current.date(date),
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
        EmojiFallScene(
            emoji = "🌱",
            falling = SEEDLING_FALL,
            height = BEGINNING_SCENE_HEIGHT,
            circling = SEEDLING_ANIMALS
        )
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
    BoxWithConstraints(modifier) {
        // The tree gets the pane's height less what the message needs, and no more than it can use.
        // The message is the part that has to be readable, so on a phone in landscape with the
        // keyboard up — where there is barely room for the message alone — the tree gives way
        // entirely rather than pushing it off the screen.
        val sceneHeight = (maxHeight - EMPTY_MESSAGE_ROOM).coerceAtMost(EMPTY_SCENE_HEIGHT)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 16.dp)
        ) {
            if (sceneHeight >= EMPTY_SCENE_MIN_HEIGHT) {
                EmojiFallScene(
                    emoji = "🌲",
                    falling = TREE_FALL,
                    height = sceneHeight,
                    scampering = TREE_ANIMALS
                )
                Spacer(Modifier.height(8.dp))
            }
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
}

/** What comes down past the tree of the empty state, and past the seedling of the beginning note. */
private val TREE_FALL = listOf("🍂", "🍃", "🌰", "🍁")
private val SEEDLING_FALL = listOf("💧", "✨", "🍃")

/**
 * Who turns up, every half minute or so. What each of them does is decided by which list it is in:
 * the tree is tall enough to be climbed down, whereas nothing climbs down a seedling — so the ones
 * who come to see that go round it at ground level instead and off out the other side.
 */
private val TREE_ANIMALS = listOf("🐿️", "🐦")
private val SEEDLING_ANIMALS = listOf("🐞", "🐁")

/**
 * How tall the two scenes are. The beginning note's is a fixed item in a scrolling list, so it can
 * simply be as tall as it wants to be. The empty state's shares the pane with its message, so it is
 * a maximum: what the pane has beyond [EMPTY_MESSAGE_ROOM] — the two lines of the message, the space
 * above them and the padding around all of it — and nothing at all below
 * [EMPTY_SCENE_MIN_HEIGHT], where a tree would be too small to be worth the room it takes.
 */
private val BEGINNING_SCENE_HEIGHT = 120.dp
private val EMPTY_SCENE_HEIGHT = 200.dp
private val EMPTY_SCENE_MIN_HEIGHT = 72.dp
private val EMPTY_MESSAGE_ROOM = 88.dp
