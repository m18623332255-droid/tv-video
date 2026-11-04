package com.home.tvvideo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import kotlinx.coroutines.launch

object LibState {
    var tab by mutableIntStateOf(0)
    val movies = mutableStateListOf<JfItem>(); val series = mutableStateListOf<JfItem>()
    var davPath by mutableStateOf<String?>(null); var smbPath by mutableStateOf("")
    var files by mutableStateOf(listOf<FileEntry>())
    var msg by mutableStateOf("")
}

@Composable
fun LibraryTab() {
    val s = LibState
    val scope = rememberCoroutineScope()
    val tabs = buildList { if (Jellyfin.configured) { add("电影"); add("剧集") }; if (WebDav.configured) add("WebDAV"); if (Smb.configured) add("SMB") }
    val cur = tabs.getOrNull(s.tab) ?: tabs.firstOrNull()
    fun loadFiles() { s.msg = "加载中…"; s.files = emptyList(); scope.launch {
        runCatching { if (cur == "WebDAV") WebDav.list(s.davPath) else Smb.list(s.smbPath) }
            .onSuccess { s.files = it; s.msg = if (it.isEmpty()) "此文件夹没有视频" else "" }
            .onFailure { if (cur == "SMB") Smb.reset(); s.msg = "连接失败：${it.message}" } } }
    LaunchedEffect(cur) {
        when (cur) {
            "电影", "剧集" -> { val l = if (cur == "电影") s.movies else s.series
                if (l.isEmpty()) { s.msg = "加载中…"; runCatching { l.addAll(Jellyfin.library(if (cur == "电影") "Movie" else "Series", 0)) }.onFailure { s.msg = "Jellyfin 连接失败：${it.message}" }; if (l.isNotEmpty()) s.msg = "" } else s.msg = "" }
            "WebDAV", "SMB" -> loadFiles()
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2A2350), Color.Transparent), center = androidx.compose.ui.geometry.Offset(380f, 0f), radius = 1400f)))
    if (tabs.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 110.p, end = 80.p), horizontalArrangement = Arrangement.End) {
        Row(Modifier.glass(24).padding(horizontal = 22.p, vertical = 12.p), verticalAlignment = Alignment.CenterVertically) {
            Dot(Color(0xFF34C759)); Spacer(Modifier.width(10.p))
            T("已连接 NAS：" + listOfNotNull(if (Jellyfin.configured) "Jellyfin" else null, if (WebDav.configured) "WebDAV" else null, if (Smb.configured) "SMB" else null).joinToString(" / "), 20)
        }
    }
    Column(Modifier.fillMaxSize().padding(start = 96.p, end = 96.p, top = 150.p)) {
        T("家庭影院", 64, FontWeight.Bold)
        if (tabs.isEmpty()) {
            Column(Modifier.padding(top = 40.p).width(1100.p).glass(28).padding(40.p)) {
                T("还没有连接家庭媒体库", 36, FontWeight.Bold)
                T("在「设置」中添加：\n· Jellyfin：填写服务器地址（如 http://192.168.1.10:8096）、账号和密码，显示电影/剧集海报墙并支持续播\n· WebDAV：填写地址（如 http://192.168.1.10:5005/video），按文件夹浏览播放\n· SMB：填写主机、共享名、账号密码，按文件夹浏览播放", 24, alpha = .8f, lines = 8, modifier = Modifier.padding(vertical = 20.p))
                Btn("前往设置") { Nav.tab = 5 }
            }
            return@Column
        }
        Row(Modifier.padding(top = 20.p), horizontalArrangement = Arrangement.spacedBy(12.p)) {
            tabs.forEachIndexed { i, t -> val n = when (t) { "电影" -> " · ${s.movies.size}"; "剧集" -> " · ${s.series.size}"; else -> "" }
                Pill(t + n, cur == t, size = 24, padH = 28, padV = 10, radius = 24, onClick = { s.tab = i }) }
        }
        if (cur == "WebDAV" || cur == "SMB") {
            val path = if (cur == "WebDAV") (s.davPath ?: "/") else "\\" + s.smbPath
            T("当前位置：$path", 22, alpha = .6f, modifier = Modifier.padding(top = 16.p))
        }
        if (s.msg.isNotBlank()) Empty(s.msg)
        when (cur) {
            "电影", "剧集" -> { val l = if (cur == "电影") s.movies else s.series
                TvLazyVerticalGrid(TvGridCells.Fixed(6), Modifier.padding(top = 10.p), horizontalArrangement = Arrangement.spacedBy(36.p), verticalArrangement = Arrangement.spacedBy(40.p), contentPadding = PaddingValues(vertical = 30.p, horizontal = 16.p)) {
                    itemsIndexed(l, key = { _, j -> j.id }) { i, j -> FocusCard(w = 236, h = 330, brush = grad(j.id), onFocus = {
                        if (i == l.size - 6 && l.size % 60 == 0) scope.launch { runCatching { l.addAll(Jellyfin.library(if (cur == "电影") "Movie" else "Series", l.size)) } } },
                        onClick = { Nav.go(Scr.Detail("jf", j.id)) }) {
                        Poster(Jellyfin.img(j.id), j.id); CardTitle(j.name, j.year); if (j.pos > 0 && j.runtime > 0) ProgressBar(j.pos.toFloat() / j.runtime) } }
                } }
            else -> {
                val up = if (cur == "WebDAV") s.davPath != null && s.davPath != "/" else s.smbPath.isNotEmpty()
                val entries = (if (up) listOf(FileEntry("..", "", true)) else emptyList()) + s.files
                TvLazyVerticalGrid(TvGridCells.Fixed(5), Modifier.padding(top = 10.p), horizontalArrangement = Arrangement.spacedBy(36.p), verticalArrangement = Arrangement.spacedBy(36.p), contentPadding = PaddingValues(vertical = 30.p, horizontal = 16.p)) {
                    itemsIndexed(entries) { _, f -> FocusCard(w = 300, h = 170, brush = if (f.dir) Grads[7] else grad(f.name), onClick = {
                        if (f.name == "..") { if (cur == "WebDAV") s.davPath = s.davPath!!.trimEnd('/').substringBeforeLast('/').ifEmpty { "/" } + "/" else s.smbPath = s.smbPath.substringBeforeLast('\\', ""); loadFiles() }
                        else if (f.dir) { if (cur == "WebDAV") s.davPath = f.path.trimEnd('/') + "/" else s.smbPath = f.path; loadFiles() }
                        else {
                            val vids = s.files.filter { !it.dir }; val idx = vids.indexOf(f)
                            val kind = if (cur == "WebDAV") "dav" else "smb"
                            val url = { e: FileEntry -> if (kind == "dav") WebDav.url(e.path) else "smb://" + e.path.replace('\\', '/') }
                            val ref = url(f); val hh = Store.findHistory("$kind:$ref")
                            Nav.go(Scr.Play(PlayReq(f.name, "", kind, ref, listOf(Line(cur ?: "", vids.map { Ep(it.name, url(it)) })), 0, idx, hh?.pos ?: 0, if (kind == "dav") WebDav.authHeader() else emptyMap())))
                        } }) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T(if (f.name == "..") "↩" else if (f.dir) "📁" else "▶", 48) }
                        CardTitle(if (f.name == "..") "返回上级" else f.name, if (!f.dir && f.size > 0) String.format("%.1f GB", f.size / 1e9) else null) } }
                }
            }
        }
    }
}
