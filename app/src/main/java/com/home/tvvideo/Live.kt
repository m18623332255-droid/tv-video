package com.home.tvvideo

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val LIVE_GROUPS = listOf("央视", "卫视", "地方", "其他", "收藏")
val LOGO_COLORS = listOf(0xFFD63A3A, 0xFF2A7DE1, 0xFF8E54E9, 0xFF2E9E6A, 0xFFF39C12, 0xFFC0392B).map { Color(it) }

fun channelsOf(all: List<Channel>, g: String, favs: Set<String>) = if (g == "收藏") all.filter { "live:${it.key}" in favs } else all.filter { it.group == g }

/** 导航栏“直播”页：频道墙，点击进入全屏 */
@Composable
fun LiveTab() {
    val ctx = LocalContext.current
    var all by remember { mutableStateOf(LiveRepo.playlist.channels) }
    var g by remember { mutableStateOf("央视") }
    val favs by Store.favs.collectAsState()
    LaunchedEffect(Unit) { all = LiveRepo.load(ctx).channels }
    Column(Modifier.fillMaxSize().padding(start = 96.p, end = 96.p, top = 150.p)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("电视直播", 64, FontWeight.Bold); Spacer(Modifier.width(40.p))
            LIVE_GROUPS.forEach { Pill(it, g == it, size = 24, padH = 26, padV = 10, onFocus = { g = it }, onClick = { g = it }) }
            Spacer(Modifier.width(30.p)); T("共 ${all.size} 个频道 · 同名频道已合并为多线路", 20, alpha = .6f)
        }
        val list = channelsOf(all, g, favs)
        if (list.isEmpty()) Empty(if (g == "收藏") "还没有收藏频道，在全屏直播中按 菜单键 收藏" else "加载中…")
        TvLazyVerticalGrid(TvGridCells.Fixed(6), Modifier.padding(top = 30.p), horizontalArrangement = Arrangement.spacedBy(30.p), verticalArrangement = Arrangement.spacedBy(30.p),
            contentPadding = PaddingValues(20.p)) {
            items(list) { c -> FocusCard(w = 236, h = 130, brush = grad(c.name), onClick = { Nav.go(Scr.Live(c.key)) }) {
                Badge("${c.urls.size} 线路"); CardTitle(c.name, c.group) } }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun LiveScreen(startKey: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf(LiveRepo.playlist.channels) }
    val favs by Store.favs.collectAsState()
    var cur by remember { mutableStateOf<Channel?>(null) }
    var line by remember { mutableIntStateOf(0) }
    var showList by remember { mutableStateOf(false) }
    var info by remember { mutableIntStateOf(0) } // 每次换台计数触发信息条
    var status by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf(listOf<Prog>()) }
    var group by remember { mutableStateOf("央视") }
    val player = remember { PlayerKit.build(ctx, live = true) }
    val rootFr = remember { FocusRequester() }
    val listFr = remember { FocusRequester() }

    fun play(c: Channel, l: Int) {
        cur = c; line = l.mod(c.urls.size); status = "加载中…"; info++
        Store.set("lastLive", c.key)
        player.setMediaItem(PlayerKit.item(c.urls[line])); player.prepare(); player.play()
    }
    fun groupList() = channelsOf(all, cur?.group ?: group, favs).ifEmpty { all }
    fun zap(d: Int) { val l = groupList(); val c = cur ?: return; val i = l.indexOfFirst { it.key == c.key }; play(l[(i + d).mod(l.size)], 0) }
    fun nextLine(auto: Boolean) { val c = cur ?: return; if (c.urls.size > 1) { play(c, line + 1); if (auto) status = "线路异常，已自动切换到线路 ${line + 1}" } else status = "播放失败，该频道仅一条线路" }

    LaunchedEffect(Unit) {
        all = LiveRepo.load(ctx).channels
        val k = startKey ?: Store.get("lastLive")
        val c = all.firstOrNull { it.key == k } ?: all.firstOrNull()
        if (c != null) { group = c.group; play(c, 0) }
        rootFr.requestFocus()
    }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { scope.launch { delay(300); nextLine(true) } }
            override fun onPlaybackStateChanged(s: Int) { if (s == Player.STATE_READY) status = "" }
        }
        player.addListener(l); onDispose { player.removeListener(l); player.release() }
    }
    // 卡顿检测：缓冲超过 12 秒自动切线路
    LaunchedEffect(cur, line) { var stall = 0; while (true) { delay(1000); if (player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_IDLE) stall++ else stall = 0; if (stall >= 12) { stall = 0; nextLine(true) } } }
    LaunchedEffect(cur) { epg = emptyList(); cur?.let { epg = runCatching { Epg.get(it.tvgName.ifBlank { it.name }) }.getOrDefault(emptyList()) } }
    var infoVisible by remember { mutableStateOf(true) }
    LaunchedEffect(info) { infoVisible = true; delay(5000); infoVisible = false }
    LaunchedEffect(showList) { if (showList) { delay(80); runCatching { listFr.requestFocus() } } else runCatching { rootFr.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(rootFr).onKey { k ->
        if (showList) return@onKey when (k) { Key.Back -> { showList = false; true }; else -> false }
        when (k) {
            Key.DirectionUp, Key.ChannelUp -> { zap(-1); true }
            Key.DirectionDown, Key.ChannelDown -> { zap(1); true }
            Key.DirectionCenter, Key.Enter, Key.DirectionLeft -> { group = cur?.group ?: group; showList = true; true }
            Key.DirectionRight -> { cur?.let { play(it, line + 1) }; true }
            Key.Menu, Key.Bookmark -> { cur?.let { Store.toggleFav("live:${it.key}") }; info++; true }
            else -> false
        }
    }.focusable()) {
        AndroidView({ PlayerView(it).apply { useController = false; this.player = player; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; keepScreenOn = true } }, Modifier.fillMaxSize())
        if (status.isNotBlank()) Box(Modifier.align(Alignment.Center).glass(24).padding(horizontal = 40.p, vertical = 20.p)) { T(status, 26) }
        Clock(Modifier.align(Alignment.TopEnd).padding(top = 52.p, end = 80.p))
        val c = cur
        if (showList) {
            val list = channelsOf(all, group, favs)
            Column(Modifier.padding(start = 48.p, top = 48.p, bottom = 140.p).width(520.p).fillMaxHeight().glass(32).padding(30.p)) {
                Row(Modifier.padding(bottom = 22.p), horizontalArrangement = Arrangement.spacedBy(4.p)) {
                    LIVE_GROUPS.forEach { g -> Pill(g, group == g, size = 22, padH = 18, padV = 8, radius = 20, onFocus = { group = g }, onClick = { group = g }) }
                }
                val st = rememberTvLazyListState()
                val curIdx = list.indexOfFirst { it.key == c?.key }.coerceAtLeast(0)
                LaunchedEffect(group) { st.scrollToItem(curIdx) }
                if (list.isEmpty()) T(if (group == "收藏") "暂无收藏，播放时按菜单键收藏" else "无频道", 22, alpha = .6f)
                TvLazyColumn(state = st, verticalArrangement = Arrangement.spacedBy(4.p), contentPadding = PaddingValues(vertical = 10.p, horizontal = 12.p)) {
                    itemsIndexed(list) { i, ch ->
                        ChannelRow(ch, i, ch.key == c?.key, "live:${ch.key}" in favs, if (i == curIdx) listFr else null,
                            onFav = { Store.toggleFav("live:${ch.key}") }) { play(ch, 0); showList = false }
                    }
                }
            }
            if (epg.isNotEmpty() && c != null) EpgPanel(c, epg, Modifier.align(Alignment.TopEnd).padding(top = 110.p, end = 48.p))
        }
        if ((infoVisible || showList) && c != null) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.p).glass(30).padding(horizontal = 40.p, vertical = 18.p), horizontalArrangement = Arrangement.spacedBy(30.p), verticalAlignment = Alignment.CenterVertically) {
                T(c.name + if ("live:${c.key}" in favs) " ♥" else "", 26, FontWeight.Bold)
                T("当前线路：线路 ${line + 1}", 22)
                T("◀ ▶ 切换线路（共 ${c.urls.size} 条）", 22, alpha = .7f)
                T("OK 键 频道列表 · 菜单键 收藏", 22, alpha = .7f)
                now(epg)?.let { T("正在播：${it.title}", 22, alpha = .8f) }
            }
        }
    }
}

fun now(epg: List<Prog>): Prog? { val t = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date()); return epg.lastOrNull { it.start <= t } }

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(c: Channel, i: Int, playing: Boolean, fav: Boolean, fr: FocusRequester?, onFav: () -> Unit, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val f by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.p)
    Row(Modifier.fillMaxWidth().then(if (fr != null) Modifier.focusRequester(fr) else Modifier)
        .graphicsLayer { val sc = if (f) 1.05f else 1f; scaleX = sc; scaleY = sc }.clip(shape).background(if (f) Color(0xE6FFFFFF) else if (playing) Color(0x26FFFFFF) else Color.Transparent)
        .onKey { if (it == Key.Menu) { onFav(); true } else false }
        .combinedClickable(interactionSource = src, indication = null, onLongClick = onFav, onClick = onClick).padding(horizontal = 18.p, vertical = 14.p), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(58.p).clip(CircleShape).background(LOGO_COLORS[i % LOGO_COLORS.size]), contentAlignment = Alignment.Center) { T(c.name.take(2), 20, FontWeight.Bold) }
        Spacer(Modifier.width(18.p))
        Column(Modifier.weight(1f)) {
            T(c.name, 24, FontWeight.SemiBold, color = if (f) Color(0xFF111111) else Color.White)
            T((if (playing) "▶ 正在播放 · " else "") + "${c.urls.size} 条线路" + if (fav) " · ♥ 已收藏" else "", 17, alpha = .6f, color = if (f) Color(0xFF111111) else Color.White)
        }
    }
}

@Composable
fun EpgPanel(c: Channel, epg: List<Prog>, modifier: Modifier) {
    val t = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date())
    val ni = epg.indexOfLast { it.start <= t }.coerceAtLeast(0)
    Column(modifier.width(480.p).glass(32).padding(30.p)) {
        T("节目单 · ${c.name}", 20, alpha = .6f)
        T("今日 " + SimpleDateFormat("M月d日", Locale.CHINA).format(Date()), 34, FontWeight.Bold, modifier = Modifier.padding(top = 8.p, bottom = 20.p))
        epg.drop((ni - 1).coerceAtLeast(0)).take(6).forEach { p ->
            val live = p === epg[ni]
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.p)).background(if (live) Color(0x26FFFFFF) else Color.Transparent).padding(horizontal = 16.p, vertical = 14.p)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("${p.start}　${p.title}", 24, if (live) FontWeight.Bold else FontWeight.Normal, if (live) 1f else .75f, modifier = Modifier.weight(1f, false))
                    if (live) Box(Modifier.padding(start = 8.p).clip(RoundedCornerShape(6.p)).background(Color(0xFFFF375F)).padding(horizontal = 8.p, vertical = 2.p)) { T("直播中", 16) }
                }
            }
        }
    }
}
