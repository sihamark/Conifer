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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.bits_action_all_days
import conifer.shared.generated.resources.bits_label_days
import conifer.shared.generated.resources.bits_label_today
import eu.heha.conifer.ui.DatedBits
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource

/**
 * The two-pane layout's day list: "All days" followed by the same 30 days the day strip offers,
 * each with the strip's dots and the number of bits written that day. It replaces the day strip
 * inside the composer's picker, so selecting a day here filters the list *and* dates the next bit,
 * exactly as the strip does — tapping the selected day again (or "All days") lifts the filter.
 */
@Composable
internal fun DaySidebar(
    bitsByDate: List<DatedBits>,
    selectedDate: LocalDate?,
    currentDate: LocalDate,
    isTopBarVisible: Boolean,
    onClickDate: (LocalDate) -> Unit,
    onClickAllDays: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.width(SIDEBAR_WIDTH)) {
        // Lines the sidebar's content up with the list's, below the top bar floating over the main
        // pane; it follows the bar out of the way while the IME is open.
        AnimatedVisibility(isTopBarVisible) {
            Spacer(Modifier.height(TopAppBarDefaults.TopAppBarExpandedHeight))
        }
        Text(
            text = stringResource(Res.string.bits_label_days),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 18.dp, end = 10.dp, bottom = 4.dp)
        )
        // As in DaySelection, the items are as flat as their content instead of being stretched to
        // the minimum touch target height, so a useful number of days fits without scrolling.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            LazyColumn(
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
                items(30, key = { it }) { dayIndex ->
                    val date = LocalDate.fromEpochDays(currentDate.toEpochDays() - dayIndex)
                    val datedBits = bitsByDate.firstOrNull { it.date == date }
                    DaySidebarItem(
                        weekday = date.dayOfWeek.name.take(3),
                        label = "${date.day}.${date.month.number}.",
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
                        text = " · " + stringResource(Res.string.bits_label_today),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            LocalContentColor.current
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
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

/** Width of the [DaySidebar], as in the mockup. */
private val SIDEBAR_WIDTH = 216.dp
