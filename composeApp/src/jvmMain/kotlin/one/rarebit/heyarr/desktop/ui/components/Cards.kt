package one.rarebit.heyarr.desktop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.rarebit.heyarr.desktop.state.LibraryStatus
import one.rarebit.heyarr.desktop.theme.CardAspect
import one.rarebit.heyarr.desktop.theme.LocalAppearance
import one.rarebit.heyarr.desktop.theme.LocalMediaTheme
import one.rarebit.heyarr.desktop.theme.MediaScope
import one.rarebit.heyarr.desktop.theme.MediaThemes
import one.rarebit.heyarr.desktop.theme.MediaType
import one.rarebit.heyarr.desktop.theme.Tokens

/** The glyph a type's placeholder art shows. */
fun MediaType.icon(): ImageVector = when (this) {
    MediaType.MOVIE -> Icons.Rounded.Movie
    MediaType.SERIES -> Icons.Rounded.Tv
    MediaType.BOOK -> Icons.Rounded.MenuBook
    MediaType.AUDIOBOOK -> Icons.Rounded.Headphones
    MediaType.PODCAST -> Icons.Rounded.Podcasts
    MediaType.MUSIC -> Icons.Rounded.MusicNote
    MediaType.FEED -> Icons.Rounded.RssFeed
    MediaType.UNKNOWN -> Icons.Rounded.HelpOutline
}

/**
 * Artwork with a blur-up: an accent-tinted gradient placeholder (with the type glyph)
 * shows at once; the decoded bitmap fades over it when it lands. Loading is lazy —
 * see [one.rarebit.heyarr.desktop.state.ArtworkLoader.rememberArtwork] at the call site.
 */
@Composable
fun Artwork(bitmap: ImageBitmap?, type: MediaType, modifier: Modifier = Modifier, contentDescription: String? = null, glyphSize: Dp = 28.dp) {
    val theme = MediaThemes.of(type)
    val reduce = LocalAppearance.current.reduceMotion
    val alpha by animateFloatAsState(if (bitmap != null) 1f else 0f, tween(if (reduce) 0 else 350))
    Box(modifier.background(Brush.linearGradient(listOf(theme.accent.copy(alpha = 0.35f), Tokens.surface2, Tokens.surface1))), contentAlignment = Alignment.Center) {
        if (bitmap == null) Icon(type.icon(), contentDescription = null, tint = theme.accent.copy(alpha = 0.55f), modifier = Modifier.size(glyphSize))
        if (bitmap != null) Image(bitmap, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(alpha))
    }
}

/** Library status as a small pill: In library (accent), Wanted (gold), Missing (danger), Not tracked (muted). */
@Composable
fun StatusPill(status: LibraryStatus, modifier: Modifier = Modifier, compact: Boolean = false) {
    val accent = LocalMediaTheme.current.accentGradientEnd
    val (tone, dot) = when (status) {
        LibraryStatus.IN_LIBRARY -> accent to accent
        LibraryStatus.WANTED -> Tokens.ratingGold to Tokens.ratingGold
        LibraryStatus.MISSING -> Tokens.danger to Tokens.danger
        LibraryStatus.NOT_TRACKED -> Tokens.textMuted to Tokens.textDisabled
    }
    Row(
        modifier.background(Tokens.bgBase.copy(alpha = 0.72f), CircleShape).border(Tokens.hairline, tone.copy(alpha = 0.45f), CircleShape).padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 2.dp)
            .semantics { this.contentDescription = "Status: ${status.label}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).background(dot, CircleShape))
        if (!compact) Text(status.label, style = MaterialTheme.typography.labelSmall, color = tone, maxLines = 1)
    }
}

/**
 * The poster / cover / square card. Aspect, placeholder glyph and accent follow the
 * type; the type badge sits top-left, the status pill top-right, and the one-click
 * Want action appears on hover (or focus) at the bottom edge. Fully keyboard-operable:
 * the card is a focusable button, and Want is a second focus stop.
 */
@Composable
fun MediaCard(
    title: String,
    type: MediaType,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: List<String?> = emptyList(),
    artwork: ImageBitmap? = null,
    status: LibraryStatus? = null,
    onWant: (() -> Unit)? = null,
    width: Dp = Tokens.posterWidth,
    showBadge: Boolean = true,
) = MediaScope(type) {
    val theme = LocalMediaTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(Tokens.radiusCard)
    Column(
        modifier.width(width)
            .focusRing(interaction, shape, inset = 2.dp)
            .clip(shape)
            .hoverSurface(interaction, shape)
            .border(Tokens.hairline, if (hovered) theme.accent.copy(alpha = 0.6f) else Tokens.border, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)
            .semantics { this.contentDescription = "${type.label}: $title${status?.let { ", ${it.label}" } ?: ""}" },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(theme.aspect.ratio)) {
            Artwork(artwork, type, Modifier.fillMaxSize(), contentDescription = null)
            if (theme.spineShadow) Box(Modifier.width(10.dp).fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
            if (showBadge) MediaBadge(type, Modifier.align(Alignment.TopStart).padding(8.dp))
            if (status != null) StatusPill(status, Modifier.align(Alignment.TopEnd).padding(8.dp), compact = !hovered)
            if (onWant != null && status != LibraryStatus.IN_LIBRARY && hovered) {
                Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                    PrimaryButton(if (status == LibraryStatus.NOT_TRACKED || status == null) "Want" else "Wanted", onWant, icon = if (status == LibraryStatus.NOT_TRACKED || status == null) Icons.Rounded.Add else Icons.Rounded.Check, compact = true, enabled = status == LibraryStatus.NOT_TRACKED || status == null)
                }
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Tokens.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MetaLine(meta)
        }
    }
}

/** The skeleton twin of [MediaCard]. */
@Composable
fun MediaCardSkeleton(aspect: CardAspect = CardAspect.POSTER, width: Dp = Tokens.posterWidth) {
    Column(Modifier.width(width).clip(RoundedCornerShape(Tokens.radiusCard)).background(Tokens.surface1)) {
        Skeleton(Modifier.fillMaxWidth().aspectRatio(aspect.ratio), RoundedCornerShape(0.dp))
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Skeleton(Modifier.fillMaxWidth(0.8f).height(12.dp))
            Skeleton(Modifier.fillMaxWidth(0.5f).height(10.dp))
        }
    }
}

/** A compact list row (search results, missing list): art thumb, title, meta, badge, status, trailing action. */
@Composable
fun MediaRow(
    title: String,
    type: MediaType,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: List<String?> = emptyList(),
    artwork: ImageBitmap? = null,
    status: LibraryStatus? = null,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) = MediaScope(type) {
    val theme = LocalMediaTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(Tokens.radiusInput)
    val bg = when { selected -> theme.tint(0.16f); hovered -> Tokens.surface2; else -> Color.Transparent }
    Row(
        modifier.fillMaxWidth()
            .focusRing(interaction, shape)
            .clip(shape)
            .background(bg, shape)
            .border(Tokens.hairline, if (selected) theme.accent else Color.Transparent, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)
            .semantics { this.contentDescription = "${type.label}: $title${status?.let { ", ${it.label}" } ?: ""}${if (selected) ", selected" else ""}" }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val thumbW = if (theme.aspect == CardAspect.POSTER) 40.dp else 52.dp
        Box(Modifier.width(thumbW).height(52.dp).clip(RoundedCornerShape(6.dp))) { Artwork(artwork, type, Modifier.fillMaxSize(), glyphSize = 18.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Tokens.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (type != MediaType.UNKNOWN) MediaBadge(type)
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MetaLine(meta)
        }
        if (status != null) StatusPill(status)
        if (trailing != null) trailing()
    }
}

/** Skeleton rows for a list. */
@Composable
fun MediaRowSkeleton(count: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(Modifier.width(40.dp).height(52.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Skeleton(Modifier.fillMaxWidth(0.5f).height(12.dp)); Skeleton(Modifier.fillMaxWidth(0.3f).height(10.dp))
                }
            }
        }
    }
}

/** The state a rail can be in; the rail renders skeletons / an inline error / an empty line itself. */
sealed interface RailState<out T> {
    data object Loading : RailState<Nothing>
    data class Loaded<T>(val items: List<T>) : RailState<T>
    data class Failed(val message: String) : RailState<Nothing>
}

/**
 * A horizontally-scrolling content rail with a section header. LazyRow, so a 200-item
 * rail composes only what is visible. The header is themed by the surrounding scope;
 * each card themes itself.
 */
@Composable
fun <T> Rail(
    title: String,
    state: RailState<T>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    emptyText: String = "Nothing here yet.",
    skeletonAspect: CardAspect = CardAspect.POSTER,
    skeletonWidth: Dp = Tokens.posterWidth,
    trailing: (@Composable () -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    key: ((T) -> Any)? = null,
    card: @Composable (T) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(title, subtitle = subtitle, trailing = trailing)
        when (state) {
            RailState.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(Tokens.gridGap)) { repeat(6) { MediaCardSkeleton(skeletonAspect, skeletonWidth) } }
            is RailState.Failed -> Notice(state.message, tone = Tokens.danger, detail = onRetry?.let { "Click Retry in the banner or reload the page." })
            is RailState.Loaded -> if (state.items.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = Tokens.textMuted)
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(Tokens.gridGap), contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 24.dp)) {
                items(state.items, key = key) { card(it) }
            }
        }
    }
}

/**
 * The hero spotlight: full-bleed art behind a layered scrim, a title/meta/CTA stack
 * bottom-left. The whole block sits in the item's media scope, so the CTA gradient and
 * the badge follow the type.
 */
@Composable
fun Hero(
    title: String,
    type: MediaType,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    meta: List<String?> = emptyList(),
    description: String? = null,
    artwork: ImageBitmap? = null,
    status: LibraryStatus? = null,
    primary: (@Composable () -> Unit)? = null,
    secondary: (@Composable () -> Unit)? = null,
    height: Dp = 380.dp,
) = MediaScope(type) {
    val theme = LocalMediaTheme.current
    val shape = RoundedCornerShape(Tokens.radiusCard)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(Tokens.surface1).border(Tokens.hairline, Tokens.border, shape)) {
        Artwork(artwork, type, Modifier.fillMaxSize(), glyphSize = 72.dp)
        // Layered scrim: bottom-up darkening, plus a left-to-right one so the text column reads on any art.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Tokens.bgBase.copy(alpha = 0.55f), Tokens.bgBase.copy(alpha = 0.96f)))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Tokens.bgBase.copy(alpha = 0.85f), Tokens.bgBase.copy(alpha = 0.35f), Color.Transparent))))
        Box(Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomStart).background(Brush.horizontalGradient(listOf(theme.accent, theme.accentGradientEnd, Color.Transparent))))
        Column(Modifier.align(Alignment.BottomStart).padding(28.dp).fillMaxWidth(0.62f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaBadge(type)
                if (kicker != null) Text(kicker.uppercase(), style = MaterialTheme.typography.labelSmall, color = theme.accentGradientEnd)
                if (status != null) StatusPill(status)
            }
            Text(title, style = MaterialTheme.typography.displayMedium, color = Tokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            MetaLine(meta, color = Tokens.textPrimary.copy(alpha = 0.85f))
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = Tokens.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (primary != null) primary()
                if (secondary != null) secondary()
            }
        }
    }
}

@Composable
fun HeroSkeleton(height: Dp = 380.dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(Tokens.radiusCard))) {
        Skeleton(Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(Modifier.width(80.dp).height(14.dp)); Skeleton(Modifier.width(360.dp).height(30.dp)); Skeleton(Modifier.width(220.dp).height(12.dp))
            Skeleton(Modifier.width(120.dp).height(40.dp), CircleShape)
        }
    }
}
