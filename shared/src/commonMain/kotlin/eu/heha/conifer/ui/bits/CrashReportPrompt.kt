package eu.heha.conifer.ui.bits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.crash_action_copy
import conifer.shared.generated.resources.crash_action_dismiss
import conifer.shared.generated.resources.crash_prompt_lead
import conifer.shared.generated.resources.crash_prompt_note
import conifer.shared.generated.resources.crash_prompt_unknown_error
import eu.heha.conifer.log.CrashBreadcrumb
import eu.heha.conifer.ui.LocalDateTimeFormats
import eu.heha.conifer.ui.dateAndTimeOf
import org.jetbrains.compose.resources.stringResource

/** [CrashReportPrompt] with its show/hide animation; nothing is shown without a crash to report. */
@Composable
internal fun CrashReportPromptItem(
    state: BitsPaneState,
    actions: BitsPaneActions,
    modifier: Modifier = Modifier
) {
    val lastCrash = state.lastCrash
    AnimatedVisibility(lastCrash != null, modifier = modifier) {
        if (lastCrash != null) {
            CrashReportPrompt(lastCrash, isCopyPossible = state.isCopyPossible, actions = actions)
        }
    }
}

/**
 * The banner that turns the last run's crash into something a person can report: when it happened,
 * what it was, and a button that puts the whole thing on the clipboard.
 *
 * It is here because a log file nobody knows about answers nothing - see [CrashBreadcrumb]. It says
 * what it will hand over, and it is honest: the copied text names the build and the error and never
 * a single bit (see `crashReportText`).
 *
 * Shaped like [PermissionPrompt] - the screen's other banner - but in the error colours and with no
 * dashed frame: this one reports something that already went wrong rather than asking for something.
 */
@Composable
private fun CrashReportPrompt(
    lastCrash: CrashBreadcrumb,
    isCopyPossible: Boolean,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "⚠️", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(stringResource(Res.string.crash_prompt_lead))
                            }
                            appendLine()
                            // The moment in the reader's own spelling, the error in the machine's:
                            // one of the two is for them and the other is for whoever gets the
                            // report. A crash with neither a type nor a message is possible (a
                            // platform that reported it in no words at all), so it says so.
                            append(formats.dateAndTimeOf(lastCrash.at))
                            append(" · ")
                            append(
                                lastCrash.summary
                                    ?: stringResource(Res.string.crash_prompt_unknown_error)
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = stringResource(Res.string.crash_prompt_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // No clipboard on this platform leaves the banner as a notice: it still says the
                    // run crashed, and the log file it names is still where it says it is.
                    if (isCopyPossible) {
                        TextButton(onClick = actions.onClickCopyCrashReport) {
                            Text(stringResource(Res.string.crash_action_copy))
                        }
                    }
                    TextButton(onClick = actions.onDismissCrashReport) {
                        Text(stringResource(Res.string.crash_action_dismiss))
                    }
                }
            }
        }
    }
}

/** `IllegalStateException: boom`, or as much of it as the crash actually came with. */
private val CrashBreadcrumb.summary: String?
    get() = listOfNotNull(type, message).joinToString(": ").takeIf { it.isNotEmpty() }

/** The same width the other banner is drawn to; see [PermissionPrompt]. */
private val PROMPT_MAX_WIDTH = 480.dp

private val CORNER_RADIUS = 12.dp
