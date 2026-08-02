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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import conifer.shared.generated.resources.bits_action_show_date_picker
import conifer.shared.generated.resources.bits_content_edit_bit
import conifer.shared.generated.resources.bits_content_save_bit
import conifer.shared.generated.resources.bits_label_date_time_now
import conifer.shared.generated.resources.bits_label_new_bit
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.label
import eu.heha.conifer.ui.print
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Compact date & time control presented as a chip. Collapsed it shows the effective date and
 * time; a custom selection highlights the chip and a "back to now" text button clears it, so a
 * selection survives collapsing the picker. While editing a bit, a "cancel edit" text button is
 * offered instead. Expanding the chip reveals the day picker (unless the two-pane layout's sidebar
 * already owns the day, see [isDaySelectionVisible]) and a slim time slider.
 */
@Composable
internal fun DateTimeSelector(
    bitsByDate: List<DatedBits>,
    selectedDate: LocalDate?,
    selectedTime: LocalTime?,
    currentDate: LocalDate,
    currentTime: LocalTime,
    isEditing: Boolean,
    isDaySelectionVisible: Boolean,
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
                // Collapses rather than vanishing, so handing the day over to the sidebar (or
                // taking it back) doesn't make the open picker jump in height.
                AnimatedVisibility(isDaySelectionVisible) {
                    DaySelection(
                        bitsByDate = bitsByDate,
                        selectedDate = selectedDate,
                        currentDate = currentDate,
                        onClickDate = onClickDate
                    )
                }
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
            value = time.timeSlot.toFloat(),
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

private const val MINUTES_PER_STEP = 15

// Number of 15-minute slots in a day (96: 00:00 … 23:45).
private const val STEP_SLOTS = 24 * 60 / MINUTES_PER_STEP

// Highest slider value (slot 95 == 23:45).
private const val LAST_STEP_SLOT = STEP_SLOTS - 1

// Slider `steps` counts the marks strictly between the endpoints.
private const val INTERMEDIATE_STEPS = STEP_SLOTS - 2

/**
 * The slot this time falls in, rounding down — slot 0 is 00:00, [LAST_STEP_SLOT] is 23:45.
 *
 * The slider's thumb position and the ↑/↓ nudge both start here, so the two can never disagree
 * about which slot a given time is in.
 */
private val LocalTime.timeSlot: Int get() = (hour * 60 + minute) / MINUTES_PER_STEP

/**
 * Whether this time sits exactly on a slot, i.e. whether [timeSlot] rounds anything away. Seconds
 * don't count: the composer deals in whole minutes and shows nothing finer, so 12:00:30 is "on"
 * 12:00 as far as the user can tell.
 */
private val LocalTime.isOnTimeSlot: Boolean get() = (hour * 60 + minute) % MINUTES_PER_STEP == 0

/**
 * The time at [slot] — the inverse of [timeSlot], clamped to the day so out-of-range arithmetic
 * settles at 00:00 or 23:45 instead of throwing or wrapping.
 */
private fun timeAtSlot(slot: Int): LocalTime {
    val minutes = slot.coerceIn(0, LAST_STEP_SLOT) * MINUTES_PER_STEP
    return LocalTime(minutes / 60, minutes % 60)
}

/**
 * This time moved by [slots] slots — what ↑/↓ do while the text field is focused (see
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


@Composable
private fun DaySelection(
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
    /** The time the bit would get, i.e. what ↑/↓ nudge. See [BitsPaneState.effectiveTime]. */
    time: LocalTime,
    onNewBitTextChange: (String) -> Unit,
    onSelectTime: (LocalTime) -> Unit,
    onClickAdd: () -> Unit,
    focusRequester: FocusRequester,
    // Trimmed by the layouts that have to fit the whole composer into what a landscape keyboard
    // leaves over, where every dp below the field is one the field itself does not get.
    bottomPadding: Dp = 16.dp,
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
            singleLine = true,
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                // ↑/↓ nudge the time by one slider slot without leaving the text field, so a bit
                // can be timed without reaching for the mouse. Previewed ahead of the field so it
                // sees them first, and safe to take: the field is single-line, so up and down have
                // nothing to do in it anyway - ←/→ keep moving the caret as usual.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val slots = when (event.key) {
                        Key.DirectionUp -> 1
                        Key.DirectionDown -> -1
                        else -> return@onPreviewKeyEvent false
                    }
                    onSelectTime(time.shiftedByTimeSlots(slots))
                    true
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
