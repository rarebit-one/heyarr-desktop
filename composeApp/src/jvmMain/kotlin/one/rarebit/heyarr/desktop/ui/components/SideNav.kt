package one.rarebit.heyarr.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import one.rarebit.heyarr.desktop.state.Connection
import one.rarebit.heyarr.desktop.theme.LocalMediaTheme
import one.rarebit.heyarr.desktop.theme.Tokens
import one.rarebit.heyarr.desktop.ui.Route

/** A nav destination: label, icon, route, optional keyboard hint. */
data class NavItem(val route: Route, val label: String, val icon: ImageVector, val hint: String? = null)

val NAV_ITEMS = listOf(
    NavItem(Route.Home, "Home", Icons.Rounded.Home, "⌃1"),
    NavItem(Route.Discover, "Discover", Icons.Rounded.Explore, "⌃2"),
    NavItem(Route.Search, "Search", Icons.Rounded.Search, "⌘K"),
    NavItem(Route.Library, "Library", Icons.Rounded.VideoLibrary, "⌃3"),
    NavItem(Route.Missing, "Missing", Icons.Rounded.ReportProblem, "⌃4"),
    NavItem(Route.NowPlaying, "Now playing", Icons.Rounded.Cast, "⌃5"),
    NavItem(Route.Forum, "Forum", Icons.Rounded.Forum),
    NavItem(Route.Settings, "Settings", Icons.Rounded.Settings, "⌘,"),
)

/**
 * The left navigation. Full width shows labels and shortcuts; the compact form (small
 * windows) is icon-only with the label as its accessible name. The active item is
 * marked in the accent of the media currently in focus.
 */
@Composable
fun SideNav(current: Route, onGo: (Route) -> Unit, connection: Connection, compact: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalMediaTheme.current.accent
    Column(
        modifier.fillMaxHeight().width(if (compact) Tokens.navWidthCompact else Tokens.navWidth).background(Tokens.surface1).padding(vertical = 16.dp, horizontal = if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(28.dp).background(Brush.linearGradient(listOf(accent, LocalMediaTheme.current.accentGradientEnd)), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("h", style = MaterialTheme.typography.headlineSmall, color = Tokens.bgBase)
            }
            if (!compact) Text("heyarr", style = MaterialTheme.typography.headlineSmall, color = Tokens.textPrimary)
        }
        Spacer(Modifier.height(8.dp))
        for (item in NAV_ITEMS) {
            val active = current.section == item.route.section
            NavRow(item, active, compact, accent) { onGo(item.route) }
        }
        Spacer(Modifier.weight(1f))
        ConnectionDot(connection, compact)
    }
}

@Composable
private fun NavRow(item: NavItem, active: Boolean, compact: Boolean, accent: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(Tokens.radiusButton)
    val bg = when { active -> accent.copy(alpha = 0.16f); hovered -> Tokens.surface2; else -> Color.Transparent }
    val fg = if (active) Tokens.textPrimary else Tokens.textMuted
    Row(
        Modifier.fillMaxWidth()
            .focusRing(interaction, shape)
            .clip(shape)
            .background(bg, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.contentDescription = item.label + if (active) ", current" else "" }
            .padding(horizontal = if (compact) 0.dp else 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(10.dp),
    ) {
        if (active) Box(Modifier.width(3.dp).height(18.dp).background(accent, CircleShape)) else if (!compact) Spacer(Modifier.width(3.dp))
        Icon(item.icon, contentDescription = null, tint = if (active) accent else fg, modifier = Modifier.size(20.dp))
        if (!compact) {
            Text(item.label, style = MaterialTheme.typography.labelLarge, color = fg, modifier = Modifier.weight(1f))
            if (item.hint != null && (hovered || active)) Kbd(item.hint)
        }
    }
}

@Composable
private fun ConnectionDot(connection: Connection, compact: Boolean) {
    val (tone, label) = when (connection) {
        Connection.ONLINE -> Tokens.success to "Connected"
        Connection.OFFLINE -> Tokens.danger to "Offline"
        Connection.UNAUTHORIZED -> Tokens.warning to "Token refused"
        Connection.UNCONFIGURED -> Tokens.textDisabled to "Not configured"
        Connection.UNKNOWN -> Tokens.textDisabled to "Connecting…"
    }
    Row(Modifier.padding(horizontal = if (compact) 0.dp else 10.dp, vertical = 8.dp).semantics { this.contentDescription = "heyarr: $label" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(tone, CircleShape).border(Tokens.hairline, tone.copy(alpha = 0.4f), CircleShape))
        if (!compact) Text(label, style = MaterialTheme.typography.labelMedium, color = Tokens.textMuted)
    }
}
