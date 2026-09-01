package eu.heha.conifer.ui.bits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.bits_action_back_to_now
import conifer.shared.generated.resources.bits_action_cancel_edit
import conifer.shared.generated.resources.bits_action_hide_date_picker
import conifer.shared.generated.resources.bits_action_show_calendar
import conifer.shared.generated.resources.bits_action_show_date_picker
import conifer.shared.generated.resources.bits_action_show_time_picker
import conifer.shared.generated.resources.bits_content_edit_bit
import conifer.shared.generated.resources.bits_content_save_bit
import conifer.shared.generated.resources.bits_label_date_time_now
import conifer.shared.generated.resources.bits_label_new_bit
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.LocalDateTimeFormats
import eu.heha.conifer.ui.label
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Compact date & time control presented as a chip. Collapsed it shows the date and time the bit
 * being written would get; a custom selection highlights the chip and a "back to now" text button
 * clears it, so a selection survives collapsing the picker. While editing a bit, a "cancel edit"
 * text button is offered instead. Expanding the chip reveals the day picker (unless the two-pane
 * layout's sidebar already owns the day, see [isDaySelectionVisible]) and a slim time slider, whose
 * own chip opens the time picker for a time the slider's quarter hours cannot make.
 */
@Composable
internal fun DateTimeSelector(
    bitsByDate: List<DatedBits>,
    composerDate: LocalDate?,
    composerTime: LocalTime?,
    /**
     * The day the list is filtered to, which is what the day strip highlights — it is a day list
     * like the sidebar, so it marks the day being looked at, while the chip beside it shows the day
     * being written to. The two only differ while a bit from another day is being edited.
     */
    filterDate: LocalDate?,
    currentDate: LocalDate,
    currentTime: LocalTime,
    isEditing: Boolean,
    isDaySelectionVisible: Boolean,
    /** Only decides which half of the time picker opens first — see [TimeChip]. */
    isKeyboardPresent: Boolean,
    /**
     * Whether the picker below the chip is open. Hoisted rather than kept here so that Esc can close
     * it before it reaches anything else — it is drawn inside the screen, so unlike a dialog it is
     * the screen's own to get out of (see [handleShortcut]).
     */
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClickDate: (LocalDate) -> Unit,
    /**
     * Opens the calendar ([DayPickerDialog]), which [BitsPane] owns because the two-pane layout's
     * sidebar offers the same way in.
     */
    onClickCalendar: () -> Unit,
    onSelectTime: (LocalTime) -> Unit,
    onResetToNow: () -> Unit,
    onCancelEdit: () -> Unit,
    /** How many days the strip reaches back, and how it asks for more — see [DaySelection]. */
    dayCount: Int = DAY_LIST_PAGE,
    onLoadOlderDays: () -> Unit = {},
    /** Bumped to send the strip back to today — see [ScrollBackToTodayWhenAsked]. */
    scrollHomeRequest: Int = 0,
    modifier: Modifier = Modifier
) {
    val hasCustomSelection = composerDate != null || composerTime != null
    val effectiveDate = composerDate ?: currentDate
    val effectiveTime = composerTime ?: currentTime

    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                onClick = { onExpandedChange(!isExpanded) },
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
                    val label = day + " · " + LocalDateTimeFormats.current.timeOfDay(effectiveTime)
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
            // The way to a day the strip would take a long drag to reach. It keeps the full 48.dp
            // touch target that the chips beside it give up (see [TimeChip]): those are one of a
            // row of many, where a miss costs a neighbouring day and a second tap, while this is
            // the only way in to the calendar — and it is the row's height either way, since the
            // "back to now" button next to it already stands 40.dp tall.
            IconButton(onClick = onClickCalendar) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = stringResource(Res.string.bits_action_show_calendar),
                    // Sized to the chip's own icons rather than the button's default 24.dp, so the
                    // two read as one row of controls.
                    modifier = Modifier.size(20.dp)
                )
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
                // Collapses rather than vanishing, so handing the day over to the sidebar (or
                // taking it back) doesn't make the open picker jump in height.
                AnimatedVisibility(isDaySelectionVisible) {
                    DaySelection(
                        bitsByDate = bitsByDate,
                        selectedDate = filterDate,
                        currentDate = currentDate,
                        onClickDate = onClickDate,
                        dayCount = dayCount,
                        onLoadOlderDays = onLoadOlderDays,
                        scrollHomeRequest = scrollHomeRequest
                    )
                }
                TimeSlider(
                    time = effectiveTime,
                    isKeyboardPresent = isKeyboardPresent,
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
    isKeyboardPresent: Boolean,
    onSelectTime: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        TimeChip(
            time = time,
            isKeyboardPresent = isKeyboardPresent,
            onSelectTime = onSelectTime
        )
        Spacer(Modifier.width(12.dp))
        // The slider works in 15-minute slots (0 = 00:00 … LAST = 23:45) so the discrete
        // steps line up exactly and draw a tick mark at every quarter hour.
        val interactionSource = remember { MutableInteractionSource() }
        Slider(
            // The unrounded position rather than the slot, so a time the picker made between two
            // ticks — 12:07 — comes to rest on the nearer of them; see [sliderPosition].
            value = time.sliderPosition,
            onValueChange = { slot -> onSelectTime(timeAtSlot(slot.roundToInt())) },
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

/**
 * The time the bit would be stamped with, and the way to an exact one: it reads as the slider's
 * value, and pressing it opens the [TimeOfDayPickerDialog] for the times the quarter-hour slots
 * cannot make. The clock icon is what says it can be pressed at all — a bare number beside a slider
 * reads as a display.
 */
@Composable
private fun TimeChip(
    time: LocalTime,
    isKeyboardPresent: Boolean,
    onSelectTime: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPickerOpen by remember { mutableStateOf(false) }
    // As in [DaySelection]: the minimum touch target would otherwise make this chip taller than the
    // slider it sits beside, and the composer's height is the one thing the landscape layout has
    // none of to spare. It leaves the chip under the 48.dp a tap target is meant to be — knowingly,
    // for now: the height is scarce on exactly the windows where the target matters most, so the
    // way out is to spend it where there is some, not to spend it everywhere.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = { isPickerOpen = true },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = stringResource(Res.string.bits_action_show_time_picker),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = LocalDateTimeFormats.current.timeOfDay(time),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    if (isPickerOpen) {
        TimeOfDayPickerDialog(
            time = time,
            // Where a keyboard is the way in, the dial is the long way round to a time that can
            // simply be typed; where it is a finger, it is the other way about. The dialog's own
            // toggle switches either way, so the guess only decides which half is one press cheaper.
            initialDisplayMode = if (isKeyboardPresent) {
                TimePickerDisplayMode.Input
            } else {
                TimePickerDisplayMode.Picker
            },
            onSelectTime = onSelectTime,
            onDismiss = { isPickerOpen = false }
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

/**
 * Minutes since midnight. Seconds are dropped rather than rounded: the composer deals in whole
 * minutes and shows nothing finer, so 12:00:30 counts as 12:00 everywhere below.
 */
private val LocalTime.minutesOfDay: Int get() = hour * 60 + minute

/**
 * The slot this time falls in, rounding down — slot 0 is 00:00, [LAST_STEP_SLOT] is 23:45.
 *
 * What the Alt+↑/↓ nudge steps from, and what the slider hands back as it is dragged, so the two
 * can never disagree about which slot a given time is in.
 */
private val LocalTime.timeSlot: Int get() = minutesOfDay / MINUTES_PER_STEP

/**
 * Where the slider's thumb sits for this time, in slots — [timeSlot] without its rounding, which
 * the slider then does its own way: it snaps whatever value it is handed to the *nearest* tick, so
 * 12:12 rests on 12:15 rather than on the 12:00 [timeSlot] would round it down to. The thumb never
 * comes to rest between two ticks; the chip beside it is what spells the time out exactly.
 *
 * Capped at the last slot so the final quarter hour (23:45 … 23:59) stays a position on the track
 * rather than one past its end.
 */
internal val LocalTime.sliderPosition: Float
    get() = (minutesOfDay / MINUTES_PER_STEP.toFloat()).coerceAtMost(LAST_STEP_SLOT.toFloat())

/** Whether this time sits exactly on a slot, i.e. whether [timeSlot] rounds anything away. */
private val LocalTime.isOnTimeSlot: Boolean get() = minutesOfDay % MINUTES_PER_STEP == 0

/**
 * The time at [slot] — the inverse of [timeSlot], clamped to the day so out-of-range arithmetic
 * settles at 00:00 or 23:45 instead of throwing or wrapping.
 */
private fun timeAtSlot(slot: Int): LocalTime {
    val minutes = slot.coerceIn(0, LAST_STEP_SLOT) * MINUTES_PER_STEP
    return LocalTime(minutes / 60, minutes % 60)
}

/**
 * This time moved by [slots] slots — what Alt+↑/↓ do while the text field is focused (see
 * [NewBitText]).
 *
 * Clamped rather than rolling over, by [timeAtSlot]: the day is a separate choice, and holding an
 * arrow key long enough to silently move the bit to another day would be a nasty surprise.
 *
 * A time that isn't on the grid — the clock's "now", most of the time — snaps onto it in the
 * direction pressed first, so the first nudge never jumps *past* a slot. [timeSlot] already
 * rounds down, which is what going up wants; going down has to round up first. From 12:07, ↑
 * gives 12:15 and ↓ gives 12:00.
 */
internal fun LocalTime.shiftedByTimeSlots(slots: Int): LocalTime =
    timeAtSlot(if (slots < 0 && !isOnTimeSlot) timeSlot + 1 + slots else timeSlot + slots)


/**
 * The composer's day list: a strip of day chips running back from today, newest at the right. It
 * reaches [dayCount] days back and asks for another page as it is scrolled towards the oldest of
 * them ([LoadOlderDaysWhenNearTheOldest]) — dragging it leftwards goes on into the past for as
 * long as one keeps dragging, and Esc or the key for today brings it back
 * ([ScrollBackToTodayWhenAsked]).
 */
@Composable
private fun DaySelection(
    bitsByDate: List<DatedBits>,
    selectedDate: LocalDate?,
    currentDate: LocalDate,
    onClickDate: (LocalDate) -> Unit,
    dayCount: Int = DAY_LIST_PAGE,
    onLoadOlderDays: () -> Unit = {},
    scrollHomeRequest: Int = 0,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LoadOlderDaysWhenNearTheOldest(listState, onLoadOlderDays)
    ScrollBackToTodayWhenAsked(listState, scrollHomeRequest)
    // As in the sidebar: one lookup per chip, of which there are as many as have been scrolled to.
    val bitsOfDate = remember(bitsByDate) { bitsByDate.associateBy { it.date } }
    // The clickable Surface would otherwise enforce the 48.dp minimum touch target height on
    // every chip; lifting the enforcement lets the chips stay as flat as their content.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        LazyRow(
            state = listState,
            reverseLayout = true,
            modifier = modifier
        ) {
            item { Spacer(Modifier.width(14.dp)) }
            items(dayCount, key = { it }) { dayIndex ->
                val formats = LocalDateTimeFormats.current
                val date = LocalDate.fromEpochDays(currentDate.toEpochDays() - dayIndex)
                val isSelected = date == selectedDate
                val isCurrent = date == currentDate
                val dots = bitsOfDate[date]?.dots ?: 0
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
                            text = formats.weekdayShort(date),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = formats.dayAndMonth(date),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        DayDots(dots)
                    }
                }
            }
            item { Spacer(Modifier.width(14.dp)) }
        }
    }
}


@Composable
internal fun NewBitText(
    newBitText: String,
    isEditing: Boolean,
    onNewBitTextChange: (String) -> Unit,
    onClickAdd: () -> Unit,
    focusRequester: FocusRequester,
    // Trimmed by the layouts that have to fit the whole composer into what a landscape keyboard
    // leaves over, where every dp below the field is one the field itself does not get.
    bottomPadding: Dp = 16.dp,
    /** How far the field may grow before it scrolls instead. See [currentComposerMaxLines]. */
    maxLines: Int = 1,
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
            .padding(bottom = bottomPadding)
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
            // Long bits wrap into the room the window has to spare instead of scrolling sideways
            // through a one-line slot; see [currentComposerMaxLines] for how far that goes.
            //
            // Never a single-line field, not even at one line: it would scroll sideways where a
            // taller one scrolls down, and the field saves that direction with the scroller it
            // kept. Turning the screen with the keyboard up restores a one-line field into a
            // two-line budget, and a scroller restored the wrong way round throws. At one line the
            // two look the same anyway — same height, same place for the text.
            singleLine = false,
            maxLines = maxLines,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                // Enter submits without leaving the field, so a bit can be written and saved without
                // reaching for the mouse; previewed ahead of the field so it is seen before the
                // field turns it into a line break. The rest of the shortcuts — the time nudge and
                // the day keys — are the screen's rather than the field's and live on the pane
                // (`handleShortcut`), which sees them before this does either way.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        // A field that can hold several lines would otherwise swallow Enter as a
                        // line break. Shift+Enter still makes one, for the rare bit that wants it.
                        Key.Enter, Key.NumPadEnter -> {
                            if (event.isShiftPressed) return@onPreviewKeyEvent false
                            onClickAdd()
                            true
                        }

                        else -> false
                    }
                }
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

