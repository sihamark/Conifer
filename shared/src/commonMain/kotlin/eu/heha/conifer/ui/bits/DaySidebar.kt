package eu.heha.conifer.ui.bits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.bits_action_all_days
import conifer.shared.generated.resources.bits_action_show_calendar
import conifer.shared.generated.resources.bits_label_days
import conifer.shared.generated.resources.bits_label_today
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.LocalDateTimeFormats
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * The two-pane layout's day list: "All days" followed by the same days the day strip offers, each
 * with the strip's dots and the number of bits written that day. It replaces the day strip inside
 * the composer's picker, so selecting a day here filters the list *and* dates the next bit,
 * exactly as the strip does — tapping the selected day again (or "All days") lifts the filter.
 *
 * It reaches [dayCount] days back and asks for more as it is scrolled towards the oldest of them
 * ([LoadOlderDaysWhenNearTheOldest]), so scrolling into the past simply goes on — and comes back to
 * today when asked ([ScrollBackToTodayWhenAsked]).
 *
 * [selectedDate] is the day the list is filtered to ([BitsPaneState.filterDate]), not the day the
 * composer is set to write on: both day lists mark the day being looked at, which is all they
 * decide.
 */
@Composable
internal fun DaySidebar(
    bitsByDate: List<DatedBits>,
    selectedDate: LocalDate?,
    currentDate: LocalDate,
    isTopBarVisible: Boolean,
    onClickDate: (LocalDate) -> Unit,
    onClickAllDays: () -> Unit,
    /** Opens the calendar ([DayPickerDialog]), which [BitsPane] owns — the composer opens the same one. */
    onClickCalendar: () -> Unit = {},
    dayCount: Int = DAY_LIST_PAGE,
    onLoadOlderDays: () -> Unit = {},
    scrollHomeRequest: Int = 0,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LoadOlderDaysWhenNearTheOldest(listState, onLoadOlderDays)
    ScrollBackToTodayWhenAsked(listState, scrollHomeRequest)
    // A day is looked up once per row and there are as many rows as have been scrolled to, so the
    // days are indexed rather than searched through for each of them.
    val bitsOfDate = remember(bitsByDate) { bitsByDate.associateBy { it.date } }
    Column(modifier.width(SIDEBAR_WIDTH)) {
        // Lines the sidebar's content up with the list's, below the top bar floating over the main
        // pane; it follows the bar out of the way while the IME is open.
        AnimatedVisibility(isTopBarVisible) {
            Spacer(Modifier.height(TopAppBarDefaults.TopAppBarExpandedHeight))
        }
        // The days' own heading, and beside it the way to a day too far back to scroll to. The
        // composer's chip row carries the same button in every layout; this one is here because in
        // this layout the days are the sidebar's, and reaching for the composer to move the list
        // beside it would be the long way round.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 18.dp, end = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = stringResource(Res.string.bits_label_days),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClickCalendar) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = stringResource(Res.string.bits_action_show_calendar),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // As in DaySelection, the items are as flat as their content instead of being stretched to
        // the minimum touch target height, so a useful number of days fits without scrolling.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                item(key = "all-days") {
                    DaySidebarItem(
                        weekday = "∗",
                        label = stringResource(Res.string.bits_action_all_days),
                        // Unlike a day's, the total is shown even when there is nothing to count.
                        count = bitsByDate.sumOf { it.bits.size },
                        // Dots describe a single day, so the total row doesn't carry them.
                        dots = 0,
                        isSelected = selectedDate == null,
                        isCurrentDate = false,
                        onClick = onClickAllDays
                    )
                }
                items(dayCount, key = { it }) { dayIndex ->
                    val formats = LocalDateTimeFormats.current
                    val date = LocalDate.fromEpochDays(currentDate.toEpochDays() - dayIndex)
                    val datedBits = bitsOfDate[date]
                    DaySidebarItem(
                        weekday = formats.weekdayShort(date),
                        label = formats.dayAndMonth(date),
                        count = datedBits?.bits?.size,
                        dots = datedBits?.dots ?: 0,
                        isSelected = date == selectedDate,
                        isCurrentDate = date == currentDate,
                        onClick = { onClickDate(date) }
                    )
                }
            }
        }
    }
}

/**
 * A single row of the [DaySidebar]: weekday, day, the day strip's dots, and a badge counting that
 * day's bits. The dots and the count answer different questions — how the day's bits are spread
 * over it versus how many there are — so a day carries both.
 */
@Composable
private fun DaySidebarItem(
    weekday: String,
    label: String,
    count: Int?,
    dots: Int,
    isSelected: Boolean,
    isCurrentDate: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = weekday,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                // A fixed column, so that the dates line up down the sidebar rather than each
                // starting wherever its own weekday happened to end — and fixed means something
                // has to give when the weekday is longer than it. Wrapping is what it did before,
                // which cost the row its second line and the sidebar its rhythm.
                //
                // Longer than it sounds: the platforms hand back whatever their locale calls the
                // short weekday, and for a good number of them that is the whole word — `sábado`
                // in Portuguese as spoken in Portugal, `Jumamosi` in Swahili, against `Sat`, `Sa.`
                // and `sam.` elsewhere. Shortened, those still read as the day they name; wrapped,
                // they took every row in the sidebar with them.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) {
                    LocalContentColor.current.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.width(34.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = label, style = MaterialTheme.typography.bodySmall)
                if (isCurrentDate) {
                    Text(
                        text = stringResource(Res.string.bits_label_today),
                        style = MaterialTheme.typography.labelSmall,
                        // The row is a single line, and this is what would otherwise break it: the
                        // marker takes what the date leaves of a fixed-width sidebar, which is not
                        // much, and text given too little room wraps rather than complain. Wrapped
                        // here it put its first word on a line of its own and dragged the row to
                        // twice the height of every other, the date left floating against it.
                        // Kept to one line, a marker that cannot fit shortens instead — the row
                        // stays a row, which matters more than the last letters of the word.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected) {
                            LocalContentColor.current
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        // Weighted so it is the marker that gives way when the two do not fit, and
                        // never the date — a shortened date would be a different day.
                        //
                        // The gap replaces the "·" this used to be spelled with: the separator cost
                        // more width than the space between them needs, and it was the space beside
                        // it that the wrap broke at.
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(start = 5.dp)
                    )
                }
            }
            // Skipped entirely rather than drawn empty, so a day without dots doesn't leave the
            // row's spacing behind.
            if (dots > 0) {
                DayDots(
                    dots = dots,
                    // Tertiary would sit on the selected row's primary fill; there the row's own
                    // content color carries them instead.
                    color = if (isSelected) {
                        LocalContentColor.current
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                )
            }
            if (count != null) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onTertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.widthIn(min = 22.dp)
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Calls [onLoadOlderDays] whenever [listState] comes within [DAY_LOAD_MARGIN] items of its end —
 * shared by the day strip and the sidebar, so both grow into the past the same way.
 *
 * Counted from the end of the whole list rather than from the days in it, so the "All days" row
 * above the sidebar's days and the spacers around the strip's need no accounting for; a margin of
 * a few items is wide enough to cover them and to have the next page ready before its first day
 * would have been scrolled to.
 *
 * A [snapshotFlow] rather than an effect keyed on "is it near the end": the answer is still yes
 * right after a page arrives, and a list taller than a page (or one asked for before it was ever
 * measured) needs asking again on those terms. Every page changes the item count, so the flow
 * emits again and the question is put again until the days reach past the margin — which they
 * must, a page being longer than it.
 */
@Composable
internal fun LoadOlderDaysWhenNearTheOldest(
    listState: LazyListState,
    onLoadOlderDays: () -> Unit
) {
    // The action is rebuilt with the pane's actions on every recomposition; read through this, the
    // collection below is started once for the list rather than restarted for each of them.
    val loadOlderDays by rememberUpdatedState(onLoadOlderDays)
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastVisibleIndex to layoutInfo.totalItemsCount
        }.collect { (lastVisibleIndex, totalItemsCount) ->
            // Nothing measured yet (a list of no height, a pane still off screen): asking for days
            // then would grow the list for as long as nobody could see it.
            if (lastVisibleIndex == null) return@collect
            if (lastVisibleIndex >= totalItemsCount - DAY_LOAD_MARGIN) loadOlderDays()
        }
    }
}

/** How near its oldest day a day list has to come before it asks for the next page. */
private const val DAY_LOAD_MARGIN = 5

/**
 * Takes [listState] back to today each time [scrollHomeRequest] changes — Esc, "All days" and the
 * key for today all ask for this (see [BitsPaneState.scrollDaysHomeRequest]). Now that the lists go
 * as far into the past as they are scrolled, the way home can be a very long drag, and those three
 * actions are the ones that mean "out of wherever I was".
 *
 * Driven by a request from the view model rather than by watching the selection fall away, because
 * the case that most wants a way home is the one where the state does not move: a list scrolled a
 * year back with nothing selected leaves Esc nothing to clear, and the intent is in the press.
 *
 * The days themselves stay ([BitsPaneState.listedDayCount] is not wound back): what was scrolled to
 * costs a longer list and nothing else, and leaving it there means going back to where one was
 * reading is one drag rather than another walk through the pages.
 *
 * Item 0 is the today end of both lists — the sidebar's "All days" row above its days, the strip's
 * spacer to the right of its newest chip — so this is the position each of them started in. A list
 * already there scrolls nowhere, which is what makes the first firing, on the number the list was
 * composed with, cost nothing.
 */
@Composable
internal fun ScrollBackToTodayWhenAsked(
    listState: LazyListState,
    scrollHomeRequest: Int
) {
    LaunchedEffect(scrollHomeRequest) {
        listState.animateScrollToItem(0)
    }
}

/**
 * How much a day holds, as dots (see [DatedBits.dots]) — shared by the day strip and the sidebar so
 * both day lists indicate a day the same way.
 */
@Composable
internal fun DayDots(
    dots: Int,
    color: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        repeat(dots) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * Width of the [DaySidebar] — the mockup's 216.dp plus the little the mockup had not counted on.
 *
 * A row is the weekday column, the date, the dots, the count badge and the fixed gaps between them,
 * and on the current day the "Today" marker as well. All of that came to 6.dp more than 216 left
 * for it, which the marker paid for by wrapping: it was the only part of the row that could give.
 * Twelve here rather than the six strictly owed, so that a marker a few letters longer than the
 * English one still fits before [DaySidebarItem] has to shorten it.
 */
private val SIDEBAR_WIDTH = 228.dp
