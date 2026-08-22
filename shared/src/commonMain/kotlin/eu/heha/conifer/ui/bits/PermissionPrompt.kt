package eu.heha.conifer.ui.bits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.bits_action_grant_permission
import eu.heha.conifer.PermissionRationale
import org.jetbrains.compose.resources.stringResource

/** [PermissionPrompt] with its show/hide animation; nothing is shown without a rationale. */
@Composable
internal fun PermissionPromptItem(
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
    val borderColor = MaterialTheme.colorScheme.tertiary
    val leadColor = MaterialTheme.colorScheme.primary
    // Centered inside the pane rather than spanning it: on a phone the banner fills the width
    // anyway, but a wide pane would otherwise pull the bell, the text and the button far apart.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(CORNER_RADIUS),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .widthIn(max = PROMPT_MAX_WIDTH)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                // The dashed frame is drawn by hand: Surface's own border is a plain BorderStroke,
                // which has no PathEffect to dash with. It sits on the content rather than on the
                // Surface because a modifier handed to a Surface wraps it from the outside, where
                // the Surface's own background would paint straight over the dashes.
                modifier = Modifier
                    .drawBehind {
                        drawRoundRect(
                            color = borderColor,
                            cornerRadius = CornerRadius(CORNER_RADIUS.toPx()),
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
    }
}

/** Roughly the width the banner has on a phone, which is the width it was drawn for. */
private val PROMPT_MAX_WIDTH = 480.dp

private val CORNER_RADIUS = 12.dp
