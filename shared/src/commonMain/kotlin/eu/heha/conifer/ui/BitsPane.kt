package eu.heha.conifer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.app_name
import conifer.shared.generated.resources.bits_action_back_to_now
import conifer.shared.generated.resources.bits_action_bit_options
import conifer.shared.generated.resources.bits_action_cancel
import conifer.shared.generated.resources.bits_action_cancel_edit
import conifer.shared.generated.resources.bits_action_delete
import conifer.shared.generated.resources.bits_action_grant_permission
import conifer.shared.generated.resources.bits_action_hide_date_picker
import conifer.shared.generated.resources.bits_action_menu_cancel_edit
import conifer.shared.generated.resources.bits_action_menu_edit
import conifer.shared.generated.resources.bits_action_show_date_picker
import conifer.shared.generated.resources.bits_content_copy_date_to_clipboard
import conifer.shared.generated.resources.bits_content_edit_bit
import conifer.shared.generated.resources.bits_content_save_bit
import conifer.shared.generated.resources.bits_label_counter
import conifer.shared.generated.resources.bits_label_date_time_now
import conifer.shared.generated.resources.bits_label_new_bit
import conifer.shared.generated.resources.bits_label_today
import conifer.shared.generated.resources.bits_message_beginning
import conifer.shared.generated.resources.bits_message_delete_bit
import conifer.shared.generated.resources.bits_message_empty
import conifer.shared.generated.resources.bits_message_empty_filtered
import conifer.shared.generated.resources.bits_title_delete_bit
import eu.heha.conifer.PermissionRationale
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.theme.ConiferTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitsPane(
    state: BitsPaneState = BitsPaneState(),
    actions: BitsPaneActions = BitsPaneActions()
) {
    Scaffold(contentWindowInsets = WindowInsets()) { innerPadding ->
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(state.newBitText) {
            if (state.newBitText.isBlank()) focusRequester.requestFocus()
        }
        LaunchedEffect(state.editingBitId) {
            if (state.editingBitId != null) focusRequester.requestFocus()
        }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                // The status bar is always padded by the column itself so showing/hiding the top
                // bar only animates the bar's own height and nothing else jumps.
                .statusBarsPadding()
        ) {
            // The state always holds all days; a selected date only filters what the list (and
            // the counter) shows, so the day chips keep their indicators while filtering.
            val visibleBitsByDate = state.selectedDate?.let { selected ->
                state.bitsByDate.filter { it.date == selected }
            } ?: state.bitsByDate
            val permissionRationale = state.permissionRationale
            // The list and the top bar share a Box so the bar floats above the list instead of
            // pushing it down (and the bottom bits under the input) when it reappears. The list
            // may start underneath the bar, but its first item is the BeginningNote whose top
            // padding keeps the text clear of the bar.
            Box(Modifier.weight(1f)) {
                // The DAO delivers bits newest-first; presenting everything reversed puts the
                // newest bits at the bottom next to the input while sticky day headers still pin
                // to the top.
                val listState = rememberLazyListState()
                // Keyed on the list only: it runs when the database flow delivers the updated
                // bits, at which point a pending scrollToBitId (set right after saving) is
                // already in the state. Clearing the id afterwards must not rerun the effect,
                // or it would jump to the bottom right after scrolling to the bit.
                LaunchedEffect(visibleBitsByDate) {
                    if (visibleBitsByDate.isEmpty()) return@LaunchedEffect
                    val targetId = state.scrollToBitId
                    val targetIndex = targetId?.let { visibleBitsByDate.indexOfBit(it) }
                    if (targetIndex != null) {
                        val isAlreadyVisible = listState.layoutInfo.visibleItemsInfo
                            .any { it.key == targetId }
                        if (!isAlreadyVisible) {
                            // A negative offset lands the bit a third down the viewport, clear
                            // of the floating top bar and the pinned sticky day header.
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
                val listModifier = Modifier.fillMaxSize()
                if (visibleBitsByDate.isEmpty()) {
                    Column(listModifier) {
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
                        // Bottom arrangement keeps the bits anchored to the input when the list
                        // is shorter than the viewport; it has no effect once the list fills the
                        // screen.
                        verticalArrangement = Arrangement.Bottom,
                        modifier = listModifier
                    ) {
                        // Makes up for the height of the floating top bar so the top of the
                        // list can scroll fully out from underneath it.
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
                // Hidden while the IME is open, since the keyboard already claims a large share
                // of the screen.
                val bitsCount = visibleBitsByDate.sumOf { it.bits.size }
                Topbar(bitsCount)
            }
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    // Padding the navigation bar and IME inside the colored column extends its
                    // background behind the navigation bar instead of leaving a differently
                    // colored strip below the input.
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                DateTimeSelector(
                    bitsByDate = state.bitsByDate,
                    selectedDate = state.selectedDate,
                    selectedTime = state.selectedTime,
                    currentDate = state.today,
                    currentTime = state.currentTime,
                    isEditing = state.editingBitId != null,
                    onClickDate = actions.onClickDate,
                    onSelectTime = actions.onSelectTime,
                    onResetToNow = actions.onResetToNow,
                    onCancelEdit = actions.onCancelEdit
                )
                NewBitText(
                    newBitText = state.newBitText,
                    isEditing = state.editingBitId != null,
                    onNewBitTextChange = actions.onNewBitTextChange,
                    onClickAdd = actions.onClickAdd,
                    focusRequester = focusRequester
                )
            }
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
private fun Topbar(bitsCount: Int) {
    // While typing, the IME already claims a large share of the screen, so the top bar is hidden
    // to leave as much room as possible for reading existing bits.
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    AnimatedVisibility(visible = !isImeVisible, modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(stringResource(Res.string.app_name)) },
            actions = {
                Text(
                    pluralStringResource(
                        Res.plurals.bits_label_counter,
                        bitsCount,
                        bitsCount
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 16.dp)
                )
            },
            // The column already handles the status bar inset.
            windowInsets = WindowInsets()
        )
    }
}

/**
 * Compact date & time control presented as a chip. Collapsed it shows the effective date and
 * time; a custom selection highlights the chip and a "back to now" text button clears it, so a
 * selection survives collapsing the picker. While editing a bit, a "cancel edit" text button is
 * offered instead. Expanding the chip reveals the day picker and a slim time slider.
 */
@Composable
private fun DateTimeSelector(
    bitsByDate: List<DatedBits>,
    selectedDate: LocalDate?,
    selectedTime: LocalTime?,
    currentDate: LocalDate,
    currentTime: LocalTime,
    isEditing: Boolean,
    onClickDate: (LocalDate) -> Unit,
    onSelectTime: (LocalTime) -> Unit,
    onResetToNow: () -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val hasCustomSelection = selectedDate != null || selectedTime != null
    val effectiveDate = selectedDate ?: currentDate
    val effectiveTime = selectedTime ?: currentTime

    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                onClick = { isExpanded = !isExpanded },
                shape = MaterialTheme.shapes.extraLarge,
                color = if (hasCustomSelection) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (hasCustomSelection) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                border = BorderStroke(
                    1.dp,
                    if (hasCustomSelection) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    val day = if (hasCustomSelection) {
                        effectiveDate.label(currentDate)
                    } else {
                        stringResource(Res.string.bits_label_date_time_now)
                    }
                    val label = day + " · " + effectiveTime.print()
                    Text(text = label, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f,
                        label = "chevronRotation"
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) {
                            stringResource(Res.string.bits_action_hide_date_picker)
                        } else {
                            stringResource(Res.string.bits_action_show_date_picker)
                        },
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation)
                    )
                }
            }
            AnimatedVisibility(hasCustomSelection && !isEditing) {
                TextButton(onClick = onResetToNow) {
                    Text(
                        stringResource(Res.string.bits_action_back_to_now),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            AnimatedVisibility(isEditing) {
                TextButton(onClick = onCancelEdit) {
                    Text(
                        stringResource(Res.string.bits_action_cancel_edit),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        AnimatedVisibility(isExpanded) {
            Column {
                DaySelection(
                    bitsByDate = bitsByDate,
                    selectedDate = selectedDate,
                    currentDate = currentDate,
                    onClickDate = onClickDate
                )
                TimeSlider(
                    time = effectiveTime,
                    onSelectTime = onSelectTime,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun TimeSlider(
    time: LocalTime,
    onSelectTime: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = time.print(),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        // The slider works in 15-minute slots (0 = 00:00 … LAST = 23:45) so the discrete
        // steps line up exactly and draw a tick mark at every quarter hour.
        val interactionSource = remember { MutableInteractionSource() }
        Slider(
            value = ((time.hour * 60 + time.minute) / MINUTES_PER_STEP).toFloat(),
            onValueChange = { slotIndex ->
                val minutes = slotIndex.roundToInt() * MINUTES_PER_STEP
                onSelectTime(LocalTime(minutes / 60, minutes % 60))
            },
            interactionSource = interactionSource,
            valueRange = 0f..LAST_STEP_SLOT.toFloat(),
            steps = INTERMEDIATE_STEPS,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource,
                    thumbSize = DpSize(8.dp, 24.dp)
                )
            },
            modifier = Modifier.weight(1f)
        )
    }
}

private const val MINUTES_PER_STEP = 15

// Number of 15-minute slots in a day (96: 00:00 … 23:45).
private const val STEP_SLOTS = 24 * 60 / MINUTES_PER_STEP

// Highest slider value (slot 95 == 23:45).
private const val LAST_STEP_SLOT = STEP_SLOTS - 1

// Slider `steps` counts the marks strictly between the endpoints.
private const val INTERMEDIATE_STEPS = STEP_SLOTS - 2

@Composable
private fun LocalDate.label(today: LocalDate): String =
    if (this == today) stringResource(Res.string.bits_label_today) else print()

@Composable
fun DaySelection(
    bitsByDate: List<DatedBits>,
    selectedDate: LocalDate?,
    currentDate: LocalDate,
    onClickDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    // The clickable Surface would otherwise enforce the 48.dp minimum touch target height on
    // every chip; lifting the enforcement lets the chips stay as flat as their content.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        LazyRow(
            reverseLayout = true,
            modifier = modifier
        ) {
            item { Spacer(Modifier.width(14.dp)) }
            items(30, key = { it }) { dayIndex ->
                val date = LocalDate.fromEpochDays(currentDate.toEpochDays() - dayIndex)
                val isSelected = date == selectedDate
                val isCurrent = date == currentDate
                val dots = bitsByDate.firstOrNull { it.date == date }?.dots ?: 0
                Surface(
                    onClick = { onClickDate(date) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ).takeIf { !isSelected },
                    modifier = Modifier.padding(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = date.dayOfWeek.name.take(3),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "${date.day}.${date.month.number}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        // dots indicate how much has been written in a day
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(dots) {
                                Box(
                                    Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary)
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.width(14.dp)) }
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

/** [PermissionPrompt] with its show/hide animation; nothing is shown without a rationale. */
@Composable
private fun PermissionPromptItem(
    permissionRationale: PermissionRationale?,
    actions: BitsPaneActions
) {
    AnimatedVisibility(permissionRationale != null) {
        if (permissionRationale != null) PermissionPrompt(permissionRationale, actions)
    }
}

/**
 * Compact banner asking for the notification permission, as in the mockup: a bell, the rationale
 * with its first sentence highlighted, and a filled pill button, framed by a dashed border.
 */
@Composable
private fun PermissionPrompt(
    permissionRationale: PermissionRationale,
    actions: BitsPaneActions
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = MaterialTheme.colorScheme.tertiary
    val leadColor = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 6.dp.toPx())
                        )
                    )
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text = "🔔", style = MaterialTheme.typography.titleLarge)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = leadColor)) {
                    append(permissionRationale.lead)
                }
                appendLine()
                append(permissionRationale.text)
            },
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        Button(onClick = actions.onClickRequestPermission) {
            Text(stringResource(Res.string.bits_action_grant_permission))
        }
    }
}

@Composable
private fun NewBitText(
    newBitText: String,
    isEditing: Boolean,
    onNewBitTextChange: (String) -> Unit,
    onClickAdd: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    // Track the selection locally; when the text is replaced from outside (an edit starts or the
    // field is cleared) place the cursor at the end instead of wherever it happened to be.
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(newBitText, TextRange(newBitText.length)))
    }
    if (textFieldValue.text != newBitText) {
        textFieldValue = TextFieldValue(newBitText, TextRange(newBitText.length))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { value ->
                textFieldValue = value
                if (value.text != newBitText) onNewBitTextChange(value.text)
            },
            label = {
                Text(
                    stringResource(
                        if (isEditing) {
                            Res.string.bits_content_edit_bit
                        } else {
                            Res.string.bits_label_new_bit
                        }
                    )
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions { onClickAdd() },
            singleLine = true,
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .weight(1f)
        )
        AnimatedVisibility(newBitText.isNotBlank()) {
            Row {
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = onClickAdd) {
                    val icon = if (isEditing) Icons.Default.Edit else Icons.Default.Check
                    val contentDescriptionRes = if (isEditing) {
                        Res.string.bits_content_save_bit
                    } else {
                        Res.string.bits_content_edit_bit
                    }
                    Icon(
                        icon,
                        contentDescription = stringResource(contentDescriptionRes)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BitItem(
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
                        bit.date.time.print(),
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


data class BitsPaneState(
    val permissionRationale: PermissionRationale? = null,
    val isCopyPossible: Boolean = true,
    val newBitText: String = "",
    val selectedDate: LocalDate? = null,
    val selectedTime: LocalTime? = null,
    val today: LocalDate = now().date,
    val currentTime: LocalTime = now().time,
    val bitsByDate: List<DatedBits> = emptyList(),
    val editingBitId: String? = null,
    /** One-shot request to scroll the list to this bit after it was added or edited. */
    val scrollToBitId: String? = null
)

data class DatedBits(
    val date: LocalDate,
    val bits: List<Bit>
) {
    /**
     * Dots shown on the day chip: one for any bit, two when both the morning (before 12:00) and
     * the afternoon have one, three when on top of that the day holds more than three bits.
     */
    val dots: Int = run {
        val hasMorningBit = bits.any { it.date.hour < 12 }
        val hasAfternoonBit = bits.any { it.date.hour >= 12 }
        when {
            hasMorningBit && hasAfternoonBit && bits.size > 3 -> 3
            hasMorningBit && hasAfternoonBit -> 2
            else -> 1
        }
    }
}

class BitsPaneActions(
    val onClickAdd: () -> Unit = {},
    val onNewBitTextChange: (String) -> Unit = {},
    val onClickRequestPermission: () -> Unit = {},
    val onClickDate: (LocalDate) -> Unit = {},
    val onSelectTime: (LocalTime) -> Unit = {},
    val onResetToNow: () -> Unit = {},
    val onClickCopyBitsOfDateToClipboard: (LocalDate) -> Unit = {},
    val onClickEditBit: (Bit) -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val onDeleteBit: (Bit) -> Unit = {},
    val onScrolledToBit: () -> Unit = {}
)

@PreviewLightDark
@Composable
private fun BitsPanePreview() {
    ConiferTheme {
        BitsPane(
            state = BitsPaneState(
                newBitText = "This is a new bit",
                permissionRationale = PermissionRationale(
                    "Add Bits from anywhere.",
                    "Allow notifications and reply to the Conifer conversation to capture a bit without opening the app."
                ),
                editingBitId = "1",
                selectedTime = LocalTime(1, 0, 0),
                bitsByDate = listOf(
                    DatedBits(
                        date = LocalDate(2024, 6, 1),
                        bits = listOf(
                            Bit(id = "1", text = "First bit"),
                            Bit(text = "Second bit"),
                            Bit(text = (0..10).joinToString { "This is a new bit" })
                        )
                    )
                )
            )
        )
    }
}
