package ru.yakut54.ktoto.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Reusable confirm dialog.
 *
 * @param icon         Optional icon shown in a tinted circle above the title.
 * @param iconTint     Background color of the icon circle. Defaults to [confirmColor].
 * @param confirmColor Color of the confirm button text. Use error for destructive actions.
 */
@Composable
fun KtotoDialog(
    title: String,
    message: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    confirmText: String,
    confirmColor: Color? = null,
    dismissText: String = "Отмена",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.85f),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
            ) {
                // ── Icon ────────────────────────────────────────────────
                if (icon != null) {
                    val tint = iconTint
                        ?: confirmColor
                        ?: MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(tint.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Title ────────────────────────────────────────────────
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )

                // ── Message ──────────────────────────────────────────────
                if (!message.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Buttons ──────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = dismissText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onConfirm) {
                        Text(
                            text = confirmText,
                            fontWeight = FontWeight.SemiBold,
                            color = confirmColor ?: MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Multi-action dialog — for cases with more than two options (e.g. delete for everyone / for me).
 *
 * @param actions List of (label, isDestructive, onClick). Actions are shown top-to-bottom,
 *                separated by dividers. "Отмена" is added automatically at the bottom.
 */
data class DialogAction(
    val text: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun KtotoActionDialog(
    title: String,
    message: String? = null,
    actions: List<DialogAction>,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)) {
                // ── Title ─────────────────────────────────────────────────
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )

                if (!message.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Action rows ───────────────────────────────────────────
                actions.forEach { action ->
                    TextButton(
                        onClick = action.onClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = action.text,
                            fontWeight = FontWeight.Medium,
                            color = if (action.isDestructive)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                // ── Cancel ────────────────────────────────────────────────
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = "Отмена",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
