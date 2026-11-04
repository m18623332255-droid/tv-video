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
            var cur by remember { mutableStateOf(Cms.cur.api) }
            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(10.p), contentPadding = PaddingValues(8.p)) {
                items(Cms.sources) { s -> Pill(s.name, s.api == cur, size = 22, padH = 20, padV = 8, radius = 14) { Store.set("cmsApi", s.api); cur = s.api; VodState.classes = emptyList(); VodState.items.clear(); HomeData.latest = emptyList(); msg = "已切换点播源：${s.name}" } }
            }
            var name by remember { mutableStateOf("") }; var api by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.p)) {
                TvTextField(name, { name = it }, "名称", Modifier.width(260.p)); TvTextField(api, { api = it }, "自定义接口 https://xxx/api.php/provide/vod/", Modifier.width(900.p))
                Btn("添加") { if (api.startsWith("http")) { Store.set("cmsCustom", (Store.get("cmsCustom") + "\n${name.ifBlank { "自定义" }}|$api").trim()); Cms.sources = emptyList(); Cms.load(ctx); msg = "已添加点播源" } }
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
                TvTextField(epg, { epg = it }, "自定义 EPG（DIYP 接口，如 https://你的EPG地址/?ch={ch}&date={date}）", Modifier.width(1000.p))
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
                var askClear by remember { mutableStateOf(false) }
                Btn("清除历史记录") { askClear = true }
                if (askClear) ConfirmDialog("清除历史记录", "将删除全部 ${Store.history.value.size} 条播放记录，无法恢复。", "清除", onOk = { Store.clearHistory(); msg = "已清除历史记录" }, onDismiss = { askClear = false })
                Btn("清空收藏") { Store.favs.value = emptySet(); Store.set("favs", ""); msg = "已清空收藏" }
            }
            T("家庭影视 TV ${BuildConfig.VERSION_NAME} · 点播内容来自第三方资源接口，已过滤成人分类，仅供家庭学习交流", 20, alpha = .5f, modifier = Modifier.padding(top = 16.p), lines = 2)
        }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 30.p).fillMaxWidth().glass(28).padding(30.p), verticalArrangement = Arrangement.spacedBy(14.p)) {
        T(title, 30, FontWeight.Bold); content()
    }
}
