package com.telerelay.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.telerelay.R

/** Visual state of an [InlineStatus] row. */
enum class InlineStatusState { IN_PROGRESS, SUCCESS, ERROR, NEUTRAL }

/**
 * One-line inline result row: a 16dp leading indicator (spinner / check /
 * error glyph) and a short text. Used inside cards so feedback stays next to
 * the action that produced it — never a toast or dialog.
 */
@Composable
fun InlineStatus(
    text: String,
    state: InlineStatusState,
    modifier: Modifier = Modifier,
) {
    val tint = when (state) {
        InlineStatusState.SUCCESS -> MaterialTheme.colorScheme.primary
        InlineStatusState.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            InlineStatusState.IN_PROGRESS -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = tint,
            )
            InlineStatusState.SUCCESS -> Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tint,
            )
            InlineStatusState.ERROR -> Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tint,
            )
            InlineStatusState.NEUTRAL -> Spacer(Modifier.width(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}
