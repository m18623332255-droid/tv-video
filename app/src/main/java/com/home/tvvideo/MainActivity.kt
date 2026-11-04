@file:kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val crash = CrashGuard.pending(this)
        if (crash != null) { setContent { SafeModeScreen(crash, onContinue = { CrashGuard.clear(this); recreate() }, onExit = { CrashGuard.clear(this); finish() }) }; return }
        setContent {
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { Store.init(this@MainActivity); withContext(Dispatchers.IO) { Cms.load(this@MainActivity) }; ready = true
                // 来自桌面「继续观看」：直接续播
                intent?.getStringExtra("resume_key")?.let { k -> Store.findHistory(k)?.let { resume(it) } } }
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
    BackHandler(true) { if (!onNav) { navFr[Nav.tab].safeFocus() } else if (Nav.tab != 0) { Nav.tab = 0; navFr[0].safeFocus() } else onExit() }
    LaunchedEffect(Unit) { navFr[Nav.tab].safeFocus() }
    val contentFr = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    Box(Modifier.fillMaxSize()) {
        // 内容区作为一个焦点组：导航栏按 下键 时明确把焦点送进内容区
        Box(Modifier.fillMaxSize().focusRequester(contentFr).focusGroup()) {
            when (Nav.tab) {
                0 -> HomeTab(); 1 -> LiveTab(); 2 -> VodTab(); 3 -> LibraryTab(); 4 -> SearchTab(); 5 -> SettingsTab()
            }
        }
        Row(Modifier.align(Alignment.TopCenter).padding(top = 40.p).zIndex(20f).glass(40).padding(8.p)
            .onKey { k -> if (k == Key.DirectionDown) { if (!contentFr.safeFocus() || onNav) fm.moveFocus(FocusDirection.Down); true } else false },
            horizontalArrangement = Arrangement.spacedBy(6.p)) {
            TABS.forEachIndexed { i, t ->
                Pill(t, selected = Nav.tab == i, focusRequester = navFr[i], modifier = Modifier.focusProperties { up = FocusRequester.Cancel }.onFocusChanged { if (it.hasFocus) onNav = true else if (Nav.tab == i) onNav = false },
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
        launch { if (HomeData.latest.isEmpty()) runCatching { val l = Cms.list(null, 1).list; HomeData.latest = l; HomeData.hero = l.firstOrNull { it.pic.isNotBlank() } } }
        launch { if (Jellyfin.configured) runCatching { HomeData.nas = Jellyfin.latest() } }
    }
    val hero = HomeData.hero
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(660.p).background(Grads[4])) {
            if (hero != null) Poster(hero.pic, hero.id, wPx = 960, hPx = 540)
            Box(Modifier.fillMaxSize().background(Color(0x990A0A0F)))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(start = 96.p, top = 130.p).width(900.p)) {
                T("本周主推 · ${hero?.typeName ?: "精选"}", 22, FontWeight.SemiBold, .8f)
                T(hero?.name ?: "加载中…", 84, FontWeight.Bold, modifier = Modifier.padding(vertical = 8.p))
                T(hero?.content?.ifBlank { null } ?: hero?.remarks ?: "", 28, alpha = .85f, lines = 2)
                Row(Modifier.padding(top = 30.p)) { listOfNotNull(hero?.year, hero?.area, hero?.remarks).filter { it.isNotBlank() }.take(3).forEach { Tag(it) } }
                Row(Modifier.padding(top = 26.p), horizontalArrangement = Arrangement.spacedBy(16.p)) {
                    Btn("立即播放", play = true) { hero?.let { Nav.go(Scr.Detail("cms", it.id, Cms.cur.api)) } }
                    val favs by Store.favs.collectAsState()
                    Btn(if (hero != null && "vod:${hero.id}" in favs) "✓ 已收藏" else "＋ 收藏") { hero?.let { Store.toggleFav("vod:${it.id}") } }
                }
            }
            Column(Modifier.padding(start = 96.p, top = 50.p, bottom = 60.p)) {
                if (hist.isNotEmpty()) {
                    var askClear by remember { mutableStateOf(false) }
                    var askDel by remember { mutableStateOf<History?>(null) }
                    Row(Modifier.padding(bottom = 18.p), verticalAlignment = Alignment.CenterVertically) {
                        T("继续观看", 30, FontWeight.Bold); Spacer(Modifier.width(30.p))
                        Btn("🗑 清除历史记录") { askClear = true }; Spacer(Modifier.width(20.p))
                        T("选中某条按 菜单键 或长按 OK 可单独删除", 20, alpha = .5f)
                    }
                    if (askClear) ConfirmDialog("清除历史记录", "将删除全部 ${hist.size} 条播放记录，无法恢复。", "清除", onOk = { Store.clearHistory() }, onDismiss = { askClear = false })
                    askDel?.let { d -> ConfirmDialog("删除这条记录？", d.title, "删除", onOk = { Store.removeHistory(d.key) }, onDismiss = { askDel = null }) }
                    TvLazyRow(modifier = Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(36.p), contentPadding = PaddingValues(vertical = 20.p, horizontal = 4.p)) {
                        items(hist.take(20), key = { it.key }) { h ->
                            FocusCard(Modifier.onKey { if (it == Key.Menu) { askDel = h; true } else false }, w = 340, h = 190, brush = grad(h.key), onLongClick = { askDel = h }, onClick = { resume(h) }) {
                                Poster(h.pic, h.key, wPx = 340, hPx = 190); CardTitle(h.title + if (h.epName.isNotBlank() && h.epName != "正片") " ${h.epName}" else "")
                                if (h.dur > 0) ProgressBar(h.pos.toFloat() / h.dur)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(60.p), modifier = Modifier.padding(top = 10.p)) {
                    Column {
                        T("热门直播", 30, FontWeight.Bold, modifier = Modifier.padding(bottom = 18.p))
                        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(36.p), contentPadding = PaddingValues(vertical = 20.p, horizontal = 4.p), modifier = Modifier.focusRestorer().widthIn(max = 800.p)) {
                            items(live, key = { it.key }) { c -> FocusCard(w = 230, h = 118, brush = grad(c.name), onClick = { Nav.go(Scr.Live(c.key)) }) { Badge("● 直播"); CardTitle(c.name) } }
                        }
                    }
                    Column {
                        T("最近添加到 NAS", 30, FontWeight.Bold, modifier = Modifier.padding(bottom = 18.p))
                        TvLazyRow(modifier = Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(36.p), contentPadding = PaddingValues(vertical = 20.p, horizontal = 4.p)) {
                            if (HomeData.nas.isEmpty()) item { FocusCard(w = 300, h = 118, brush = Grads[7], onClick = { Nav.tab = 5 }) { CardTitle(if (Jellyfin.configured) "暂无新内容" else "设置 NAS", if (Jellyfin.configured) null else "添加 Jellyfin / WebDAV / SMB") } }
                            items(HomeData.nas, key = { it.id }) { j -> FocusCard(w = 230, h = 118, brush = grad(j.id), onClick = { openJf(j) }) { Poster(Jellyfin.img(j.id, "Backdrop", 400), j.id, wPx = 230, hPx = 118); CardTitle(j.seriesName.ifBlank { j.name }) } }
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
