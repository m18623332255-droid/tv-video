package com.home.tvvideo

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val RATIOS = listOf("适应屏幕", "拉伸铺满", "裁剪填充", "16:9", "4:3")

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(req: PlayReq) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { PlayerKit.build(ctx, req.headers) }
    var line by remember { mutableIntStateOf(req.line.coerceIn(0, (req.lines.size - 1).coerceAtLeast(0))) }
    var ep by remember { mutableIntStateOf(req.ep) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf("") }
    var osd by remember { mutableIntStateOf(1) }
    var osdVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var panelTab by remember { mutableIntStateOf(0) }
    var ratio by remember { mutableIntStateOf(Store.get("ratio", "0").toIntOrNull() ?: 0) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var seekAcc by remember { mutableIntStateOf(0) }
    val rootFr = remember { FocusRequester() }
    val panelFr = remember { FocusRequester() }
    val eps = req.lines.getOrNull(line)?.eps ?: emptyList()

    fun save() {
        val e = eps.getOrNull(ep) ?: return
        val p = player.currentPosition; val d = player.duration.takeIf { it > 0 } ?: 0
        val key = when (req.kind) { "cms" -> "cms:${req.ref}"; "jf" -> "jf:${req.ref}"; else -> "${req.kind}:${req.ref}" }
        Store.saveHistory(History(key, req.title, req.pic, req.kind, req.ref, line, ep, e.name, p, d, System.currentTimeMillis()))
        req.jfIds.getOrNull(ep)?.let { id -> scope.launch { Jellyfin.reportStop(id, p) } }
    }
    fun load(l: Int, e: Int, start: Long = 0) {
        val list = req.lines.getOrNull(l)?.eps ?: return
        if (e !in list.indices) return
        line = l; ep = e; err = ""; buffering = true
        player.setMediaItem(PlayerKit.item(list[e].url), start); player.prepare(); player.play(); osd++
    }
    LaunchedEffect(Unit) { load(line, ep, req.start); rootFr.safeFocus() }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { err = "播放失败（${error.errorCodeName}）" + if (req.lines.size > 1) "，按 下键 切换线路" else "" ; buffering = false; osdVisible = true }
            override fun onPlaybackStateChanged(s: Int) { buffering = s == Player.STATE_BUFFERING
                if (s == Player.STATE_ENDED) { if (ep + 1 < (req.lines.getOrNull(line)?.eps?.size ?: 0)) load(line, ep + 1) else { save(); Nav.back() } } }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTracksChanged(t: Tracks) { tracks = t }
        }
        player.addListener(l)
        onDispose { save(); player.removeListener(l); player.release() }
    }
    LaunchedEffect(Unit) { var n = 0; while (true) { delay(500); pos = player.currentPosition; dur = player.duration.coerceAtLeast(0); if (++n % 20 == 0 && player.isPlaying) save() } }
    LaunchedEffect(osd) { osdVisible = true; delay(4000); if (playing && !panel) osdVisible = false }
    LaunchedEffect(panel) { if (panel) { delay(80); panelFr.safeFocus() } else rootFr.safeFocus() }

    fun seek(d: Int) { seekAcc = if (seekAcc * d > 0) seekAcc + d else d
        val step = 10_000L * minOf(6, Math.abs(seekAcc)); val t = (player.currentPosition + d * step).coerceIn(0, if (dur > 0) dur else Long.MAX_VALUE)
        player.seekTo(t); pos = t; osd++ }

    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(rootFr).onKey { k ->
        if (panel) return@onKey if (k == Key.Back) { panel = false; true } else false
        when (k) {
            Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.Spacebar -> { if (player.isPlaying) player.pause() else player.play(); osd++; true }
            Key.DirectionLeft, Key.MediaRewind -> { seek(-1); true }
            Key.DirectionRight, Key.MediaFastForward -> { seek(1); true }
            Key.DirectionDown, Key.DirectionUp, Key.Menu -> { panel = true; osdVisible = true; true }
            Key.MediaNext, Key.ChannelDown -> { load(line, ep + 1); true }
            Key.MediaPrevious, Key.ChannelUp -> { load(line, ep - 1); true }
            else -> false
        }
    }.focusable()) {
        val mod = when (ratio) { 3 -> Modifier.aspectRatio(16f / 9f); 4 -> Modifier.aspectRatio(4f / 3f); else -> Modifier.fillMaxSize() }
        AndroidView({ PlayerView(it).apply { useController = false; this.player = player; keepScreenOn = true } }, mod.align(Alignment.Center),
            update = { it.resizeMode = when (ratio) { 1, 3, 4 -> AspectRatioFrameLayout.RESIZE_MODE_FILL; 2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT } })
        if (buffering && err.isBlank()) Box(Modifier.align(Alignment.Center).glass(24).padding(horizontal = 40.p, vertical = 20.p)) { T("缓冲中…", 26) }
        if (err.isNotBlank()) Box(Modifier.align(Alignment.Center).glass(24).padding(horizontal = 40.p, vertical = 20.p)) { T(err, 26) }
        if (osdVisible || panel) {
            Column(Modifier.align(Alignment.TopStart).padding(start = 96.p, top = 60.p)) {
                T(req.title, 48, FontWeight.Bold)
                eps.getOrNull(ep)?.let { T(it.name + " · " + (req.lines.getOrNull(line)?.name ?: ""), 24, alpha = .7f) }
            }
            Clock(Modifier.align(Alignment.TopEnd).padding(top = 52.p, end = 80.p))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 96.p, vertical = 50.p)) {
                if (panel) PlayerPanel(req, line, ep, panelTab, { panelTab = it }, tracks, ratio, panelFr,
                    onEp = { l, e -> load(l, e); panel = false }, onRatio = { ratio = it; Store.set("ratio", "$it") },
                    onTrack = { g, i -> player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(g.type, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build() },
                    onSubOff = { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build() })
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.p)) {
                    T(if (playing) "❚❚" else "▶", 28, FontWeight.Bold); Spacer(Modifier.width(24.p))
                    T(fmtTime(pos), 22); Spacer(Modifier.width(20.p))
                    Box(Modifier.weight(1f).height(8.p).clip(RoundedCornerShape(4.p)).background(Color(0x4DFFFFFF))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f).background(Color.White))
                    }
                    Spacer(Modifier.width(20.p)); T(if (dur > 0) fmtTime(dur) else "--:--", 22)
                }
                if (!panel) T("◀ ▶ 快退/快进（连按加速）　OK 暂停/播放　▲▼ 选集 · 音轨 · 字幕 · 画面比例", 20, alpha = .6f, modifier = Modifier.padding(top = 14.p))
            }
        }
    }
}

@Composable
fun PlayerPanel(req: PlayReq, line: Int, ep: Int, tab: Int, setTab: (Int) -> Unit, tracks: Tracks, ratio: Int, fr: FocusRequester,
                onEp: (Int, Int) -> Unit, onRatio: (Int) -> Unit, onTrack: (Tracks.Group, Int) -> Unit, onSubOff: () -> Unit) {
    val tabs = listOf("选集", "线路", "音轨", "字幕", "画面比例")
    var selLine by remember { mutableIntStateOf(line) }
    Column(Modifier.fillMaxWidth().glass(28).padding(28.p)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.p)) { tabs.forEachIndexed { i, t -> Pill(t, tab == i, size = 24, padH = 24, padV = 10, focusRequester = if (i == tab) fr else null, onFocus = { setTab(i) }, onClick = { setTab(i) }) } }
        Spacer(Modifier.height(20.p))
        val st = rememberTvLazyListState()
        TvLazyRow(state = st, horizontalArrangement = Arrangement.spacedBy(10.p), contentPadding = PaddingValues(8.p)) {
            when (tab) {
                0 -> { val es = req.lines.getOrNull(selLine)?.eps ?: emptyList(); itemsIndexed(es) { i, e -> Pill(e.name, selLine == line && i == ep, size = 22, padH = 20, padV = 8, radius = 12) { onEp(selLine, i) } } }
                1 -> itemsIndexed(req.lines) { i, l -> Pill("${l.name}（${l.eps.size}）", i == selLine, size = 22, padH = 20, padV = 8, radius = 12, onClick = { selLine = i; setTab(0) }) }
                2, 3 -> {
                    val type = if (tab == 2) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
                    val opts = tracks.groups.filter { it.type == type }.flatMap { g -> (0 until g.length).map { g to it } }
                    if (tab == 3) item { Pill("关闭", size = 22, padH = 20, padV = 8, radius = 12, onClick = onSubOff) }
                    if (opts.isEmpty()) item { T(if (tab == 2) "无可选音轨" else "无内嵌字幕", 22, alpha = .6f, modifier = Modifier.padding(10.p)) }
                    itemsIndexed(opts) { _, (g, i) -> val f = g.getTrackFormat(i)
                        val name = listOfNotNull(f.label, f.language?.let { langName(it) }, if (f.channelCount > 0) "${f.channelCount}声道" else null).joinToString(" ").ifBlank { "轨道 ${i + 1}" }
                        Pill(name, g.isTrackSelected(i), size = 22, padH = 20, padV = 8, radius = 12) { onTrack(g, i) } }
                }
                else -> itemsIndexed(RATIOS) { i, r -> Pill(r, i == ratio, size = 22, padH = 20, padV = 8, radius = 12) { onRatio(i) } }
            }
        }
        LaunchedEffect(tab) { if (tab == 0 && ep > 0 && selLine == line) st.scrollToItem(ep) }
    }
}
fun langName(l: String) = when (l.lowercase().take(2)) { "zh" -> "中文"; "en" -> "英语"; "ja" -> "日语"; "ko" -> "韩语"; "fr" -> "法语"; "de" -> "德语"; "es" -> "西语"; "un" -> null; else -> l } ?: ""
