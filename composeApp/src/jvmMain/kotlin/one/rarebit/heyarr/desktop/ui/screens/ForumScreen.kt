package one.rarebit.heyarr.desktop.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import one.rarebit.heyarr.desktop.ui.components.EmptyState
import one.rarebit.heyarr.desktop.ui.components.SectionHeader

/** The optional community stub from the SaintStream kit — a placeholder, honestly labelled. */
@Composable
fun ForumScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        SectionHeader("Forum")
        EmptyState("Community is not wired up", detail = "heyarr has no forum surface. This tab is a stub kept from the SaintStream layout for when one exists.", icon = Icons.Rounded.Forum)
    }
}
