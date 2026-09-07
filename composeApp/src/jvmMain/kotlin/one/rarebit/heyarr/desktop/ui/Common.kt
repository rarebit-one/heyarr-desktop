package one.rarebit.heyarr.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared browse UI shared by the Music / Books / Feeds sections: a back+refresh header, a
 * status line, and a two-line clickable row — the same Material3 look as the Library tab's
 * `WorkRow`, so the sections read as one app.
 */

/** A top bar for a drill-down level: an optional Back button, a Refresh, a spinner, and status. */
@Composable
fun BrowseHeader(
    onBack: (() -> Unit)? = null,
    backLabel: String = "← Back",
    onRefresh: (() -> Unit)? = null,
    loading: Boolean = false,
    status: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) OutlinedButton(onClick = onBack) { Text(backLabel) }
        if (onRefresh != null) OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("Refresh") }
        if (loading) CircularProgressIndicator(Modifier.width(22.dp))
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** A two-line clickable browse row: a bold [title] and a dim [subtitle]. */
@Composable
fun BrowseRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
