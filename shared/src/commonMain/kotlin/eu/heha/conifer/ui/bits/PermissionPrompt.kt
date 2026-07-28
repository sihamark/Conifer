package eu.heha.conifer.ui.bits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
