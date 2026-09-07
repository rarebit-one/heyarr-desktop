package one.rarebit.heyarr.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.rarebit.heyarr.desktop.heyarr.Candidate
import one.rarebit.heyarr.desktop.heyarr.DesiredItem
import one.rarebit.heyarr.desktop.heyarr.McpResult
import one.rarebit.heyarr.desktop.library.PrimaryAsset
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
import one.rarebit.heyarr.desktop.ui.components.ErrorState
import one.rarebit.heyarr.desktop.ui.components.FilterChip
import one.rarebit.heyarr.desktop.ui.components.GhostButton
import one.rarebit.heyarr.desktop.ui.components.Hero
import one.rarebit.heyarr.desktop.ui.components.HeroSkeleton
import one.rarebit.heyarr.desktop.ui.components.KeyValue
import one.rarebit.heyarr.desktop.ui.components.Notice
import one.rarebit.heyarr.desktop.ui.components.Panel
import one.rarebit.heyarr.desktop.ui.components.PrimaryButton
import one.rarebit.heyarr.desktop.ui.components.ReasonList
import one.rarebit.heyarr.desktop.ui.components.RejectedBy
import one.rarebit.heyarr.desktop.ui.components.RuleCode
import one.rarebit.heyarr.desktop.ui.components.SecondaryButton
import one.rarebit.heyarr.desktop.ui.components.Skeleton
import one.rarebit.heyarr.desktop.ui.components.StatusPill
import one.rarebit.heyarr.desktop.ui.components.verdictColor

/** Everything the detail screen loads for one work, each piece independently. */
class DetailState(val workId: String) {
    var detail by mutableStateOf<WorkDetail?>(null)
    var detailError by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var assets by mutableStateOf<List<Track>>(emptyList())
    var externalIds by mutableStateOf<List<ExternalId>>(emptyList())
    var satisfaction by mutableStateOf<Map<String, McpResult<Satisfaction?>>>(emptyMap())
    var candidates by mutableStateOf<Map<String, List<Candidate>>>(emptyMap())
    var replicas by mutableStateOf<McpResult<List<Replica>>?>(null)
    var renderers by mutableStateOf<List<Renderer>?>(null)
    var busy by mutableStateOf<String?>(null)
}

/**
 * The one adaptive detail template. It re-skins per media type (hero art + scrim, the
 * type's own CTA verb, its metadata chips) and is honest about what heyarr can say:
 * "Why this release" is `get_content_satisfaction` and the candidates list rule by
 * rule, quoted verbatim; health is placement + `get_replica_status`; playback goes
 * either to a renderer (`play_here`) or to mpv on this machine.
 */
@Composable
fun DetailScreen(session: AppSession, route: Route.Detail, state: DetailState, onBack: () -> Unit, onOpen: (Route) -> Unit, onWant: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val wants: List<DesiredItem> = session.index.wantsFor(route.workId)
    val detail = state.detail
    val type = detail?.work?.kind?.let { MediaType.from(it) } ?: route.typeHint
    val theme = MediaThemes.of(type)

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
    }
    fun loadWants() {
        val a = session.api ?: return
        for (w in wants) {
            if (w.id.startsWith("pending:")) continue
            scope.launch { session.io { a.satisfaction(w.id) }.onSuccess { r -> state.satisfaction = state.satisfaction + (w.id to r) } }
            scope.launch { session.io { a.candidates(w.id) }.onSuccess { c -> state.candidates = state.candidates + (w.id to (c?.candidates ?: emptyList())) } }
        }
    }
    LaunchedEffect(route.workId) { load() }
    LaunchedEffect(wants.map { it.id }) { loadWants() }
    LaunchedEffect(detail?.primaryAsset?.blobHash) {
        val hash = detail?.primaryAsset?.blobHash ?: return@LaunchedEffect
        val a = session.api ?: return@LaunchedEffect
        session.io { a.replicas(hash) }.onSuccess { state.replicas = it }
    }

    MediaScope(type) {
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 32.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(route.from, onBack, icon = Icons.Rounded.ArrowBack)
                }
            }
            item {
                when {
                    state.loading && detail == null -> HeroSkeleton(340.dp)
                    detail == null -> ErrorState("Couldn't load this work", state.detailError, onRetry = ::load)
                    else -> DetailHero(session, detail, type, wants, state, onWant)
                }
            }
            if (detail != null) {
                item { TwoColumn(
                    left = {
                        WhyPanel(session, wants, state, ::loadWants)
                        ReleasesPanel(session, wants, state, ::loadWants)
                        ExplainPanel(session, wants)
                    },
                    right = {
                        HealthPanel(session, detail, wants, state)
                        DetailsPanel(detail, state, type)
                        FilesPanel(state.assets)
                    },
                ) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
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
private fun DetailHero(session: AppSession, detail: WorkDetail, type: MediaType, wants: List<DesiredItem>, state: DetailState, onWant: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val art by session.artwork.rememberArtwork(detail.artworkPath)
    val theme = MediaThemes.of(type)
    val status = session.index.statusOf(detail.work.id)
    val asset = detail.primaryAsset
    var pickRenderer by remember { mutableStateOf(false) }
    val work = detail.work
    val meta = when (type) {
        MediaType.MOVIE -> listOf(work.year?.toString(), asset?.mime, asset?.sizeBytes?.let { PrimaryAsset.formatBytes(it) })
        MediaType.SERIES -> listOf(work.year?.toString(), "${state.assets.count { it.isPrimaryRole && it.isPlayable }} episodes held")
        MediaType.BOOK -> listOf(work.author, work.year?.toString(), asset?.mime)
        MediaType.MUSIC -> listOf(work.artist, work.year?.toString(), "${state.assets.count { it.isAudio && it.isPlayable }} tracks")
        else -> listOf(work.year?.toString(), work.kind, asset?.mime)
    }

    fun playLocal() {
        val a = asset ?: return
        scope.launch {
            state.busy = "play"
            val r = session.io { session.player.play(session.config.baseUrl, a.blobHash, session.config.bearerToken.trim()) }.getOrNull()
            state.busy = null
            when (r) {
                is PlayResult.Launched -> session.toast(Toast.Kind.SUCCESS, "Playing in mpv", work.title)
                is PlayResult.Failed -> session.toast(Toast.Kind.ERROR, "Couldn't play here", r.message)
                null -> {}
            }
        }
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
    fun playOn(renderer: Renderer) {
        val a = asset ?: return
        val api = session.api ?: return
        val assetId = a.assetId ?: run { session.toast(Toast.Kind.ERROR, "No asset id to play"); return }
        pickRenderer = false
        scope.launch {
            state.busy = "cast"
            session.io { api.playHere(assetId, renderer.name) }.onSuccess { r ->
                when (r) {
                    is McpResult.Ok -> session.toast(Toast.Kind.SUCCESS, "Playing on ${renderer.name}", work.title)
                    is McpResult.Refused -> session.refused(r)
                }
            }
            state.busy = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hero(
            title = work.title, type = type, meta = meta, artwork = art, status = status, height = 340.dp,
            kicker = wants.firstOrNull()?.let { "profile · ${session.profiles.firstOrNull { p -> p.id == it.qualityProfileId }?.name ?: "?"}" },
            primary = {
                when {
                    asset == null && wants.isNotEmpty() -> PrimaryButton("Search indexers now", {
                        val a = session.api ?: return@PrimaryButton
                        val w = wants.first()
                        scope.launch {
                            state.busy = "search"
                            session.io { a.searchReleases(w.id) }.onSuccess { r ->
                                when (r) {
                                    is McpResult.Ok -> session.toast(Toast.Kind.INFO, "Search queued", "An indexer can take thirty seconds to answer; refresh Releases in a moment.")
                                    is McpResult.Refused -> session.refused(r)
                                }
                            }
                            state.busy = null
                        }
                    }, icon = Icons.Rounded.Search, enabled = state.busy == null)
                    asset == null -> PrimaryButton("Want", { onWant(work.id, work.title) }, icon = Icons.Rounded.Add, enabled = status == LibraryStatus.NOT_TRACKED)
                    type == MediaType.BOOK || type == MediaType.FEED -> PrimaryButton(theme.ctaLabel, ::openLocal, icon = if (type == MediaType.BOOK) Icons.Rounded.MenuBook else Icons.Rounded.OpenInNew, enabled = state.busy == null)
                    else -> PrimaryButton("${theme.ctaLabel} here", ::playLocal, icon = Icons.Rounded.PlayArrow, enabled = state.busy == null)
                }
            },
            secondary = {
                if (asset != null && type != MediaType.BOOK && type != MediaType.FEED) SecondaryButton("Play on…", {
                    pickRenderer = !pickRenderer
                    if (state.renderers == null) scope.launch { session.api?.let { a -> session.io { a.renderers() }.onSuccess { state.renderers = it } } }
                }, icon = Icons.Rounded.Cast)
                if (asset != null && status == LibraryStatus.NOT_TRACKED) SecondaryButton("Want", { onWant(work.id, work.title) }, icon = Icons.Rounded.Add)
            },
        )
        if (asset == null) Notice("No file is held for this work yet — ${if (wants.isEmpty()) "it is not wanted, so nothing is looking for one." else "heyarr is looking (see Releases below)."}")
        if (pickRenderer) Panel("Play on a renderer", trailing = { GhostButton("Close", { pickRenderer = false }) }) {
            val r = state.renderers
            when {
                r == null -> Skeleton(Modifier.fillMaxWidth().height(36.dp))
                r.isEmpty() -> Text("No renderers found. A device that is switched off will not be listed — that is not the same as it not existing.", style = MaterialTheme.typography.bodyMedium, color = Tokens.textMuted)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (x in r) FilterChip(x.name, false, { playOn(x) }, icon = Icons.Rounded.Cast) }
            }
            GhostButton("Search the network again", { scope.launch { session.api?.let { a -> session.io { a.renderers(refresh = true) }.onSuccess { state.renderers = it } } } })
        }
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
