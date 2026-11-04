package com.home.tvvideo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.launch

data class PlayReq(val title: String, val pic: String, val kind: String, val ref: String, val lines: List<Line>, val line: Int, val ep: Int,
                   val start: Long = 0, val headers: Map<String, String> = emptyMap(), val jfIds: List<String> = emptyList())
sealed class Scr {
    data class Main(val tab: Int) : Scr()
    data class Detail(val kind: String, val id: String, val api: String = "") : Scr()
    data class Play(val req: PlayReq) : Scr()
    data class Live(val key: String?) : Scr()
}
object Nav {
    val stack = mutableStateListOf<Scr>(Scr.Main(0))
    var tab by mutableIntStateOf(0)
    fun go(s: Scr) { stack.add(s) }
    fun back(): Boolean { if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }; return false }
    fun replace(s: Scr) { stack[stack.lastIndex] = s }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { Store.init(this@MainActivity); Cms.load(this@MainActivity); ready = true }
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(BG)) {
                    if (ready) when (val top = Nav.stack.last()) {
                        is Scr.Main -> MainScreen(onExit = { finish() })
                        is Scr.Detail -> key(top) { DetailScreen(top) }
                        is Scr.Play -> key(top) { PlayerScreen(top.req) }
                        is Scr.Live -> key(top) { LiveScreen(top.key) }
                    }
                }
                BackHandler(Nav.stack.size > 1) { Nav.back() }
            }
        }
    }
}

val TABS = listOf("首页", "直播", "点播", "家庭影院", "搜索", "设置")

@Composable
fun MainScreen(onExit: () -> Unit) {
    val navFr = remember { TABS.map { FocusRequester() } }
    var onNav by remember { mutableStateOf(true) }
    BackHandler(true) { if (!onNav) { runCatching { navFr[Nav.tab].requestFocus() } } else if (Nav.tab != 0) { Nav.tab = 0; runCatching { navFr[0].requestFocus() } } else onExit() }
    LaunchedEffect(Unit) { runCatching { navFr[Nav.tab].requestFocus() } }
    Box(Modifier.fillMaxSize()) {
        when (Nav.tab) {
            0 -> HomeTab(); 1 -> LiveTab(); 2 -> VodTab(); 3 -> LibraryTab(); 4 -> SearchTab(); 5 -> SettingsTab()
        }
        Row(Modifier.align(Alignment.TopCenter).padding(top = 40.p).zIndex(20f).glass(40).padding(8.p), horizontalArrangement = Arrangement.spacedBy(6.p)) {
            TABS.forEachIndexed { i, t ->
                Pill(t, selected = Nav.tab == i, focusRequester = navFr[i], modifier = Modifier.onFocusChanged { if (it.hasFocus) onNav = true else if (Nav.tab == i) onNav = false },
                    onFocus = { Nav.tab = i }, onClick = { Nav.tab = i; if (i == 1) Nav.go(Scr.Live(null)) })
            }
        }
        Clock(Modifier.align(Alignment.TopEnd).padding(top = 52.p, end = 80.p).zIndex(20f))
    }
}

// ---------------- 首页 ----------------
object HomeData { var hero: VodItem? by mutableStateOf(null); var latest by mutableStateOf(listOf<VodItem>()); var nas by mutableStateOf(listOf<JfItem>()) }

@Composable
fun HomeTab() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val hist by Store.history.collectAsState()
    var live by remember { mutableStateOf(listOf<Channel>()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        launch { live = LiveRepo.load(ctx).channels.take(8) }
        launch { val src = Cms.cur; if (src != null && HomeData.latest.isEmpty()) runCatching { val l = Cms.list(null, 1, src).list; HomeData.latest = l; HomeData.hero = l.firstOrNull { it.pic.isNotBlank() } } }
        launch { if (Jellyfin.configured) runCatching { HomeData.nas = Jellyfin.latest() } }
    }
    val hero = HomeData.hero
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(660.p).background(Grads[4])) {
            if (hero != null) Poster(hero.pic, hero.id)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xEE0A0A0F), Color(0x660A0A0F), Color.Transparent))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to BG)))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val noVod = !Cms.configured
            Column(Modifier.padding(start = 96.p, top = 130.p).width(900.p)) {
                T("本周主推 · ${hero?.typeName ?: if (noVod) "家庭影视" else "精选"}", 22, FontWeight.SemiBold, .8f)
                T(hero?.name ?: if (noVod) "从这里开始" else "加载中…", 84, FontWeight.Bold, modifier = Modifier.padding(vertical = 8.p))
                T(hero?.content?.ifBlank { null } ?: hero?.remarks ?: if (noVod) "直播可直接观看。点播不附带任何接口，请到设置里自行添加苹果 CMS 地址；家庭影院可连接 Jellyfin、WebDAV 或 SMB。" else "", 28, alpha = .85f, lines = 3)
                Row(Modifier.padding(top = 30.p)) { listOfNotNull(hero?.year, hero?.area, hero?.remarks).filter { it.isNotBlank() }.take(3).forEach { Tag(it) } }
                Row(Modifier.padding(top = 26.p), horizontalArrangement = Arrangement.spacedBy(16.p)) {
                    if (hero != null) {
                        Btn("立即播放", play = true) { Cms.cur?.let { src -> Nav.go(Scr.Detail("cms", hero.id, src.api)) } }
                        val favs by Store.favs.collectAsState()
                        Btn(if ("vod:${hero.id}" in favs) "✓ 已收藏" else "＋ 收藏") { Store.toggleFav("vod:${hero.id}") }
                    } else if (noVod) Btn("添加点播源", play = true) { Nav.tab = 5 }
                }
            }
            Column(Modifier.padding(start = 96.p, top = 50.p, bottom = 60.p)) {
                if (hist.isNotEmpty()) {
                    T("继续观看", 30, FontWeight.Bold, modifier = Modifier.padding(bottom = 18.p))
                    TvLazyRow(horizontalArrangement = Arrangement.spacedBy(36.p), contentPadding = PaddingValues(vertical = 20.p, horizontal = 4.p)) {
                        items(hist.take(20)) { h ->
                            FocusCard(w = 340, h = 190, brush = grad(h.key), onClick = { resume(h) }) {
                                Poster(h.pic, h.key); CardTitle(h.title + if (h.epName.isNotBlank() && h.epName != "正片") " ${h.epName}" else "")
                                if (h.dur > 0) ProgressBar(h.pos.toFloat() / h.dur)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(60.p), modifier = Modifier.padding(top = 10.p)) {
                    Column {
                        T("热门直播", 30, FontWeight.Bold, modifier = Modifier.padding(bottom = 18.p))
                        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(36.p), contentPadding = PaddingValues(vertical = 20.p, horizontal = 4.p), modifier = Modifier.widthIn(max = 800.p)) {
                            items(live) { c -> FocusCard(w = 230, h = 118, brush = grad(c.name), onClick = { Nav.go(Scr.Live(c.key)) }) { Badge("● 直播"); CardTitle(c.name) } }
                        }
                    }
                    Column {
                        T("最近添加到 NAS", 30, FontWeight.Bold, modifier = Modifier.padding(bottom = 18.p))
                        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(36.p), contentPadding = PaddingValues(vertical = 20.p, horizontal = 4.p)) {
                            if (HomeData.nas.isEmpty()) item { FocusCard(w = 300, h = 118, brush = Grads[7], onClick = { Nav.tab = 5 }) { CardTitle(if (Jellyfin.configured) "暂无新内容" else "设置 NAS", if (Jellyfin.configured) null else "添加 Jellyfin / WebDAV / SMB") } }
                            items(HomeData.nas) { j -> FocusCard(w = 230, h = 118, brush = grad(j.id), onClick = { openJf(j) }) { Poster(Jellyfin.img(j.id, "Backdrop", 500), j.id); CardTitle(j.seriesName.ifBlank { j.name }) } }
                        }
                    }
                }
            }
        }
    }
}

fun openJf(j: JfItem) {
    if (j.type == "Episode" || j.type == "Movie" && false) Nav.go(Scr.Play(PlayReq(j.seriesName.ifBlank { j.name }, Jellyfin.img(j.id), "jf", j.id, listOf(Line("Jellyfin", listOf(Ep(j.name, Jellyfin.stream(j.id))))), 0, 0, j.pos, jfIds = listOf(j.id))))
    else Nav.go(Scr.Detail("jf", j.id))
}

fun resume(h: History) {
    when (h.kind) {
        "cms" -> Nav.go(Scr.Detail("cms", h.ref.substringAfter("|"), h.ref.substringBefore("|")))
        "jf" -> Nav.go(Scr.Detail("jf", h.ref))
        else -> Nav.go(Scr.Play(PlayReq(h.title, "", h.kind, h.ref, listOf(Line(h.kind, listOf(Ep(h.title, h.ref)))), 0, 0, h.pos, if (h.kind == "dav") WebDav.authHeader() else emptyMap())))
    }
}
