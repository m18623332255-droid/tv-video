package com.home.tvvideo

import android.content.Context
import android.util.Base64
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

const val UA = "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true).followSslRedirects(true).build()

suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
    val b = Request.Builder().url(url).header("User-Agent", UA); headers.forEach { (k, v) -> b.header(k, v) }
    http.newCall(b.build()).execute().use { r -> if (!r.isSuccessful) error("HTTP ${r.code}"); r.body!!.string() }
}
fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

// ---------------- 直播 ----------------
object LiveRepo {
    var playlist: Playlist = Playlist(emptyList())
    suspend fun load(ctx: Context, refresh: Boolean = false): Playlist = withContext(Dispatchers.IO) {
        if (playlist.channels.isNotEmpty() && !refresh) return@withContext playlist
        val texts = mutableListOf(ctx.assets.open("live.m3u").bufferedReader().readText())
        var epg = ""
        for (u in Store.liveUrls) {
            val cacheKey = "liveCache_" + u.hashCode()
            val t = if (refresh || Store.get(cacheKey).isBlank()) runCatching { getText(u).also { Store.set(cacheKey, it) } }.getOrNull() else Store.get(cacheKey)
            if (t != null) texts.add(0, t)
        }
        val all = mutableListOf<Channel>()
        for (t in texts) { val p = Parsers.parseLive(t); if (epg.isBlank()) epg = p.epgUrl; all += p.channels }
        playlist = Playlist(Parsers.merge(all), epg); playlist
    }
}

data class Prog(val start: String, val end: String, val title: String)
object Epg {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<Prog>>()
    /** DIYP 格式接口：{ch} {date}；取不到返回空列表（界面隐藏） */
    suspend fun get(ch: String): List<Prog> = withContext(Dispatchers.IO) { getImpl(ch) }
    private suspend fun getImpl(ch: String): List<Prog> {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val name = Parsers.normName(ch).replace("CCTV", "CCTV")
        cache[name]?.let { return it }
        val tpls = (Store.get("epgUrl").split("\n") + listOf<String>()).filter { it.contains("{ch}") }.distinct()
        for (t in tpls) {
            val r = runCatching {
                val o = JSONObject(getText(t.replace("{ch}", enc(name)).replace("{date}", date)))
                val a = o.optJSONArray("epg_data") ?: JSONArray()
                (0 until a.length()).map { a.getJSONObject(it) }.map { Prog(it.optString("start"), it.optString("end"), it.optString("title")) }
            }.getOrNull()
            if (!r.isNullOrEmpty()) { cache[name] = r; return r }
        }
        cache[name] = emptyList(); return emptyList()
    }
}

// ---------------- 点播 苹果CMS ----------------
data class CmsSource(val name: String, val api: String)
object Cms {
    var sources: List<CmsSource> = emptyList()
    val classCache = java.util.concurrent.ConcurrentHashMap<String, List<CmsClass>>()
    fun load(ctx: Context) {
        if (sources.isNotEmpty()) return
        val o = JSONObject(ctx.assets.open("vod.json").bufferedReader().readText())
        val a = o.getJSONArray("cms_apis")
        sources = (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optBoolean("ok") }.map { CmsSource(it.getString("name"), it.getString("api")) }
        val custom = Store.get("cmsCustom").split("\n").mapNotNull { l -> l.split("|").takeIf { it.size == 2 }?.let { CmsSource(it[0], it[1]) } }
        sources = custom + sources
    }
    val cur: CmsSource get() = sources.firstOrNull { it.api == Store.get("cmsApi") } ?: sources.firstOrNull() ?: CmsSource("未配置点播源", "")
    private fun u(api: String, q: String) = api + (if (api.contains("?")) "&" else "?") + q
    private suspend fun fetch(url: String): CmsPage = withContext(Dispatchers.IO) { if (url.startsWith("?") || url.startsWith("&")) error("未配置点播源，请在设置中添加"); Parsers.parseCms(getText(url)) } // 网络+JSON 解析都在后台线程
    suspend fun classes(src: CmsSource = cur): List<CmsClass> = classCache[src.api] ?: fetch(u(src.api, "ac=list")).classes.also { classCache[src.api] = it }
    suspend fun list(t: String?, pg: Int, src: CmsSource = cur): CmsPage = fetch(u(src.api, "ac=detail&pg=$pg" + (t?.let { "&t=$it" } ?: "")))
    suspend fun search(wd: String, pg: Int = 1, src: CmsSource = cur) = fetch(u(src.api, "ac=detail&wd=${enc(wd)}&pg=$pg"))
    suspend fun detail(id: String, src: CmsSource = cur) = fetch(u(src.api, "ac=detail&ids=$id")).list.firstOrNull()
}

// ---------------- Jellyfin ----------------
data class JfItem(val id: String, val name: String, val type: String, val year: String, val overview: String, val pos: Long, val runtime: Long,
                  val seriesName: String = "", val idx: Int = 0, val season: Int = 0, val rating: String = "", val genres: String = "",
                  val people: List<Pair<String, String>> = emptyList(), val streams: List<String> = emptyList())
object Jellyfin {
    val base get() = Store.get("jfUrl").trimEnd('/')
    val token get() = Store.get("jfToken")
    val uid get() = Store.get("jfUid")
    val configured get() = base.isNotBlank() && token.isNotBlank()
    private const val AUTH = "MediaBrowser Client=\"家庭影视TV\", Device=\"AndroidTV\", DeviceId=\"tvvideo-1\", Version=\"1.0\""
    private fun hdr() = mapOf("X-Emby-Authorization" to "$AUTH, Token=\"$token\"")
    suspend fun login(url: String, user: String, pass: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("Username", user).put("Pw", pass).toString().toRequestBody("application/json".toMediaType())
        val r = http.newCall(Request.Builder().url(url.trimEnd('/') + "/Users/AuthenticateByName").post(body).header("X-Emby-Authorization", AUTH).build()).execute()
        r.use { if (!it.isSuccessful) error("登录失败 HTTP ${it.code}"); val o = JSONObject(it.body!!.string())
            Store.set("jfUrl", url.trimEnd('/')); Store.set("jfToken", o.getString("AccessToken")); Store.set("jfUid", o.getJSONObject("User").getString("Id")) }
    }
    private fun item(o: JSONObject) = JfItem(o.optString("Id"), o.optString("Name"), o.optString("Type"), o.optString("ProductionYear"), o.optString("Overview"),
        o.optJSONObject("UserData")?.optLong("PlaybackPositionTicks")?.div(10000) ?: 0, o.optLong("RunTimeTicks") / 10000,
        o.optString("SeriesName"), o.optInt("IndexNumber"), o.optInt("ParentIndexNumber"), o.optString("CommunityRating").take(3),
        o.optJSONArray("Genres")?.let { g -> (0 until g.length()).joinToString(" / ") { g.getString(it) } } ?: "",
        o.optJSONArray("People")?.let { p -> (0 until minOf(8, p.length())).map { p.getJSONObject(it) }.map { it.optString("Name") to it.optString("Role") } } ?: emptyList(),
        o.optJSONArray("MediaStreams")?.let { m -> (0 until m.length()).map { m.getJSONObject(it) }.filter { it.optString("Type") != "Video" }.map { it.optString("DisplayTitle") } } ?: emptyList())
    private suspend fun items(path: String): List<JfItem> = withContext(Dispatchers.IO) { itemsImpl(path) }
    private suspend fun itemsImpl(path: String): List<JfItem> {
        val t = getText("$base$path", hdr())
        val a = if (t.trimStart().startsWith("[")) JSONArray(t) else JSONObject(t).optJSONArray("Items") ?: JSONArray()
        return (0 until a.length()).map { item(a.getJSONObject(it)) }
    }
    suspend fun library(type: String, start: Int) = items("/Users/$uid/Items?IncludeItemTypes=$type&Recursive=true&SortBy=DateCreated&SortOrder=Descending&StartIndex=$start&Limit=60&Fields=Overview")
    suspend fun search(q: String) = items("/Users/$uid/Items?searchTerm=${enc(q)}&IncludeItemTypes=Movie,Series&Recursive=true&Limit=30")
    suspend fun resume() = items("/Users/$uid/Items/Resume?Limit=12&MediaTypes=Video")
    suspend fun latest() = items("/Users/$uid/Items/Latest?Limit=16")
    suspend fun episodes(seriesId: String) = items("/Shows/$seriesId/Episodes?UserId=$uid&Fields=Overview")
    suspend fun info(id: String) = withContext(Dispatchers.IO) { item(JSONObject(getText("$base/Users/$uid/Items/$id", hdr()))) }
    fun img(id: String, type: String = "Primary", w: Int = 400) = "$base/Items/$id/Images/$type?maxWidth=$w&api_key=$token"
    fun stream(id: String) = "$base/Videos/$id/stream?static=true&api_key=$token"
    suspend fun reportStop(id: String, posMs: Long) = runCatching { withContext(Dispatchers.IO) {
        val body = JSONObject().put("ItemId", id).put("PositionTicks", posMs * 10000).toString().toRequestBody("application/json".toMediaType())
        http.newCall(Request.Builder().url("$base/Sessions/Playing/Stopped").post(body).header("X-Emby-Authorization", "$AUTH, Token=\"$token\"").build()).execute().close() } }
}

// ---------------- WebDAV ----------------
data class FileEntry(val name: String, val path: String, val dir: Boolean, val size: Long = 0)
val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "ts", "m2ts", "flv", "wmv", "rmvb", "webm", "m4v", "mpg", "mpeg", "iso", "m3u8", "3gp", "vob")
fun isVideo(n: String) = n.substringAfterLast('.', "").lowercase() in VIDEO_EXT
object WebDav {
    val base get() = Store.get("davUrl").trimEnd('/')
    val configured get() = base.isNotBlank()
    fun authHeader(): Map<String, String> { val u = Store.get("davUser"); if (u.isBlank()) return emptyMap()
        return mapOf("Authorization" to "Basic " + Base64.encodeToString("$u:${Store.get("davPass")}".toByteArray(), Base64.NO_WRAP)) }
    fun url(path: String): String { val host = Regex("^(https?://[^/]+)").find(base)?.value ?: base; return host + path }
    suspend fun list(path: String?): List<FileEntry> = withContext(Dispatchers.IO) {
        val u = if (path == null) "$base/" else url(path)
        val b = Request.Builder().url(u).method("PROPFIND", "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>".toRequestBody("text/xml".toMediaType()))
            .header("Depth", "1"); authHeader().forEach { (k, v) -> b.header(k, v) }
        val xml = http.newCall(b.build()).execute().use { if (it.code >= 400) error("HTTP ${it.code}"); it.body!!.string() }
        val self = java.net.URI(u).rawPath.trimEnd('/')
        parsePropfind(xml).filter { it.path.trimEnd('/') != self }.filter { it.dir || isVideo(it.name) }.sortedWith(compareBy({ !it.dir }, { it.name }))
    }
    fun parsePropfind(xml: String): List<FileEntry> {
        val p = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser(); p.setInput(xml.reader())
        val out = mutableListOf<FileEntry>(); var href = ""; var dir = false; var size = 0L; var tag = ""
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> { tag = p.name; if (tag == "response") { href = ""; dir = false; size = 0 }; if (tag == "collection") dir = true }
                XmlPullParser.TEXT -> { if (tag == "href") href += p.text.trim(); if (tag == "getcontentlength") size = p.text.trim().toLongOrNull() ?: 0 }
                XmlPullParser.END_TAG -> { if (p.name == "response" && href.isNotBlank()) {
                    val path = if (href.startsWith("http")) java.net.URI(href).rawPath else href
                    out += FileEntry(URLDecoder.decode(path.trimEnd('/').substringAfterLast('/'), "UTF-8"), path, dir, size) }; tag = "" }
            }
            p.next()
        }
        return out
    }
}

// ---------------- SMB (smbj) ----------------
object Smb {
    val configured get() = Store.get("smbHost").isNotBlank() && Store.get("smbShare").isNotBlank()
    private var session: Session? = null
    private var share: DiskShare? = null
    private val client by lazy { SMBClient() }
    @Synchronized fun share(): DiskShare {
        share?.let { if (it.isConnected) return it }
        val conn = client.connect(Store.get("smbHost"))
        val user = Store.get("smbUser")
        val ac = if (user.isBlank()) AuthenticationContext.guest() else AuthenticationContext(user, Store.get("smbPass").toCharArray(), Store.get("smbDomain"))
        session = conn.authenticate(ac)
        return (session!!.connectShare(Store.get("smbShare")) as DiskShare).also { share = it }
    }
    fun reset() { runCatching { session?.close() }; session = null; share = null }
    suspend fun list(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        share().list(path).filter { it.fileName != "." && it.fileName != ".." && !it.fileName.startsWith(".") }.map {
            val d = (it.fileAttributes and 0x10L) != 0L
            FileEntry(it.fileName, if (path.isEmpty()) it.fileName else "$path\\${it.fileName}", d, it.endOfFile)
        }.filter { it.dir || isVideo(it.name) }.sortedWith(compareBy({ !it.dir }, { it.name }))
    }
    fun open(path: String) = share().openFile(path, EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
}
