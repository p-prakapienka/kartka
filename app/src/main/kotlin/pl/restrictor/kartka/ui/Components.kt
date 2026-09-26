package pl.restrictor.kartka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import pl.restrictor.kartka.R
import pl.restrictor.kartka.domain.DueText
import pl.restrictor.kartka.domain.TopicColors

fun topicColor(key: String, dark: Boolean): Color = when (key) {
    "blue" -> if (dark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
    "violet" -> if (dark) Color(0xFFC4B5FD) else Color(0xFF6D28D9)
    "rose" -> if (dark) Color(0xFFFDA4AF) else Color(0xFFBE123C)
    "amber" -> if (dark) Color(0xFFFCD34D) else Color(0xFFB45309)
    "green" -> if (dark) Color(0xFF86EFAC) else Color(0xFF15803D)
    else -> if (dark) Color(0xFF5EEAD4) else Color(0xFF0F766E)
}

@Composable
fun dueLabel(text: DueText): String = when (text) {
    DueText.Now -> stringResource(R.string.due_now)
    is DueText.Minutes -> stringResource(R.string.due_in_min, text.minutes)
    is DueText.Hours -> stringResource(R.string.due_in_hours, text.hours)
    DueText.Tomorrow -> stringResource(R.string.back_tomorrow)
    is DueText.Days -> stringResource(R.string.back_in_days, text.days)
}

@Composable
fun dueAndCards(due: Int, cards: Int): String {
    return pluralStringResource(R.plurals.due, due, due) + " · " + pluralStringResource(R.plurals.cards, cards, cards)
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            body,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onAction) {
            Text(action, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckRow(
    title: String,
    subtitle: String,
    meta: String,
    colorKey: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onExport: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val stripe = topicColor(colorKey, isSystemInDarkTheme())
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(stripe))
            Column(Modifier.weight(1f).padding(start = 16.dp, top = 16.dp, bottom = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    meta,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = stripe,
                )
            }
            RowMenu(onEdit = onEdit, onExport = onExport, onDelete = onDelete)
        }
    }
}

@Composable
fun RowMenu(onEdit: () -> Unit, onExport: (() -> Unit)?, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit)) },
                onClick = { open = false; onEdit() },
            )
            if (onExport != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export)) },
                    onClick = { open = false; onExport() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun ColorDots(selected: String, onSelect: (String) -> Unit) {
    val dark = isSystemInDarkTheme()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TopicColors.keys.forEach { key ->
            val color = topicColor(key, dark)
            val chosen = key == selected
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (chosen) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(key) }
                    .semantics { stateDescription = if (chosen) "Selected" else "Not selected" },
            )
        }
    }
}
