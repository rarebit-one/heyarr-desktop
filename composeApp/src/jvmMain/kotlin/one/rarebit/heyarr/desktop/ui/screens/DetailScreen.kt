package one.rarebit.heyarr.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.rarebit.heyarr.desktop.feeds.FollowedItem
import one.rarebit.heyarr.desktop.heyarr.Candidate
import one.rarebit.heyarr.desktop.heyarr.ContinueEntry
import one.rarebit.heyarr.desktop.heyarr.DesiredItem
import one.rarebit.heyarr.desktop.heyarr.McpResult
import one.rarebit.heyarr.desktop.library.Episode
import one.rarebit.heyarr.desktop.library.PrimaryAsset
import one.rarebit.heyarr.desktop.library.Season
import one.rarebit.heyarr.desktop.library.Series
import one.rarebit.heyarr.desktop.library.WorkDetail
import one.rarebit.heyarr.desktop.mcp.Explanation
import one.rarebit.heyarr.desktop.mcp.ExternalId
import one.rarebit.heyarr.desktop.mcp.ReleaseAttributes
import one.rarebit.heyarr.desktop.mcp.ReleaseToExplain
import one.rarebit.heyarr.desktop.mcp.Renderer
import one.rarebit.heyarr.desktop.mcp.Replica
import one.rarebit.heyarr.desktop.mcp.Satisfaction
import one.rarebit.heyarr.desktop.music.Track
import one.rarebit.heyarr.desktop.playback.PlayResult
import one.rarebit.heyarr.desktop.state.AppSession
import one.rarebit.heyarr.desktop.state.LibraryStatus
import one.rarebit.heyarr.desktop.state.Toast
import one.rarebit.heyarr.desktop.state.rememberArtwork
import one.rarebit.heyarr.desktop.theme.LocalMediaTheme
import one.rarebit.heyarr.desktop.theme.MediaScope
import one.rarebit.heyarr.desktop.theme.MediaThemes
import one.rarebit.heyarr.desktop.theme.MediaType
import one.rarebit.heyarr.desktop.theme.Tokens
import one.rarebit.heyarr.desktop.ui.Route
import one.rarebit.heyarr.desktop.ui.components.Artwork
import one.rarebit.heyarr.desktop.ui.components.ErrorState
import one.rarebit.heyarr.desktop.ui.components.FilterChip
import one.rarebit.heyarr.desktop.ui.components.GhostButton
import one.rarebit.heyarr.desktop.ui.components.Hero
import one.rarebit.heyarr.desktop.ui.components.HeroSkeleton
import one.rarebit.heyarr.desktop.ui.components.IconButtonRound
import one.rarebit.heyarr.desktop.ui.components.KeyValue
import one.rarebit.heyarr.desktop.ui.components.MediaRowSkeleton
import one.rarebit.heyarr.desktop.ui.components.Notice
import one.rarebit.heyarr.desktop.ui.components.Panel
import one.rarebit.heyarr.desktop.ui.components.PrimaryButton
import one.rarebit.heyarr.desktop.ui.components.ReasonList
import one.rarebit.heyarr.desktop.ui.components.RejectedBy
import one.rarebit.heyarr.desktop.ui.components.RuleCode
import one.rarebit.heyarr.desktop.ui.components.SecondaryButton
import one.rarebit.heyarr.desktop.ui.components.SectionHeader
import one.rarebit.heyarr.desktop.ui.components.Skeleton
import one.rarebit.heyarr.desktop.ui.components.StatusPill
import one.rarebit.heyarr.desktop.ui.components.focusRing
import one.rarebit.heyarr.desktop.ui.components.verdictColor

/** The two faces of a work: what you came to watch, and the tooling that keeps it that way. */
enum class DetailTab(val label: String) { WATCH("Watch"), CURATE("Curate") }

/** Everything the detail screen loads for one work, each piece independently. */
class DetailState(val workId: String) {
    var tab by mutableStateOf(DetailTab.WATCH)
    var detail by mutableStateOf<WorkDetail?>(null)
    var detailError by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var assets by mutableStateOf<List<Track>?>(null)
    var season by mutableStateOf<Int?>(null)
    var continueEntry by mutableStateOf<ContinueEntry?>(null)
    var feedItems by mutableStateOf<List<FollowedItem>?>(null)
    var externalIds by mutableStateOf<List<ExternalId>>(emptyList())
    var satisfaction by mutableStateOf<Map<String, McpResult<Satisfaction?>>>(emptyMap())
    var candidates by mutableStateOf<Map<String, List<Candidate>>>(emptyMap())
    var replicas by mutableStateOf<McpResult<List<Replica>>?>(null)
    var renderers by mutableStateOf<List<Renderer>?>(null)
    /** The asset a "Play on…" picker is open for, if any. */
    var castAssetId by mutableStateOf<String?>(null)
    var busy by mutableStateOf<String?>(null)
}

/**
 * The one adaptive detail template, built for consumption first. **Watch** is what
 * you came for: the art, a synopsis when the node has one (and an honest line when it
 * has not), and the thing itself — seasons and episodes with their thumbnails for a
 * series, tracks for an album, the file for a film, the archive for a feed. **Curate**
 * keeps every technical surface — why this release, indexer candidates, scoring, health,
 * captions and artwork inventory, files — one tab away, never on the way.
 */
@Composable
fun DetailScreen(session: AppSession, route: Route.Detail, state: DetailState, onBack: () -> Unit, onOpen: (Route) -> Unit, onWant: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val wants: List<DesiredItem> = session.index.wantsFor(route.workId)
    val detail = state.detail
    val type = detail?.work?.kind?.let { MediaType.from(it) } ?: route.typeHint

    fun load() {
        val a = session.api ?: return
        state.loading = true; state.detailError = null
        scope.launch {
            session.io { a.work(route.workId) }.fold(
                onSuccess = { d -> state.detail = d; state.detailError = if (d == null) "This work no longer exists." else null },
                onFailure = { state.detailError = it.message },
            )
            state.loading = false
        }
        scope.launch { session.io { a.assets(route.workId) }.onSuccess { state.assets = it } }
        scope.launch { session.io { a.externalIds(route.workId) }.onSuccess { state.externalIds = it } }
        scope.launch { session.io { a.continueRail() }.onSuccess { list -> state.continueEntry = list.firstOrNull { it.workId == route.workId } } }
    }
    fun loadWants() {
        val a = session.api ?: return
        for (w in wants) {
            if (w.id.startsWith("pending:")) continue
            scope.launch { session.io { a.satisfaction(w.id) }.onSuccess { r -> state.satisfaction = state.satisfaction + (w.id to r) } }
            scope.launch { session.io { a.candidates(w.id) }.onSuccess { c -> state.candidates = state.candidates + (w.id to (c?.candidates ?: emptyList())) } }
        }
    }
    LaunchedEffect(route.workId) { if (route.curate) state.tab = DetailTab.CURATE; load() }
    LaunchedEffect(wants.map { it.id }) { loadWants() }
    LaunchedEffect(detail?.primaryAsset?.blobHash) {
        val hash = detail?.primaryAsset?.blobHash ?: return@LaunchedEffect
        val a = session.api ?: return@LaunchedEffect
        session.io { a.replicas(hash) }.onSuccess { state.replicas = it }
    }
    LaunchedEffect(type, route.workId) {
        if (type != MediaType.FEED && type != MediaType.PODCAST) return@LaunchedEffect
        val a = session.api ?: return@LaunchedEffect
        val source = session.io { a.followed() }.getOrNull()?.firstOrNull { it.workId == route.workId } ?: return@LaunchedEffect
        session.io { a.followedItems(source.id) }.onSuccess { state.feedItems = it }
    }

    val seasons = remember(state.assets) { if (Series.isSeries(type.apiName ?: type.name)) Series.seasons(state.assets.orEmpty()) else emptyList() }

    MediaScope(type) {
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 32.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GhostButton(route.from, onBack, icon = Icons.Rounded.ArrowBack)
                    Spacer(Modifier.weight(1f))
                    TabSwitch(state.tab, onSelect = { state.tab = it })
                }
            }
            item {
                when {
                    state.loading && detail == null -> HeroSkeleton(340.dp)
                    detail == null -> ErrorState("Couldn't load this work", state.detailError, onRetry = ::load)
                    else -> DetailHero(session, detail, type, wants, state, seasons, onWant)
                }
            }
            if (detail != null) {
                item { SynopsisBlock(detail, type, seasons, state) }
                if (state.tab == DetailTab.WATCH) {
                    when (type) {
                        MediaType.SERIES -> item { SeasonsBlock(session, detail, seasons, state, wants) }
                        MediaType.MUSIC, MediaType.AUDIOBOOK -> item { TracksBlock(session, detail, state) }
                        MediaType.FEED, MediaType.PODCAST -> item { ArchiveBlock(session, state) }
                        else -> item { FileBlock(session, detail, state) }
                    }
                } else {
                    item { TwoColumn(
                        left = {
                            WhyPanel(session, wants, state, ::loadWants)
                            ReleasesPanel(session, wants, state, ::loadWants)
                            ExplainPanel(session, wants)
                        },
                        right = {
                            HealthPanel(session, detail, wants, state)
                            SidecarsPanel(session, state, seasons, wants)
                            DetailsPanel(detail, state, type)
                            FilesPanel(state.assets.orEmpty())
                        },
                    ) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TabSwitch(current: DetailTab, onSelect: (DetailTab) -> Unit) {
    Row(Modifier.background(Tokens.surface1, CircleShape).border(Tokens.hairline, Tokens.border, CircleShape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (t in DetailTab.entries) {
            val active = t == current
            val theme = LocalMediaTheme.current
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier.focusRing(interaction, CircleShape).clip(CircleShape)
                    .background(if (active) theme.tint(0.22f) else Color.Transparent, CircleShape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = { onSelect(t) })
                    .semantics { contentDescription = t.label + if (active) ", selected" else "" }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(if (t == DetailTab.WATCH) Icons.Rounded.PlayArrow else Icons.Rounded.Build, contentDescription = null, tint = if (active) theme.accentGradientEnd else Tokens.textMuted, modifier = Modifier.size(14.dp))
                Text(t.label, style = MaterialTheme.typography.labelLarge, color = if (active) Tokens.textPrimary else Tokens.textMuted)
            }
        }
    }
}

/** Play a blob in mpv on this machine, reporting through toasts. */
private fun playLocal(session: AppSession, state: DetailState, blobHash: String, label: String, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        state.busy = "play"
        val r = session.io { session.player.play(session.config.baseUrl, blobHash, session.config.bearerToken.trim()) }.getOrNull()
        state.busy = null
        when (r) {
            is PlayResult.Launched -> session.toast(Toast.Kind.SUCCESS, "Playing in mpv", label)
            is PlayResult.Failed -> session.toast(Toast.Kind.ERROR, "Couldn't play here", r.message)
            null -> {}
        }
    }
}

@Composable
private fun DetailHero(session: AppSession, detail: WorkDetail, type: MediaType, wants: List<DesiredItem>, state: DetailState, seasons: List<Season>, onWant: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val art by session.artwork.rememberArtwork(detail.artworkPath)
    val theme = MediaThemes.of(type)
    val status = session.index.statusOf(detail.work.id)
    val asset = detail.primaryAsset
    val work = detail.work
    val cont = state.continueEntry
    val first = Series.firstPlayable(seasons)
    val held = seasons.sumOf { it.held }
    val meta = when (type) {
        MediaType.SERIES -> listOf(work.year?.toString(), if (seasons.isNotEmpty()) "${seasons.count { it.number != null && it.number != 0 }} seasons" else null, if (state.assets != null) "$held episodes held" else null)
        MediaType.MOVIE -> listOf(work.year?.toString(), asset?.let { Series.qualityTags(Track("x", "x", filename = state.assets?.firstOrNull { t -> t.blobHash == it.blobHash }?.filename)).joinToString(" · ").ifBlank { null } }, asset?.sizeBytes?.let { PrimaryAsset.formatBytes(it) })
        MediaType.BOOK -> listOf(work.author, work.year?.toString(), asset?.mime?.substringAfter('/')?.uppercase())
        MediaType.MUSIC -> listOf(work.artist, work.year?.toString(), "${state.assets.orEmpty().count { it.isAudio && it.isPlayable }} tracks")
        else -> listOf(work.year?.toString(), work.kind)
    }

    fun openLocal() {
        val a = asset ?: return
        scope.launch {
            state.busy = "open"
            val msg = session.io { session.openExternally.open(session.config.baseUrl, a.blobHash, session.config.bearerToken.trim(), null, a.mime, work.title) }.getOrNull()
            state.busy = null
            if (msg != null) session.toast(Toast.Kind.INFO, msg)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hero(
            title = work.title, type = type, meta = meta, artwork = art, status = status, height = 340.dp,
            kicker = cont?.let { "Continue · ${it.editionLabel ?: ""} ${it.progressLabel ?: ""}".trim() },
            primary = {
                when {
                    cont?.blobHash != null && type != MediaType.BOOK -> PrimaryButton("Continue", { playLocal(session, state, cont.blobHash, "${work.title} — ${cont.editionLabel ?: ""}", scope) }, icon = Icons.Rounded.PlayArrow, enabled = state.busy == null)
                    type == MediaType.SERIES && first != null -> PrimaryButton("Play ${first.code ?: ""}".trim(), { playLocal(session, state, first.asset.blobHash!!, Series.playTitle(work, first), scope) }, icon = Icons.Rounded.PlayArrow, enabled = state.busy == null)
                    asset == null && wants.isNotEmpty() -> PrimaryButton("Look for it", {
                        val a = session.api ?: return@PrimaryButton
                        val w = wants.first()
                        scope.launch {
                            state.busy = "search"
                            session.io { a.searchReleases(w.id) }.onSuccess { r -> when (r) { is McpResult.Ok -> session.toast(Toast.Kind.INFO, "Search queued", "An indexer can take thirty seconds to answer; open Curate → Releases in a moment."); is McpResult.Refused -> session.refused(r) } }
                            state.busy = null
                        }
                    }, icon = Icons.Rounded.Search, enabled = state.busy == null)
                    asset == null -> PrimaryButton("Want", { onWant(work.id, work.title) }, icon = Icons.Rounded.Add, enabled = status == LibraryStatus.NOT_TRACKED)
                    type == MediaType.BOOK || type == MediaType.FEED -> PrimaryButton(theme.ctaLabel, ::openLocal, icon = if (type == MediaType.BOOK) Icons.Rounded.MenuBook else Icons.Rounded.OpenInNew, enabled = state.busy == null)
                    else -> PrimaryButton(theme.ctaLabel, { playLocal(session, state, asset.blobHash, work.title, scope) }, icon = Icons.Rounded.PlayArrow, enabled = state.busy == null)
                }
            },
            secondary = {
                val castId = if (type == MediaType.SERIES) (first?.asset?.id) else asset?.assetId
                if (castId != null && type != MediaType.BOOK && type != MediaType.FEED) SecondaryButton("Play on…", { toggleCast(session, state, castId, scope) }, icon = Icons.Rounded.Cast)
                if (status == LibraryStatus.NOT_TRACKED && (asset != null || type == MediaType.SERIES)) SecondaryButton("Want", { onWant(work.id, work.title) }, icon = Icons.Rounded.Add)
            },
        )
        if (asset == null && type != MediaType.SERIES && type != MediaType.FEED && type != MediaType.PODCAST) Notice("Nothing to play yet — ${if (wants.isEmpty()) "not wanted, so nothing is looking for a copy." else "heyarr is looking. Curate → Releases shows what the indexers found."}")
        CastPicker(session, state)
    }
}

private fun toggleCast(session: AppSession, state: DetailState, assetId: String, scope: kotlinx.coroutines.CoroutineScope) {
    state.castAssetId = if (state.castAssetId == assetId) null else assetId
    if (state.renderers == null) scope.launch { session.api?.let { a -> session.io { a.renderers() }.onSuccess { state.renderers = it } } }
}

@Composable
private fun CastPicker(session: AppSession, state: DetailState) {
    val scope = rememberCoroutineScope()
    val assetId = state.castAssetId ?: return
    fun playOn(renderer: Renderer) {
        val api = session.api ?: return
        state.castAssetId = null
        scope.launch {
            state.busy = "cast"
            session.io { api.playHere(assetId, renderer.name) }.onSuccess { r -> when (r) { is McpResult.Ok -> session.toast(Toast.Kind.SUCCESS, "Playing on ${renderer.name}"); is McpResult.Refused -> session.refused(r) } }
            state.busy = null
        }
    }
    Panel("Play on a renderer", trailing = { GhostButton("Close", { state.castAssetId = null }) }) {
        val r = state.renderers
        when {
            r == null -> Skeleton(Modifier.fillMaxWidth().height(36.dp))
            r.isEmpty() -> Text("No renderers found. A device that is switched off will not be listed — that is not the same as it not existing.", style = MaterialTheme.typography.bodyMedium, color = Tokens.textMuted)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (x in r) FilterChip(x.name, false, { playOn(x) }, icon = Icons.Rounded.Cast) }
        }
        GhostButton("Search the network again", { scope.launch { session.api?.let { a -> session.io { a.renderers(refresh = true) }.onSuccess { state.renderers = it } } } })
    }
}

/** The synopsis, when the node has one — and an honest line when it has not. */
@Composable
private fun SynopsisBlock(detail: WorkDetail, type: MediaType, seasons: List<Season>, state: DetailState) {
    val synopsis = listOf("overview", "synopsis", "description", "summary").firstNotNullOfOrNull { k -> detail.attributes[k]?.takeIf { it.isNotBlank() } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (synopsis != null) Text(synopsis, style = MaterialTheme.typography.bodyLarge, color = Tokens.textPrimary)
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = Tokens.textDisabled, modifier = Modifier.size(14.dp))
            Text("No synopsis on this node — it needs a metadata provider (TVDB, ADR-0058) to fetch one. Titles, seasons and episodes below come from the files themselves.", style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
        }
    }
}

/** Seasons as chips, then the selected season's episodes with the thumbnails the scan already recorded. */
@Composable
private fun SeasonsBlock(session: AppSession, detail: WorkDetail, seasons: List<Season>, state: DetailState, wants: List<DesiredItem>) {
    val scope = rememberCoroutineScope()
    if (state.assets == null) { MediaRowSkeleton(5); return }
    if (seasons.isEmpty()) { Notice("No episode files are held for this series yet.${if (wants.isNotEmpty()) " heyarr is looking — Curate → Releases shows what it found." else ""}"); return }
    val selected = seasons.firstOrNull { it.number == state.season } ?: seasons.first()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Episodes", subtitle = "${selected.held} of ${selected.episodes.size} held${selected.gaps.takeIf { it.isNotEmpty() }?.let { " · ${it.size} not held" } ?: ""}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in seasons) FilterChip(s.label, s == selected, { state.season = s.number }, count = s.episodes.size)
        }
        val rows: List<Any> = buildList {
            val byNumber = selected.episodes.associateBy { it.number }
            val max = selected.episodes.mapNotNull { it.number }.maxOrNull() ?: 0
            for (n in 1..max) add(byNumber[n] ?: n)
            addAll(selected.episodes.filter { it.number == null || it.number > max })
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in rows) when (row) {
                is Episode -> EpisodeRow(session, detail, row, state)
                is Int -> MissingEpisodeRow(session, selected, row, wants, state)
            }
        }
    }
}

@Composable
private fun EpisodeRow(session: AppSession, detail: WorkDetail, ep: Episode, state: DetailState) {
    val scope = rememberCoroutineScope()
    val theme = LocalMediaTheme.current
    val thumb by session.artwork.rememberArtwork(ep.thumbnailPath)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(Tokens.radiusInput)
    val cont = state.continueEntry
    val isContinue = cont != null && (if (cont.assetId != null) cont.assetId == ep.asset.id else cont.blobHash != null && cont.blobHash == ep.asset.blobHash)
    Row(
        Modifier.fillMaxWidth().focusRing(interaction, shape).clip(shape)
            .background(if (hovered) Tokens.surface2 else Tokens.surface1, shape).border(Tokens.hairline, if (isContinue) theme.accent.copy(alpha = 0.6f) else Tokens.border, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, enabled = ep.isPlayable, onClick = { ep.asset.blobHash?.let { playLocal(session, state, it, Series.playTitle(detail.work, ep), scope) } })
            .semantics { contentDescription = "${ep.label}${if (!ep.isPlayable) ", file missing" else ""}" }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(152.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
            Artwork(thumb, MediaType.SERIES, Modifier.fillMaxSize(), glyphSize = 22.dp)
            if (hovered && ep.isPlayable) Box(Modifier.fillMaxSize().background(Tokens.bgBase.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp).background(Brush.linearGradient(listOf(theme.ctaGradientStart, theme.accentGradientEnd)), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = theme.onAccent, modifier = Modifier.size(22.dp))
                }
            }
            state.continueEntry?.takeIf { isContinue }?.fraction?.let { f ->
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Tokens.bgBase.copy(alpha = 0.5f))) {
                    Box(Modifier.fillMaxWidth(f).height(4.dp).background(theme.accent))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ep.code?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = theme.accentGradientEnd) }
                Text(ep.title ?: ep.asset.filename ?: ep.asset.id, style = MaterialTheme.typography.titleMedium, color = if (ep.isPlayable) Tokens.textPrimary else Tokens.textDisabled, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isContinue) Text("continue · ${state.continueEntry?.progressLabel}", style = MaterialTheme.typography.labelSmall, color = theme.accentGradientEnd)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (tag in Series.qualityTags(ep.asset)) RuleCode(tag, tone = Tokens.textMuted)
                ep.asset.sizeBytes?.let { Text(PrimaryAsset.formatBytes(it), style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted) }
                if (ep.subtitles.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Rounded.ClosedCaption, contentDescription = null, tint = Tokens.textMuted, modifier = Modifier.size(14.dp))
                    Text(ep.subtitles.size.toString(), style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted)
                }
                if (!ep.isPlayable) Text("file missing since ${ep.asset.missingSince?.take(10)}", style = MaterialTheme.typography.labelSmall, color = Tokens.danger)
            }
        }
        if (ep.isPlayable) {
            IconButtonRound(Icons.Rounded.Cast, "Play ${ep.label} on a renderer", { toggleCast(session, state, ep.asset.id, scope) }, size = 34.dp)
            IconButtonRound(Icons.Rounded.PlayArrow, "Play ${ep.label} here", { ep.asset.blobHash?.let { playLocal(session, state, it, Series.playTitle(detail.work, ep), scope) } }, size = 34.dp, filled = true, enabled = state.busy == null)
        }
    }
}

/** A numbered gap in a season: nothing held, and the one honest action — ask the indexers. */
@Composable
private fun MissingEpisodeRow(session: AppSession, season: Season, number: Int, wants: List<DesiredItem>, state: DetailState) {
    val scope = rememberCoroutineScope()
    val code = "S%02dE%02d".format(season.number ?: 0, number)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.radiusInput)).border(Tokens.hairline, Tokens.border.copy(alpha = 0.6f), RoundedCornerShape(Tokens.radiusInput)).padding(8.dp)
            .semantics { contentDescription = "$code not held" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(152.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Tokens.surface1), contentAlignment = Alignment.Center) {
            Text("not held", style = MaterialTheme.typography.labelSmall, color = Tokens.textDisabled)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(code, style = MaterialTheme.typography.labelMedium, color = Tokens.textDisabled)
                Text("Not held", style = MaterialTheme.typography.titleMedium, color = Tokens.textDisabled)
            }
            Text(if (wants.isEmpty()) "Want this series and heyarr will look for it." else "Wanted — heyarr searches on its schedule; ask now to jump the queue.", style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted)
        }
        if (wants.isNotEmpty()) SecondaryButton("Look for it", {
            val a = session.api ?: return@SecondaryButton
            scope.launch { session.io { a.searchReleases(wants.first().id) }.onSuccess { r -> when (r) { is McpResult.Ok -> session.toast(Toast.Kind.INFO, "Search queued for ${season.label}", "Results land under Curate → Releases."); is McpResult.Refused -> session.refused(r) } } }
        }, icon = Icons.Rounded.Search, compact = true)
    }
}

@Composable
private fun TracksBlock(session: AppSession, detail: WorkDetail, state: DetailState) {
    val scope = rememberCoroutineScope()
    val assets = state.assets
    if (assets == null) { MediaRowSkeleton(5); return }
    val tracks = assets.filter { it.isAudio && it.isPrimaryRole }.sortedWith(compareBy({ it.filename?.lowercase() ?: "" }, { it.id }))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Tracks", subtitle = "${tracks.count { it.isPlayable }} playable", trailing = {
            val playable = tracks.filter { it.isPlayable }.mapNotNull { it.blobHash }
            if (playable.isNotEmpty()) PrimaryButton("Queue all", { scope.launch { val r = session.io { session.player.playAll(session.config.baseUrl, playable, session.config.bearerToken.trim()) }.getOrNull(); if (r is PlayResult.Failed) session.toast(Toast.Kind.ERROR, "Couldn't play", r.message) else session.toast(Toast.Kind.SUCCESS, "Queued ${playable.size} tracks in mpv") } }, icon = Icons.Rounded.PlayArrow, compact = true)
        })
        if (tracks.isEmpty()) Notice("No audio files held for this work yet.")
        for ((i, t) in tracks.withIndex()) {
            val theme = LocalMediaTheme.current
            Row(Modifier.fillMaxWidth().background(Tokens.surface1, RoundedCornerShape(Tokens.radiusInput)).border(Tokens.hairline, Tokens.border, RoundedCornerShape(Tokens.radiusInput)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("%02d".format(i + 1), style = MaterialTheme.typography.labelMedium, color = theme.accentGradientEnd, modifier = Modifier.width(28.dp))
                Text(t.title, style = MaterialTheme.typography.titleSmall, color = if (t.isPlayable) Tokens.textPrimary else Tokens.textDisabled, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                t.sizeBytes?.let { Text(PrimaryAsset.formatBytes(it), style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted) }
                if (t.isPlayable) IconButtonRound(Icons.Rounded.PlayArrow, "Play ${t.title}", { t.blobHash?.let { playLocal(session, state, it, "${detail.work.title} — ${t.title}", scope) } }, size = 32.dp, filled = true)
            }
        }
    }
}

@Composable
private fun ArchiveBlock(session: AppSession, state: DetailState) {
    val scope = rememberCoroutineScope()
    val items = state.feedItems
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Archive", subtitle = items?.let { "${it.count { i -> i.archived }} of ${it.size} archived" })
        when {
            items == null -> MediaRowSkeleton(4)
            items.isEmpty() -> Notice("Nothing archived yet — the node polls this source on its schedule.")
            else -> for (item in items) Row(Modifier.fillMaxWidth().background(Tokens.surface1, RoundedCornerShape(Tokens.radiusInput)).border(Tokens.hairline, Tokens.border, RoundedCornerShape(Tokens.radiusInput)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, color = if (item.archived) Tokens.textPrimary else Tokens.textDisabled)
                    Text(listOfNotNull(item.publishedAt?.take(10), if (item.archived) "archived" else "not archived yet").joinToString("  ·  "), style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted)
                }
                if (item.archived && item.workId != null) SecondaryButton("Open", {
                    val a = session.api ?: return@SecondaryButton
                    scope.launch {
                        val d = session.io { a.work(item.workId) }.getOrNull()
                        val asset = d?.primaryAsset
                        if (asset == null) session.toast(Toast.Kind.INFO, "No archived bytes held for this item yet.")
                        else session.io { session.openExternally.open(session.config.baseUrl, asset.blobHash, session.config.bearerToken.trim(), null, asset.mime ?: "text/html", item.title) }.getOrNull()?.let { session.toast(Toast.Kind.INFO, it) }
                    }
                }, icon = Icons.Rounded.OpenInNew, compact = true)
            }
        }
    }
}

/** A film / single-file work: the one file, its quality, and what plays it. */
@Composable
private fun FileBlock(session: AppSession, detail: WorkDetail, state: DetailState) {
    val asset = detail.primaryAsset ?: return
    val file = state.assets?.firstOrNull { it.blobHash == asset.blobHash }
    Panel("This copy") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            for (tag in file?.let { Series.qualityTags(it) }.orEmpty()) RuleCode(tag, tone = Tokens.textMuted)
            asset.mime?.let { RuleCode(it, tone = Tokens.textMuted) }
            asset.sizeBytes?.let { Text(PrimaryAsset.formatBytes(it), style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted) }
        }
        val subs = state.assets.orEmpty().filter { Series.isSubtitle(it) }
        Text(if (subs.isEmpty()) "No captions held." else "Captions: " + subs.joinToString(", ") { it.filename?.substringAfterLast('.', "")?.let { ext -> it.filename!!.removeSuffix(".$ext").substringAfterLast('.') } ?: "?" }, style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
    }
}

/** Curate → captions and artwork: what is held per episode, and the honest limits of what the node can fetch. */
@Composable
private fun SidecarsPanel(session: AppSession, state: DetailState, seasons: List<Season>, wants: List<DesiredItem>) {
    val assets = state.assets.orEmpty()
    val subs = assets.filter { Series.isSubtitle(it) }
    val art = assets.filter { it.role == "artwork" }
    Panel("Captions & artwork") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.ClosedCaption, contentDescription = null, tint = Tokens.textMuted, modifier = Modifier.size(16.dp))
            Text("${subs.size} caption file${if (subs.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium, color = Tokens.textPrimary)
            if (seasons.isNotEmpty()) Text("· ${seasons.sumOf { s -> s.episodes.count { it.subtitles.isEmpty() && it.isPlayable } }} held episodes without captions", style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Image, contentDescription = null, tint = Tokens.textMuted, modifier = Modifier.size(16.dp))
            Text("${art.size} artwork file${if (art.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium, color = Tokens.textPrimary)
            if (seasons.isNotEmpty()) Text("· ${seasons.sumOf { s -> s.episodes.count { it.thumbnail == null } }} episodes without a thumbnail", style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
        }
        Notice("heyarr's tool surface has no caption or artwork search yet: sidecars arrive with a release or a scan. Asking the indexers again (Releases) is the only fetch this node can queue.", tone = Tokens.slate)
    }
}

@Composable
private fun TwoColumn(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(20.dp)) { left() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) { right() }
    }
}

@Composable
private fun WhyPanel(session: AppSession, wants: List<DesiredItem>, state: DetailState, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    Panel("Why this release", trailing = { GhostButton("Refresh", reload) }) {
        if (wants.isEmpty()) { Text("Not wanted, so there is no profile to measure against. Want it to see every rule heyarr would apply.", style = MaterialTheme.typography.bodyMedium, color = Tokens.textMuted); return@Panel }
        for (w in wants) {
            val profile = session.profiles.firstOrNull { it.id == w.qualityProfileId }?.name ?: w.qualityProfileId ?: "?"
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(LibraryStatus.ofState(w.state))
                    Text(w.state.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelMedium, color = Tokens.textMuted)
                    Text("·  profile $profile", style = MaterialTheme.typography.labelMedium, color = Tokens.textMuted, modifier = Modifier.weight(1f))
                    FilterChip(if (w.monitor) "Monitoring" else "Not monitoring", w.monitor, {
                        val a = session.api ?: return@FilterChip
                        scope.launch { session.io { a.monitor(w.id, !w.monitor) }.onSuccess { r -> if (r is McpResult.Refused) session.refused(r) else session.refreshIndex() } }
                    })
                }
                if (w.detail != null) Text(w.detail, style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
                when (val s = state.satisfaction[w.id]) {
                    null -> Skeleton(Modifier.fillMaxWidth().height(48.dp))
                    is McpResult.Refused -> Notice("get_content_satisfaction: ${s.message}", tone = Tokens.danger)
                    is McpResult.Ok -> {
                        val sat = s.value
                        if (sat == null) Text("No satisfaction report.", color = Tokens.textMuted, style = MaterialTheme.typography.bodySmall)
                        else {
                            KeyValue("content", sat.contentSatisfaction.replace('_', ' '), valueColor = verdictColor(if (sat.contentSatisfaction == "satisfied") "pass" else "fail"))
                            KeyValue("placement", if (sat.placementUnproven) "unproven — single-node fabric, nowhere to converge to" else sat.placementSatisfaction + (sat.placementDetail.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
                            KeyValue("upgrade", (if (sat.upgradeEligible) "eligible" else sat.upgradeStatus.replace('_', ' ')) + (sat.upgradeDetail.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""))
                            if (sat.assets.isEmpty()) Text("Nothing is held for this want; the rules below apply to candidates instead.", style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
                            for (asset in sat.assets) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if (asset.accepted) "accepted" else "not accepted", style = MaterialTheme.typography.labelMedium, color = verdictColor(if (asset.accepted) "pass" else "fail"))
                                    Text("asset ${asset.assetId.takeLast(8)} · score ${asset.score}${if (asset.terminal) " · terminal" else ""}", style = MaterialTheme.typography.labelSmall, color = Tokens.textDisabled)
                                }
                                ReasonList(asset.reasons)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleasesPanel(session: AppSession, wants: List<DesiredItem>, state: DetailState, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    if (wants.isEmpty()) return
    Panel("Releases", trailing = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (w in wants.take(1)) SecondaryButton("Search indexers now", {
                val a = session.api ?: return@SecondaryButton
                scope.launch {
                    session.io { a.searchReleases(w.id) }.onSuccess { r ->
                        when (r) {
                            is McpResult.Ok -> session.toast(Toast.Kind.INFO, "Search queued", "An indexer can take thirty seconds to answer; refresh Releases in a moment." + (r.value.jobId?.let { " Job $it." } ?: ""))
                            is McpResult.Refused -> session.refused(r)
                        }
                    }
                }
            }, icon = Icons.Rounded.Search, compact = true)
        }
    }) {
        for (w in wants) {
            val list = state.candidates[w.id]
            when {
                list == null -> Skeleton(Modifier.fillMaxWidth().height(40.dp))
                list.isEmpty() -> Text("The last search found no candidates${w.detail?.let { " — $it" } ?: ""}.", style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
                else -> for (c in list) CandidateRow(session, w, c, state, reload)
            }
        }
    }
}

@Composable
private fun CandidateRow(session: AppSession, want: DesiredItem, c: Candidate, state: DetailState, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.title, style = MaterialTheme.typography.titleSmall, color = Tokens.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (c.accepted) "accepted" else "rejected", style = MaterialTheme.typography.labelMedium, color = verdictColor(if (c.accepted) "pass" else "fail"))
                    Text("score ${c.score}", style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted)
                    c.provider?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted) }
                    c.sizeBytes?.let { Text(PrimaryAsset.formatBytes(it), style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted) }
                    if (c.selected) Text("selected", style = MaterialTheme.typography.labelSmall, color = LocalMediaTheme.current.accentGradientEnd)
                }
            }
            GhostButton(if (expanded) "Hide rules" else "Rules (${c.reasons.size})", { expanded = !expanded })
            PrimaryButton("Acquire", {
                val a = session.api ?: return@PrimaryButton
                scope.launch {
                    state.busy = c.candidateId
                    session.io { a.acquire(want.id, c.candidateId) }.onSuccess { r ->
                        when (r) {
                            is McpResult.Ok -> { session.toast(Toast.Kind.SUCCESS, "Acquiring", c.title); session.refreshIndex(); reload() }
                            is McpResult.Refused -> session.refused(r)
                        }
                    }
                    state.busy = null
                }
            }, icon = Icons.Rounded.Download, compact = true, enabled = state.busy == null)
        }
        RejectedBy(c.rejectedBy)
        if (expanded) ReasonList(c.reasons)
    }
}

/** "Would this be accepted?" — describe a release, get every rule back. Absent fields stay absent so they read as undetermined. */
@Composable
private fun ExplainPanel(session: AppSession, wants: List<DesiredItem>) {
    val scope = rememberCoroutineScope()
    val profiles = session.profiles
    var profile by remember(profiles) { mutableStateOf(wants.firstOrNull()?.let { w -> profiles.firstOrNull { it.id == w.qualityProfileId }?.name } ?: profiles.firstOrNull()?.name ?: "") }
    var title by remember { mutableStateOf("") }
    var resolution by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var codec by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<McpResult<Explanation?>?>(null) }
    var busy by remember { mutableStateOf(false) }
    Panel("Score a release") {
        Text("Describe a release and heyarr explains, rule by rule, whether the profile would accept it. Leave a field blank when you do not know — a blank reads as undetermined, a guess reads as a claim.", style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (p in profiles) FilterChip(p.name, profile == p.name, { profile = p.name }) }
        Field("Title", title) { title = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Resolution (480/720/1080/2160)", resolution, Modifier.weight(1f)) { resolution = it.filter { c -> c.isDigit() } }
            Field("Source (remux/bluray/web-dl/…)", source, Modifier.weight(1f)) { source = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Video codec", codec, Modifier.weight(1f)) { codec = it }
            Field("Size (bytes)", size, Modifier.weight(1f)) { size = it.filter { c -> c.isDigit() } }
        }
        PrimaryButton("Explain", {
            val a = session.api ?: return@PrimaryButton
            busy = true
            scope.launch {
                val rel = ReleaseToExplain("candidate", title.ifBlank { "untitled release" }, ReleaseAttributes(resolution = resolution.toIntOrNull(), source = source, videoCodec = codec, sizeBytes = size.toLongOrNull()))
                session.io { a.explain(profile, listOf(rel)) }.onSuccess { result = it }
                busy = false
            }
        }, icon = Icons.Rounded.Verified, compact = true, enabled = !busy && profile.isNotBlank())
        when (val r = result) {
            null -> {}
            is McpResult.Refused -> Notice("explain_release: ${r.message}", tone = Tokens.danger)
            is McpResult.Ok -> r.value?.ranked?.firstOrNull()?.let { ranked ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (ranked.accepted) "Would be accepted" else "Would be rejected", style = MaterialTheme.typography.titleSmall, color = verdictColor(if (ranked.accepted) "pass" else "fail"))
                    Text("score ${ranked.score}${if (ranked.terminal) " · terminal" else ""}", style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted)
                }
                RejectedBy(ranked.rejectedBy)
                ReasonList(ranked.reasons)
            } ?: Text("No verdict returned.", color = Tokens.textMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun Field(label: String, value: String, modifier: Modifier = Modifier, placeholder: String? = null, secret: Boolean = false, onChange: (String) -> Unit) {
    val accent = LocalMediaTheme.current.accent
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted)
        Box(
            Modifier.fillMaxWidth().background(Tokens.surface2, RoundedCornerShape(Tokens.radiusInput))
                .border(if (focused) 2.dp else Tokens.hairline, if (focused) accent else Tokens.border, RoundedCornerShape(Tokens.radiusInput))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (value.isEmpty() && placeholder != null) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = Tokens.textDisabled)
            BasicTextField(
                value, onChange, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = Tokens.textPrimary), cursorBrush = SolidColor(accent),
                interactionSource = interaction, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            )
        }
    }
}

@Composable
private fun HealthPanel(session: AppSession, detail: WorkDetail, wants: List<DesiredItem>, state: DetailState) {
    val scope = rememberCoroutineScope()
    val hash = detail.primaryAsset?.blobHash
    Panel("Health") {
        if (hash == null) { Text("No held bytes to check.", style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted); return@Panel }
        KeyValue("blob", hash.take(16) + "…", valueColor = Tokens.textMuted)
        when (val r = state.replicas) {
            null -> Skeleton(Modifier.fillMaxWidth().height(20.dp))
            is McpResult.Refused -> Notice("get_replica_status: ${r.message}", tone = Tokens.danger)
            is McpResult.Ok -> if (r.value.isEmpty()) Text("No replica report — on a single-node fabric there is nowhere for bytes to converge to.", style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
            else for (rep in r.value) KeyValue(rep.peer, "${rep.state}${if (rep.verified) " · verified" else " · not verified"}", valueColor = if (rep.verified) verdictColor("pass") else Tokens.ratingGold)
        }
        SecondaryButton("Verify bytes now", {
            val a = session.api ?: return@SecondaryButton
            scope.launch { session.io { a.verifyBlob(hash) }.onSuccess { r -> when (r) { is McpResult.Ok -> session.toast(Toast.Kind.INFO, "Verification queued", "Re-hashing runs as a job; the answer lands on the job, not here."); is McpResult.Refused -> session.refused(r) } } }
        }, icon = Icons.Rounded.Verified, compact = true)
    }
}

@Composable
private fun DetailsPanel(detail: WorkDetail, state: DetailState, type: MediaType) {
    Panel("Details") {
        KeyValue("type", type.label)
        detail.work.year?.let { KeyValue("year", it.toString()) }
        detail.work.workKey?.let { KeyValue("work key", it, valueColor = Tokens.textMuted) }
        detail.primaryAsset?.let { a ->
            a.mime?.let { KeyValue("primary file", it) }
            a.sizeBytes?.let { KeyValue("size", PrimaryAsset.formatBytes(it)) }
        }
        if (state.externalIds.isEmpty()) KeyValue("external ids", "none recorded", valueColor = Tokens.textMuted)
        else for (e in state.externalIds) KeyValue(e.source, e.value)
    }
}

@Composable
private fun FilesPanel(assets: List<Track>) {
    Panel("Files (${assets.size})") {
        if (assets.isEmpty()) Text("No files scanned for this work.", style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
        for (t in assets.take(30)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleCode(t.role ?: "primary", tone = Tokens.textMuted)
                Text(t.filename ?: t.id, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Default), color = if (t.isPlayable) Tokens.textPrimary else Tokens.textDisabled, modifier = Modifier.weight(1f), maxLines = 1)
                t.sizeBytes?.let { Text(PrimaryAsset.formatBytes(it), style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted) }
                if (t.missingSince != null) Text("missing", style = MaterialTheme.typography.labelSmall, color = Tokens.danger)
            }
        }
        if (assets.size > 30) Text("…and ${assets.size - 30} more", style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted)
    }
}
