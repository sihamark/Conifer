package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.bits_action_cancel
import conifer.shared.generated.resources.bits_action_set_date
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * Material's calendar, for days too far back to scroll to. Picking a day works like
 * [eu.heha.conifer.ui.BitsViewModel.pickDate]. Nothing is committed until "Set day" (or Enter); Esc
 * cancels — see [CalendarKeys].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayPickerDialog(
    /** The day to open on, which is the day the composer would stamp on a bit added now. */
    date: LocalDate,
    today: LocalDate,
    /** The oldest day there is writing on, or null while there is none — see [yearRangeBackTo]. */
    earliestDate: LocalDate?,
    /**
     * Which of the two halves to open on — the calendar or the typed field. Only the first press is
     * decided here; the dialog's own toggle switches from then on, as in [TimeOfDayPickerDialog].
     */
    initialDisplayMode: DisplayMode,
    onPickDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        // Both, so that a calendar opened on a day months back opens *at* that month rather than at
        // this one with the selection somewhere off screen.
        initialSelectedDateMillis = date.toUtcMillis(),
        initialDisplayedMonthMillis = date.toUtcMillis(),
        yearRange = remember(earliestDate, today) { yearRangeBackTo(earliestDate, today) },
        initialDisplayMode = initialDisplayMode,
        selectableDates = remember(today) { DaysUpTo(today) }
    )
    // Null while the typed field holds something that is not a day yet, and while a day was cleared
    // rather than replaced. There is nothing to commit then, and a button that silently did nothing
    // would be worse than one that says so.
    val picked = state.selectedDateMillis?.let(::dateOfUtcMillis)
    val commit: () -> Unit = {
        picked?.let(onPickDate)
        onDismiss()
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.bits_action_cancel))
            }
        },
        confirmButton = {
            TextButton(enabled = picked != null, onClick = commit) {
                Text(stringResource(Res.string.bits_action_set_date))
            }
        }
    ) {
        CalendarKeys(
            isDayPicked = picked != null,
            displayMode = state.displayMode,
            onCommit = commit,
            onDismiss = onDismiss
        ) {
            DatePicker(state)
        }
    }
}

/**
 * Makes Enter mean "Set day" and Esc mean Cancel in the calendar dialog. The typed field would
 * swallow Enter, so there it is taken on the preview pass; in the grid on the normal pass, so a day
 * or button tabbed to keeps its own Enter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarKeys(
    isDayPicked: Boolean,
    displayMode: DisplayMode,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val isTyping = displayMode == DisplayMode.Input
    val focusRequester = remember { FocusRequester() }
    val isDown = { event: KeyEvent -> event.type == KeyEventType.KeyDown }
    // Nothing to commit while the field holds something that is not a day, exactly as the button is
    // disabled then: a key that closed the calendar on nothing would throw away the day it opened
    // on.
    val setsTheDay = { event: KeyEvent ->
        isDayPicked && isDown(event) && (event.key == Key.Enter || event.key == Key.NumPadEnter)
    }
    Box(
        Modifier
            .focusRequester(focusRequester)
            .focusTarget()
            .onPreviewKeyEvent { event ->
                if (isDown(event) && event.key == Key.Escape) {
                    onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (!isTyping || !setsTheDay(event)) return@onPreviewKeyEvent false
                onCommit()
                true
            }
            .onKeyEvent { event ->
                if (isTyping || !setsTheDay(event)) return@onKeyEvent false
                onCommit()
                true
            }
    ) {
        content()
    }
    LaunchedEffect(isTyping) {
        if (!isTyping) focusRequester.requestFocus()
    }
}

/**
 * The days the calendar will hand back: everything up to and including today.
 *
 * A bit is a note about something that happened, so a day that has not happened is not one to file
 * it under — the day lists have never offered one either, and the date hotkeys clamp to today
 * ([BitsPaneState.dateShiftedBy]). Barred rather than hidden, because Material draws the rest of the
 * month either way, and a greyed-out day says "not this one" where a missing day says nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal class DaysUpTo(private val today: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        dateOfUtcMillis(utcTimeMillis) <= today

    override fun isSelectableYear(year: Int): Boolean = year <= today.year
}

/**
 * The years the calendar's year grid offers: back to the oldest day there is writing on, and never
 * fewer than [SLACK_YEARS] past it.
 *
 * The writing is the honest end of it — there is nothing to look at before the first bit — but the
 * calendar is also how one backdates, and a bit worth backdating to a holiday two years ago is
 * exactly the kind that gets written on a fresh install. So the range reaches past the writing
 * rather than stopping at it, and Material's own 1900..2100 is left alone: a grid of two centuries
 * is a worse way to 2024 than the arrows beside it.
 */
internal fun yearRangeBackTo(earliestDate: LocalDate?, today: LocalDate): IntRange {
    val earliestYear = minOf(earliestDate?.year ?: today.year, today.year - SLACK_YEARS)
    return earliestYear..today.year
}

/** How far past the oldest writing the year grid still reaches — see [yearRangeBackTo]. */
private const val SLACK_YEARS = 5

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

/**
 * The day as Material's calendar counts: milliseconds from the epoch at UTC midnight.
 *
 * UTC throughout, and deliberately not the zone the bits are written in. What crosses this boundary
 * is a day on a calendar page rather than a moment, and
 * [androidx.compose.material3.DatePickerState] spells one as the midnight that begins it *at UTC*;
 * converting through the local zone would land the day before or after it for anyone east or west
 * of Greenwich. The time of day is the slider's business, and the composer puts the two together.
 */
internal fun LocalDate.toUtcMillis(): Long = toEpochDays() * MILLIS_PER_DAY

/**
 * The inverse of [toUtcMillis], flooring rather than truncating so that a day before 1970 — where
 * the millis are negative and truncation would round *towards* the epoch, landing a day late —
 * comes back as itself.
 */
internal fun dateOfUtcMillis(utcTimeMillis: Long): LocalDate =
    LocalDate.fromEpochDays(utcTimeMillis.floorDiv(MILLIS_PER_DAY))
