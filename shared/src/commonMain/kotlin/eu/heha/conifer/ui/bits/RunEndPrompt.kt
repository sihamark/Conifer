package eu.heha.conifer.ui.bits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.run_end_action_copy
import conifer.shared.generated.resources.run_end_action_dismiss
import conifer.shared.generated.resources.run_end_action_share
import conifer.shared.generated.resources.run_end_lead_crashed
import conifer.shared.generated.resources.run_end_lead_vanished
import conifer.shared.generated.resources.run_end_note
import conifer.shared.generated.resources.run_end_summary_unknown
import conifer.shared.generated.resources.run_end_summary_vanished
import eu.heha.conifer.log.CrashBreadcrumb
import eu.heha.conifer.log.LastRunEnd
import eu.heha.conifer.ui.LocalDateTimeFormats
import eu.heha.conifer.ui.dateAndTimeOf
import org.jetbrains.compose.resources.stringResource

/** [RunEndPrompt] with its show/hide animation; nothing is shown after a run that ended properly. */
@Composable
internal fun RunEndPromptItem(
    state: BitsPaneState,
    actions: BitsPaneActions,
    /**
     * How many lines of the error to show; see `currentRunEndMaxLines`, which is what decides it
     * in the app. Defaulted to the count the shortest window gets, for the same reason
     * [BitsAndComposer]'s field defaults to one line: a preview or a test is a plain composable with
     * nothing behind it, and the tightest layout is the one worth having by default.
     */
    summaryMaxLines: Int = RUN_END_SUMMARY_MIN_LINES,
    modifier: Modifier = Modifier
) {
    val lastRunEnd = state.lastRunEnd
    AnimatedVisibility(lastRunEnd != null, modifier = modifier) {
        if (lastRunEnd != null) {
            RunEndPrompt(
                lastRunEnd = lastRunEnd,
                isCopyPossible = state.isCopyPossible,
                isSharePossible = state.isSharePossible,
                summaryMaxLines = summaryMaxLines,
                actions = actions
            )
        }
    }
}

/**
 * The banner that turns the last run's bad ending into something a person can report: what is known
 * about it, and a button that hands the whole report over.
 *
 * It is here because a log file nobody knows about answers nothing - see
 * [eu.heha.conifer.log.CrashBreadcrumb]. It says what it will hand over, and it is honest twice
 * over: the report names the build and the error and never a single bit (see `runEndReportText`),
 * and a run that merely stopped is not called a crash, because from here nobody can tell whether it
 * was one.
 *
 * Shaped like [PermissionPrompt] - the screen's other banner - but in the error colours and with no
 * dashed frame: this one reports something that already went wrong rather than asking for something.
 *
 * Its height is bounded by [summaryMaxLines] and nothing else, because an error is as long as it
 * feels like being - a `VerifyError` names every local in the frame - and a banner tall enough to
 * push its own buttons off the window is a notice with no way out of it. What is cut here is not
 * lost: the report carries the whole of it.
 */
@Composable
private fun RunEndPrompt(
    lastRunEnd: LastRunEnd,
    isCopyPossible: Boolean,
    isSharePossible: Boolean,
    summaryMaxLines: Int,
    actions: BitsPaneActions
) {
    val formats = LocalDateTimeFormats.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(CORNER_RADIUS),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .widthIn(max = PROMPT_MAX_WIDTH)
                .fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                val lead = when (lastRunEnd) {
                    is LastRunEnd.Crashed -> stringResource(Res.string.run_end_lead_crashed)
                    is LastRunEnd.Vanished -> stringResource(Res.string.run_end_lead_vanished)
                }
                // A crash knows when it happened; a run that vanished only knows when it started,
                // that being the last moment anybody wrote down.
                val moment = when (lastRunEnd) {
                    is LastRunEnd.Crashed -> lastRunEnd.breadcrumb.at
                    is LastRunEnd.Vanished -> lastRunEnd.run.startedAt
                }
                val summary = when (lastRunEnd) {
                    is LastRunEnd.Crashed -> lastRunEnd.breadcrumb.summary
                        ?: stringResource(Res.string.run_end_summary_unknown)

                    is LastRunEnd.Vanished -> stringResource(Res.string.run_end_summary_vanished)
                }
                Row(
                    // Aligned to the first line rather than centred: the error below it is as many
                    // lines as it is allowed, and an icon halfway down a paragraph belongs to
                    // nothing in particular.
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "⚠️", style = MaterialTheme.typography.titleLarge)
                    Column(modifier = Modifier.weight(1f)) {
                        // The sentence that says what the banner is keeps its own line, out of the
                        // error's budget: cut to make room for a stack dump, it would be the one
                        // line the reader actually needed.
                        Text(
                            text = lead,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        // The moment in the reader's own spelling, what happened in the machine's:
                        // one half is for them, the other for whoever gets the report.
                        Text(
                            text = buildString {
                                moment?.let {
                                    append(formats.dateAndTimeOf(it))
                                    append(" · ")
                                }
                                append(summary)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = summaryMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.run_end_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                // Three buttons is more than the narrowest window this app runs in has room for in
                // one line, so they wrap rather than being squeezed or cut off.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Sharing first: it is the one that gets the report to somebody, where copying
                    // only gets it as far as the clipboard. Either can be missing - a platform with
                    // neither leaves the banner as a notice, which still says the run ended badly.
                    if (isSharePossible) {
                        TextButton(onClick = actions.onClickShareRunEndReport) {
                            Text(stringResource(Res.string.run_end_action_share))
                        }
                    }
                    if (isCopyPossible) {
                        TextButton(onClick = actions.onClickCopyRunEndReport) {
                            Text(stringResource(Res.string.run_end_action_copy))
                        }
                    }
                    TextButton(onClick = actions.onDismissRunEndReport) {
                        Text(stringResource(Res.string.run_end_action_dismiss))
                    }
                }
            }
        }
    }
}

/** `IllegalStateException: boom`, or as much of it as the crash actually came with. */
private val CrashBreadcrumb.summary: String?
    get() = listOfNotNull(type, message).joinToString(": ").takeIf { it.isNotEmpty() }

/**
 * The fewest lines of the error the banner ever shows - what the shortest windows get, and what it
 * falls back to where nobody says. Two, because the type alone can take one of them.
 */
internal const val RUN_END_SUMMARY_MIN_LINES = 2

/** The same width the other banner is drawn to; see [PermissionPrompt]. */
private val PROMPT_MAX_WIDTH = 480.dp

private val CORNER_RADIUS = 12.dp
