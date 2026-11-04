@file:kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.home.tvvideo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed as rowItemsIndexed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

object VodState {
    var top by mutableIntStateOf(0)
    var classes by mutableStateOf(listOf<CmsClass>())
    var sub by mutableStateOf<CmsClass?>(null)
    var area by mutableStateOf("全部")
    var year by mutableStateOf("全部")
    var items = mutableStateListOf<VodItem>()
    var page = 0; var pageCount = 1
    var loading by mutableStateOf(false)
    var error by mutableStateOf("")
    var version by mutableIntStateOf(0)
    var srcApi = ""
}
val AREAS = listOf("全部", "内地", "港台", "韩国", "日本", "欧美", "其他")
val YEARS = listOf("全部", "2026", "2025", "2024", "2023", "更早")

fun yearOk(y: String, sel: String) = when (sel) { "全部" -> true; "更早" -> (y.toIntOrNull() ?: 0) < 2023; else -> y == sel }
fun filtered(l: List<VodItem>) = l.filter { (VodState.area == "全部" || Parsers.regionOf(it.area) == VodState.area) && yearOk(it.year, VodState.year) }

suspend fun vodLoadMore(reset: Boolean = false) {
    val s = VodState
    if (s.loading) return
    if (reset || s.srcApi != Cms.cur.api) { s.items.clear(); s.page = 0; s.pageCount = 1; s.srcApi = Cms.cur.api; s.error = "" }
    if (s.page >= s.pageCount) return
    s.loading = true
    try {
        if (s.classes.isEmpty()) s.classes = Cms.classes()
        val topName = Parsers.TOP[s.top]
        val cls = Parsers.classesFor(topName, s.classes)
        // 父分类在多数采集站下不含子类数据：“全部”时并行合并前 4 个子分类
        val kids = cls.filter { it.pid != "0" }.ifEmpty { cls }
        val ids: List<String?> = when { topName == "推荐" -> listOf(null); s.sub != null -> listOf(s.sub!!.id); kids.isEmpty() -> listOf("-1"); else -> kids.take(4).map { it.id } }
        var tries = 0
        do {
            val pg = s.page + 1
            val rs = kotlinx.coroutines.coroutineScope { ids.map { t -> async { runCatching { Cms.list(t, pg) }.getOrNull() } }.awaitAll() }.filterNotNull()
            if (rs.isEmpty()) error("接口无响应")
            s.page = pg; s.pageCount = rs.maxOf { it.pageCount }
            val merged = (0 until rs.maxOf { it.list.size }).flatMap { i -> rs.mapNotNull { it.list.getOrNull(i) } }
            val allowed = if (topName == "推荐") merged.filter { Parsers.topOf(it.typeName) != null } else merged
            s.items.addAll(allowed.filter { n -> s.items.none { it.id == n.id } })
        } while (++tries < 4 && filtered(s.items).size < 18 && s.page < s.pageCount)
    } catch (e: Exception) { s.error = "加载失败：${e.message ?: e.javaClass.simpleName}，请在设置中切换点播源" }
    s.loading = false
}

@Composable
fun VodTab() {
    val scope = rememberCoroutineScope()
    val s = VodState
    LaunchedEffect(s.version, Cms.cur.api) { if (s.items.isEmpty() || s.srcApi != Cms.cur.api) vodLoadMore(true) }
    fun reload() { s.version++; scope.launch { vodLoadMore(true) } }
    Column(Modifier.fillMaxSize().padding(start = 96.p, end = 96.p, top = 130.p)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Parsers.TOP.forEachIndexed { i, t -> Pill(t, s.top == i, size = 28, padH = 24, padV = 10, radius = 24, onClick = { if (s.top != i) { s.top = i; s.sub = null; reload() } }) }
            Spacer(Modifier.weight(1f)); T("源：${Cms.cur.name}（设置中切换）", 20, alpha = .55f)
        }
        val subs = Parsers.classesFor(Parsers.TOP[s.top], s.classes).filter { it.pid != "0" || Parsers.classesFor(Parsers.TOP[s.top], s.classes).size == 1 }
        Column(Modifier.padding(top = 20.p).fillMaxWidth().glass(22).padding(horizontal = 22.p, vertical = 10.p)) {
            FilterRow("地区", AREAS, s.area) { s.area = it; if (filtered(s.items).size < 12) scope.launch { vodLoadMore() } }
            if (subs.isNotEmpty()) FilterRow("类型", listOf("全部") + subs.map { it.name }, s.sub?.name ?: "全部") { n -> s.sub = subs.firstOrNull { it.name == n }; reload() }
            FilterRow("年份", YEARS, s.year) { s.year = it; if (filtered(s.items).size < 12) scope.launch { vodLoadMore() } }
        }
        val list = filtered(s.items)
        if (s.error.isNotBlank()) Empty(s.error) else if (list.isEmpty() && !s.loading) Empty("暂无内容")
        PosterGrid(list, Modifier.padding(top = 10.p), onNearEnd = { scope.launch { vodLoadMore() } }) { Nav.go(Scr.Detail("cms", it.id, Cms.cur.api)) }
    }
}

@Composable
fun FilterRow(label: String, opts: List<String>, sel: String, onSel: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.p)) {
        T(label, 21, FontWeight.SemiBold, .55f, modifier = Modifier.width(64.p))
        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(4.p), contentPadding = PaddingValues(4.p)) {
            rowItemsIndexed(opts, key = { _, o -> o }) { _, o -> Pill(o, o == sel, size = 21, padH = 16, padV = 6, radius = 16) { onSel(o) } }
        }
    }
}

@Composable
fun PosterGrid(list: List<VodItem>, modifier: Modifier = Modifier, onNearEnd: () -> Unit = {}, onClick: (VodItem) -> Unit) {
    TvLazyVerticalGrid(TvGridCells.Fixed(6), modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(36.p), verticalArrangement = Arrangement.spacedBy(36.p),
        contentPadding = PaddingValues(vertical = 30.p, horizontal = 16.p)) {
        itemsIndexed(list, key = { _, v -> v.id }) { i, v ->
            FocusCard(w = 236, h = 300, brush = grad(v.id), onFocus = { if (i >= list.size - 12) onNearEnd() }, onClick = { onClick(v) }) {
                Poster(v.pic, v.id, wPx = 236, hPx = 300); CardTitle(v.name); Badge(v.remarks.take(10)); if (v.score.isNotBlank()) Badge("★ ${v.score}", true, Color(0xFFFFD60A))
            }
        }
    }
}

// ---------------- 搜索 ----------------
object SearchState { var q by mutableStateOf(""); var res = mutableStateListOf<VodItem>(); var jf = mutableStateListOf<JfItem>(); var msg by mutableStateOf("输入片名、演员，按确认搜索") }

@Composable
fun SearchTab() {
    val scope = rememberCoroutineScope()
    val s = SearchState
    fun go() { if (s.q.isBlank()) return; s.msg = "搜索中…"; s.res.clear(); s.jf.clear()
        scope.launch { runCatching { s.res.addAll(Cms.search(s.q.trim()).list) }.onFailure { s.msg = "搜索失败：${it.message}" }
            if (Jellyfin.configured) runCatching { s.jf.addAll(Jellyfin.search(s.q.trim())) }
            if (s.res.isEmpty() && s.jf.isEmpty()) s.msg = "没有找到“${s.q}”" else s.msg = "" } }
    Column(Modifier.fillMaxSize().padding(start = 96.p, end = 96.p, top = 150.p)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvTextField(s.q, { s.q = it }, "搜索影片 / 剧集 / 演员", Modifier.width(900.p), onDone = { go() })
            Spacer(Modifier.width(20.p)); Btn("搜索") { go() }
            Spacer(Modifier.width(20.p)); T("当前源：${Cms.cur.name}", 20, alpha = .55f)
        }
        if (s.msg.isNotBlank()) Empty(s.msg)
        if (s.jf.isNotEmpty()) { T("家庭影院", 28, FontWeight.Bold, modifier = Modifier.padding(top = 20.p))
            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(30.p), contentPadding = PaddingValues(20.p)) { rowItemsIndexed(s.jf, key = { _, j -> j.id }) { _, j ->
                FocusCard(w = 236, h = 300, brush = grad(j.id), onClick = { openJf(j) }) { Poster(Jellyfin.img(j.id), j.id, wPx = 236, hPx = 300); CardTitle(j.name, j.year) } } } }
        PosterGrid(s.res, Modifier.padding(top = 10.p)) { Nav.go(Scr.Detail("cms", it.id, Cms.cur.api)) }
    }
}

@Composable
fun TvTextField(v: String, onV: (String) -> Unit, hint: String, modifier: Modifier = Modifier, password: Boolean = false, onDone: () -> Unit = {}) {
    var f by remember { mutableStateOf(false) }
    BasicTextField(v, onV, modifier.onFocusChanged { f = it.isFocused }, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 26.ps), cursorBrush = SolidColor(Color.White),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { inner -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.p)).background(if (f) Color(0x40FFFFFF) else Color(0x1FFFFFFF))
            .border(if (f) 2.dp_ else 1.dp_, if (f) Color.White else Color(0x33FFFFFF), RoundedCornerShape(16.p)).padding(horizontal = 24.p, vertical = 16.p)) {
            if (v.isEmpty()) T(hint, 26, alpha = .4f); inner() } })
}
val Int.dp_ get() = androidx.compose.ui.unit.Dp(this.toFloat())

// ---------------- 详情 ----------------
@Composable
fun DetailScreen(d: Scr.Detail) {
    var item by remember { mutableStateOf<VodItem?>(null) }
    var err by remember { mutableStateOf("") }
    var streams by remember { mutableStateOf(listOf<String>()) }
    var jfIds by remember { mutableStateOf(listOf<String>()) }
    var jfPos by remember { mutableStateOf(listOf<Long>()) }
    val favs by Store.favs.collectAsState()
    val hist by Store.history.collectAsState()
    val ref = if (d.kind == "cms") "${d.api}|${d.id}" else d.id
    val h = hist.firstOrNull { it.key == "${d.kind}:$ref" }
    var line by remember { mutableIntStateOf(h?.line ?: 0) }
    val playFr = remember { FocusRequester() }
    LaunchedEffect(d) {
        try {
            if (d.kind == "cms") { val src = Cms.sources.firstOrNull { it.api == d.api } ?: Cms.cur; item = Cms.detail(d.id, src) ?: error("未找到该影片") }
            else {
                val j = Jellyfin.info(d.id); streams = j.streams
                val eps = if (j.type == "Series") Jellyfin.episodes(j.id).filter { it.type == "Episode" } else listOf(j)
                jfIds = eps.map { it.id }; jfPos = eps.map { it.pos }
                item = VodItem(j.id, j.name, Jellyfin.img(j.id, "Backdrop", 1280), "", if (j.type == "Series") "剧集" else "电影", j.year, "", j.rating,
                    j.people.joinToString(",") { it.first + "|" + it.second }, "", j.overview,
                    listOf(Line("NAS", eps.map { e -> Ep(if (j.type == "Series") "S${e.season}E${e.idx} ${e.name}" else "正片", Jellyfin.stream(e.id)) })))
                if (j.pos > 0 && h == null) jfPos = listOf(j.pos)
            }
            delay(100); playFr.safeFocus()
        } catch (e: Exception) { err = "加载失败：${e.message}" }
    }
    val v = item
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(grad(d.id))) { if (v != null) Poster(v.pic, v.id, wPx = 960, hPx = 540) }
        Box(Modifier.fillMaxSize().background(Color(0xB308080E)))
        Clock(Modifier.align(Alignment.TopEnd).padding(top = 52.p, end = 80.p))
        if (v == null) { Empty(err.ifBlank { "加载中…" }, Modifier.align(Alignment.Center)); return@Box }
        fun play(l: Int, e: Int, start: Long) = Nav.go(Scr.Play(PlayReq(v.name, if (d.kind == "jf") Jellyfin.img(v.id) else v.pic, d.kind, ref, v.lines, l, e, start, jfIds = jfIds)))
        Column(Modifier.padding(start = 96.p, top = 120.p).width(1000.p)) {
            T((if (d.kind == "jf") "${v.typeName} · 来自 NAS" else "${v.typeName} · 点播"), 22, FontWeight.SemiBold, .75f)
            T(v.name, if (v.name.length > 8) 72 else 100, FontWeight.Bold, modifier = Modifier.padding(top = 10.p, bottom = 20.p))
            T(listOf(v.year, v.area, v.lang, v.remarks).filter { it.isNotBlank() }.joinToString(" · ") + if (v.score.isNotBlank()) "　★ ${v.score}" else "", 24, alpha = .9f)
            Row(Modifier.padding(top = 18.p)) { (if (d.kind == "jf") streams.take(4) else listOfNotNull(v.lines.firstOrNull()?.let { "${it.eps.size} 集" }, "${v.lines.size} 条线路")).forEach { Tag(it) } }
            T(v.content.ifBlank { "暂无简介" }, 26, alpha = .85f, lines = 3, modifier = Modifier.padding(top = 26.p, bottom = 34.p))
            Row(horizontalArrangement = Arrangement.spacedBy(16.p)) {
                if (h != null && (h.pos > 0 || h.ep > 0)) {
                    Btn("从头播放", play = true) { play(line, 0, 0) }
                    Btn("继续播放 ${if (v.lines.getOrNull(h.line)?.eps?.size ?: 0 > 1) h.epName + " " else ""}${fmtTime(h.pos)}", play = true, focusRequester = playFr) { play(h.line, h.ep, h.pos) }
                } else if (jfPos.firstOrNull { it > 0 } != null) {
                    val i = jfPos.indexOfFirst { it > 0 }
                    Btn("从头播放", play = true) { play(0, 0, 0) }
                    Btn("继续播放 ${fmtTime(jfPos[i])}", play = true, focusRequester = playFr) { play(0, i, jfPos[i]) }
                } else Btn("立即播放", play = true, focusRequester = playFr) { if (v.lines.isNotEmpty()) play(line, 0, 0) }
                val fk = "${d.kind}:${d.id}"
                Btn(if (fk in favs) "♥ 已收藏" else "♡ 收藏") { Store.toggleFav(fk) }
            }
        }
        val people = v.actor.split(",", "，", "/").map { it.trim() }.filter { it.isNotBlank() }.take(6)
        if (people.isNotEmpty()) Column(Modifier.align(Alignment.BottomStart).padding(start = 96.p, bottom = 70.p)) {
            T("演员", 28, FontWeight.Bold, modifier = Modifier.padding(bottom = 18.p))
            Row(horizontalArrangement = Arrangement.spacedBy(34.p)) { people.forEach { p -> val (n, r) = p.split("|").let { it[0] to it.getOrElse(1) { "" } }
                Column(Modifier.width(120.p), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(110.p).clip(CircleShape).background(grad(n)), contentAlignment = Alignment.Center) { T(n.take(1), 36, FontWeight.Bold) }
                    T(n, 20, modifier = Modifier.padding(top = 10.p)); if (r.isNotBlank()) T(r, 16, alpha = .6f) } } }
        }
        if (v.lines.isNotEmpty()) Column(Modifier.align(Alignment.BottomEnd).padding(end = 96.p, bottom = 70.p).width(760.p).glass(28).padding(28.p)) {
            if (v.lines.size > 1) { T("播放线路", 22, alpha = .6f, modifier = Modifier.padding(bottom = 10.p))
                TvLazyRow(horizontalArrangement = Arrangement.spacedBy(10.p), contentPadding = PaddingValues(6.p)) { rowItemsIndexed(v.lines) { i, l -> Pill(l.name, i == line, size = 22, padH = 18, padV = 8, radius = 12, onClick = { line = i }) } }
                Spacer(Modifier.height(16.p)) }
            val eps = v.lines.getOrNull(line)?.eps ?: emptyList()
            T("选集（共 ${eps.size}）", 22, alpha = .6f, modifier = Modifier.padding(bottom = 10.p))
            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(10.p), contentPadding = PaddingValues(6.p)) {
                rowItemsIndexed(eps) { i, e -> Pill(e.name, h?.line == line && h.ep == i, size = 22, padH = 18, padV = 8, radius = 12) { play(line, i, if (h?.line == line && h.ep == i) h.pos else jfPos.getOrElse(i) { 0 }) } }
            }
        }
    }
}
