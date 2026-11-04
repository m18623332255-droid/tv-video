package com.home.tvvideo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import kotlinx.coroutines.launch

@Composable
fun SettingsTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 96.p, end = 96.p, top = 150.p, bottom = 80.p)) {
        T("设置", 64, FontWeight.Bold)
        if (msg.isNotBlank()) T(msg, 24, color = androidx.compose.ui.graphics.Color(0xFF34C759), modifier = Modifier.padding(top = 10.p), lines = 3)

        Section("点播源（苹果 CMS）") {
            var cur by remember { mutableStateOf(Cms.cur?.api.orEmpty()) }
            T("不内置任何点播接口。请添加你自己的苹果 CMS JSON 接口（通常以 api.php/provide/vod/ 结尾），并且只使用你有权访问的地址。", 22, alpha = .7f, lines = 3)
            if (Cms.sources.isEmpty()) {
                T("当前没有点播源。添加后，「点播」和首页推荐才会显示在线影片。", 22, alpha = .9f)
            } else {
                TvLazyRow(horizontalArrangement = Arrangement.spacedBy(10.p), contentPadding = PaddingValues(8.p)) {
                    items(Cms.sources) { s -> Pill(s.name, s.api == cur, size = 22, padH = 20, padV = 8, radius = 14) {
                        Store.set("cmsApi", s.api); cur = s.api; clearVod(); msg = "已切换点播源：${s.name}"
                    } }
                }
                Cms.sources.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("${s.name}    ${s.api}", 20, modifier = Modifier.width(1100.p), lines = 2)
                        Pill("删除", size = 20, padH = 16, padV = 6) {
                            Cms.replace(Cms.sources.filter { it.api != s.api }); cur = Cms.cur?.api.orEmpty(); clearVod(); msg = "已删除点播源「${s.name}」"
                        }
                    }
                }
            }
            var name by remember { mutableStateOf("") }; var api by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.p)) {
                TvTextField(name, { name = it }, "名称", Modifier.width(260.p)); TvTextField(api, { api = it }, "https://example.com/api.php/provide/vod/", Modifier.width(900.p))
                Btn("添加") {
                    val u = api.trim()
                    if (u.startsWith("http")) {
                        Cms.replace(Cms.sources + CmsSource(name.trim().ifBlank { "自定义" }, u))
                        cur = Cms.cur?.api.orEmpty(); clearVod(); name = ""; api = ""; msg = "已添加点播源，打开「点播」即可浏览"
                    } else msg = "接口地址需要以 http:// 或 https:// 开头"
                }
            }
        }
        Section("直播源") {
            var url by remember { mutableStateOf("") }
            T("内置源 + 自定义源（m3u / txt），同名频道自动合并为多线路", 22, alpha = .6f)
            Store.liveUrls.forEach { u -> Row(verticalAlignment = Alignment.CenterVertically) { T(u, 22, modifier = Modifier.width(1200.p)); Pill("删除", size = 20, padH = 16, padV = 6) { Store.liveUrls = Store.liveUrls - u; msg = "已删除" } } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.p)) {
                TvTextField(url, { url = it }, "http://example.com/live.m3u 或 .txt", Modifier.width(1000.p))
                Btn("添加") { if (url.startsWith("http")) { Store.liveUrls = Store.liveUrls + url.trim(); url = ""; msg = "已添加，点击「远程更新」拉取" } }
                Btn("远程更新") { msg = "正在更新直播源…"; scope.launch { val p = LiveRepo.load(ctx, true); msg = "更新完成：共 ${p.channels.size} 个频道" + if (p.epgUrl.isNotBlank()) "，EPG：${p.epgUrl}" else "" } }
            }
            var epg by remember { mutableStateOf(Store.get("epgUrl")) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.p), modifier = Modifier.padding(top = 10.p)) {
                TvTextField(epg, { epg = it }, "自定义 EPG（DIYP 接口，如 https://epg.112114.xyz/?ch={ch}&date={date}）", Modifier.width(1000.p))
                Btn("保存") { Store.set("epgUrl", epg.trim()); msg = "已保存 EPG 接口" }
            }
        }
        Section("Jellyfin") {
            var u by remember { mutableStateOf(Store.get("jfUrl")) }; var n by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }
            if (Jellyfin.configured) T("已登录：${Jellyfin.base}", 22, alpha = .7f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.p)) {
                TvTextField(u, { u = it }, "http://192.168.1.10:8096", Modifier.width(600.p)); TvTextField(n, { n = it }, "用户名", Modifier.width(260.p)); TvTextField(pw, { pw = it }, "密码", Modifier.width(260.p), password = true)
                Btn("登录") { msg = "登录中…"; scope.launch { runCatching { Jellyfin.login(u.trim(), n, pw) }.onSuccess { LibState.movies.clear(); LibState.series.clear(); HomeData.nas = runCatching { Jellyfin.latest() }.getOrDefault(emptyList()); msg = "Jellyfin 登录成功" }.onFailure { msg = "登录失败：${it.message}" } } }
                if (Jellyfin.configured) Btn("退出") { Store.set("jfToken", ""); HomeData.nas = emptyList(); msg = "已退出 Jellyfin" }
            }
        }
        Section("WebDAV") {
            var u by remember { mutableStateOf(Store.get("davUrl")) }; var n by remember { mutableStateOf(Store.get("davUser")) }; var pw by remember { mutableStateOf(Store.get("davPass")) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.p)) {
                TvTextField(u, { u = it }, "http://192.168.1.10:5005/video", Modifier.width(600.p)); TvTextField(n, { n = it }, "用户名", Modifier.width(260.p)); TvTextField(pw, { pw = it }, "密码", Modifier.width(260.p), password = true)
                Btn("保存") { Store.set("davUrl", u.trim()); Store.set("davUser", n); Store.set("davPass", pw); LibState.davPath = null; msg = "已保存 WebDAV" }
            }
        }
        Section("SMB 共享") {
            var h by remember { mutableStateOf(Store.get("smbHost")) }; var sh by remember { mutableStateOf(Store.get("smbShare")) }
            var n by remember { mutableStateOf(Store.get("smbUser")) }; var pw by remember { mutableStateOf(Store.get("smbPass")) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.p)) {
                TvTextField(h, { h = it }, "主机 192.168.1.10", Modifier.width(340.p)); TvTextField(sh, { sh = it }, "共享名 video", Modifier.width(240.p))
                TvTextField(n, { n = it }, "用户名（空=来宾）", Modifier.width(260.p)); TvTextField(pw, { pw = it }, "密码", Modifier.width(220.p), password = true)
                Btn("保存") { Store.set("smbHost", h.trim()); Store.set("smbShare", sh.trim().trim('/', '\\')); Store.set("smbUser", n); Store.set("smbPass", pw); Smb.reset(); LibState.smbPath = ""; msg = "已保存 SMB（仅支持 SMB2/3）" }
            }
        }
        Section("其他") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.p)) {
                Btn("清空播放记录") { Store.history.value = emptyList(); Store.set("history", "[]"); msg = "已清空播放记录" }
                Btn("清空收藏") { Store.favs.value = emptySet(); Store.set("favs", ""); msg = "已清空收藏" }
            }
            T("家庭影视 TV 1.0 · 本软件只是播放器，不内置、不提供任何影视内容。点播接口与家庭媒体库由你自行添加，请确保来源合法。", 20, alpha = .5f, modifier = Modifier.padding(top = 16.p), lines = 2)
        }
    }
}

private fun clearVod() {
    VodState.classes = emptyList()
    VodState.items.clear()
    VodState.page = 0
    VodState.pageCount = 1
    VodState.srcApi = ""
    VodState.error = ""
    HomeData.latest = emptyList()
    HomeData.hero = null
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 30.p).fillMaxWidth().glass(28).padding(30.p), verticalArrangement = Arrangement.spacedBy(14.p)) {
        T(title, 30, FontWeight.Bold); content()
    }
}
