package one.rarebit.heyarr.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.rarebit.heyarr.desktop.theme.RubikFamily
import one.rarebit.heyarr.desktop.theme.Tokens

/** One column of a [DataTable]: a header caption and a share of the width (or a fixed width). */
data class Column(val title: String, val weight: Float = 1f, val width: Dp? = null, val alignEnd: Boolean = false)

/**
 * A plain table for the Curate surfaces: a Rubik caption header row, hairline-separated
 * rows, columns by weight. Cells are composables so a cell can hold a chip or a button.
 * Rows may carry an expandable detail (the rule list behind a verdict) — the row is a
 * button that toggles it, so nothing needs a second control.
 */
@Composable
fun DataTable(
    columns: List<Column>,
    rowCount: Int,
    modifier: Modifier = Modifier,
    emptyText: String = "Nothing here.",
    detail: (@Composable (row: Int) -> Unit)? = null,
    detailLabel: (row: Int) -> String? = { null },
    cell: @Composable RowScope.(row: Int, column: Int) -> Unit,
) {
    val shape = RoundedCornerShape(Tokens.radiusInput)
    Column(modifier.fillMaxWidth().clip(shape).border(Tokens.hairline, Tokens.border, shape)) {
        Row(Modifier.fillMaxWidth().background(Tokens.surface2).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (c in columns) HeaderCell(c)
            if (detail != null) Box(Modifier.width(22.dp))
        }
        if (rowCount == 0) Text(emptyText, style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted, modifier = Modifier.padding(12.dp))
        for (r in 0 until rowCount) {
            var open by remember { mutableStateOf(false) }
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val label = detailLabel(r)
            Column(Modifier.fillMaxWidth().background(if (r % 2 == 1) Tokens.surface1.copy(alpha = 0.6f) else Color.Transparent)) {
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (detail != null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = { open = !open }).semantics { contentDescription = (label ?: "row") + if (open) ", expanded" else ", collapsed" } else Modifier)
                        .background(if (hovered && detail != null) Tokens.surface2.copy(alpha = 0.5f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for ((ci, c) in columns.withIndex()) {
                        val m = if (c.width != null) Modifier.width(c.width) else Modifier.weight(c.weight)
                        Box(m, contentAlignment = if (c.alignEnd) Alignment.CenterEnd else Alignment.CenterStart) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { cell(r, ci) } }
                    }
                    if (detail != null) Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = Tokens.textMuted, modifier = Modifier.size(18.dp))
                }
                if (detail != null) AnimatedVisibility(open) {
                    Box(Modifier.fillMaxWidth().background(Tokens.bgBase.copy(alpha = 0.5f)).padding(horizontal = 16.dp, vertical = 10.dp)) { detail(r) }
                }
                if (r < rowCount - 1) Box(Modifier.fillMaxWidth().height(Tokens.hairline).background(Tokens.border))
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(c: Column) {
    val m = if (c.width != null) Modifier.width(c.width) else Modifier.weight(c.weight)
    Box(m, contentAlignment = if (c.alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(c.title.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontFamily = RubikFamily, letterSpacing = 0.8.sp), color = Tokens.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A cell's plain text. */
@Composable
fun Cell(text: String, color: Color = Tokens.textPrimary, muted: Boolean = false, mono: Boolean = false, maxLines: Int = 1) {
    Text(text, style = if (mono) MaterialTheme.typography.labelMedium.copy(fontFamily = RubikFamily) else MaterialTheme.typography.bodySmall, color = if (muted) Tokens.textMuted else color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** A titled, collapsible section for the Curate tab — closed sections keep the page short. */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, subtitle: String? = null, initiallyOpen: Boolean = true, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.radiusButton)).clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = { open = !open })
                .semantics { contentDescription = "$title section, ${if (open) "expanded" else "collapsed"}" }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = Tokens.textMuted, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Tokens.textPrimary)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
            }
            if (trailing != null) trailing()
        }
        AnimatedVisibility(open) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Double.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
