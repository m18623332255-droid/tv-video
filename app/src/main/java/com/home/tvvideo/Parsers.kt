package com.home.tvvideo

import org.json.JSONArray
import org.json.JSONObject

data class Channel(val name: String, val group: String, val urls: MutableList<String>, val logo: String = "", val tvgName: String = "") {
    val key get() = Parsers.normName(name)
}
data class Playlist(val channels: List<Channel>, val epgUrl: String = "")

data class Ep(val name: String, val url: String)
data class Line(val name: String, val eps: List<Ep>)
data class VodItem(
    val id: String, val name: String, val pic: String = "", val remarks: String = "", val typeName: String = "",
    val year: String = "", val area: String = "", val score: String = "", val actor: String = "", val director: String = "",
    val content: String = "", val lines: List<Line> = emptyList(), val lang: String = ""
)
data class CmsClass(val id: String, val pid: String, val name: String)
data class CmsPage(val list: List<VodItem>, val page: Int, val pageCount: Int, val classes: List<CmsClass>)

object Parsers {
    val ADULT = listOf("伦理", "倫理", "里番", "裏番", "福利", "写真", "寫真", "情色", "成人", "三级", "三級", "色情", "无码", "有码", "無碼", "18禁", "性感", "美女", "激情", "黄色", "情趣", "爽片", "主播", "嫩模", "丝袜", "制服")
    fun isAdult(s: String?): Boolean { if (s.isNullOrBlank()) return false; val l = s.lowercase(); return ADULT.any { l.contains(it.lowercase()) } || Regex("(?<![a-z])(av|porn|sex|xxx)(?![a-z])").containsMatchIn(l) }

    fun normName(n: String): String {
        var s = n.uppercase()
        s = s.replace(Regex("\\[.*?]|\\(.*?\\)|（.*?）"), "")
        s = s.replace(Regex("[\\s\\-_·:：]"), "")
        s = s.replace(Regex("(超高清|高清|标清|超清|HD|FHD|UHD|4K|8K|1080P|720P|576P|频道)$"), "")
        s = s.replace(Regex("(超高清|高清|标清|超清|HD|FHD|UHD|4K|1080P|720P)$"), "")
        // CCTV1综合 -> CCTV1
        val m = Regex("^CCTV(\\d+\\+?|5\\+)").find(s)
        if (m != null) return m.value
        return s
    }

    fun cleanName(n: String): String = n.replace(Regex("\\s*\\[.*?]|\\s*\\((\\d+p|\\d+P)\\)"), "").trim()

    fun guessGroup(name: String, given: String): String {
        val u = name.uppercase()
        if (given in listOf("央视", "卫视", "地方")) return given
        if (u.startsWith("CCTV") || u.startsWith("CETV") || name.contains("央视") || u.startsWith("CGTN")) return "央视"
        if (name.contains("卫视")) return "卫视"
        if (given.contains("央视") || given.contains("CCTV", true)) return "央视"
        if (given.contains("卫视")) return "卫视"
        if (given.contains("地方") || given.contains("省") || given.contains("市")) return "地方"
        return "其他"
    }

    /** 解析 m3u 或 txt (DIYP) 直播源，同名合并为多线路 */
    fun parseLive(text: String): Playlist {
        val raw = mutableListOf<Channel>()
        var epg = ""
        val lines = text.replace("\r", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.firstOrNull()?.startsWith("#EXTM3U") == true || lines.any { it.startsWith("#EXTINF") }) {
            epg = Regex("(x-tvg-url|url-tvg)=\"([^\"]+)\"").find(lines.firstOrNull() ?: "")?.groupValues?.get(2)?.split(",")?.firstOrNull() ?: ""
            var pending: Triple<String, String, Pair<String, String>>? = null
            for (l in lines) {
                if (l.startsWith("#EXTINF")) {
                    val g = Regex("group-title=\"([^\"]*)\"").find(l)?.groupValues?.get(1) ?: ""
                    val logo = Regex("tvg-logo=\"([^\"]*)\"").find(l)?.groupValues?.get(1) ?: ""
                    val tvg = Regex("tvg-name=\"([^\"]*)\"").find(l)?.groupValues?.get(1) ?: ""
                    val name = l.substringAfterLast(",", "").trim()
                    pending = Triple(name, g, logo to tvg)
                } else if (!l.startsWith("#") && pending != null) {
                    raw += Channel(cleanName(pending.first), pending.second, mutableListOf(l), pending.third.first, pending.third.second)
                    pending = null
                }
            }
        } else {
            var group = "其他"
            for (l in lines) {
                if (l.startsWith("#")) continue
                val idx = l.indexOf(','); if (idx <= 0) continue
                val name = l.substring(0, idx).trim(); val rest = l.substring(idx + 1).trim()
                if (rest.startsWith("#genre#")) { group = name; continue }
                rest.split("#").map { it.trim() }.filter { it.contains("://") }.forEach { raw += Channel(cleanName(name), group, mutableListOf(it)) }
            }
        }
        return Playlist(merge(raw), epg)
    }

    fun merge(raw: List<Channel>): List<Channel> {
        val map = LinkedHashMap<String, Channel>()
        for (c in raw) {
            if (c.name.isBlank() || Regex("^\\d{4}-\\d{2}-\\d{2}").containsMatchIn(c.name)) continue
            if (isAdult(c.name)) continue
            val k = c.key
            val ex = map[k]
            if (ex == null) map[k] = c.copy(group = guessGroup(c.name, c.group), urls = c.urls.toMutableList())
            else c.urls.forEach { if (it !in ex.urls) ex.urls += it }
        }
        return map.values.sortedWith(compareBy({ groupOrder(it.group) }, { cctvNum(it.name) }, { it.name }))
    }
    fun groupOrder(g: String) = when (g) { "央视" -> 0; "卫视" -> 1; "地方" -> 2; else -> 3 }
    private fun cctvNum(n: String): Int = Regex("^CCTV-?(\\d+)", RegexOption.IGNORE_CASE).find(n)?.groupValues?.get(1)?.toIntOrNull() ?: 999

    // ---------------- 苹果 CMS ----------------
    val TOP = listOf("推荐", "电视剧", "电影", "动漫", "综艺", "纪录片", "少儿")
    fun topOf(name: String): String? {
        if (isAdult(name)) return null
        return when {
            name.contains("少儿") || name.contains("儿童") || name.contains("亲子") || name == "动画片" -> "少儿"
            name.contains("动漫") || name.contains("动画") || name.contains("番") -> "动漫"
            name.contains("综艺") || name.contains("晚会") -> "综艺"
            name.contains("记录") || name.contains("纪录") -> "纪录片"
            name.contains("电影") && !name.contains("解说") -> "电影"
            name.endsWith("片") && !name.contains("预告") -> "电影"
            name.contains("剧") && !name.contains("短剧") && !name.contains("漫剧") -> "电视剧"
            else -> null
        }
    }

    fun parseCms(json: String): CmsPage {
        val o = JSONObject(json)
        val classes = mutableListOf<CmsClass>()
        o.optJSONArray("class")?.let { a ->
            for (i in 0 until a.length()) { val c = a.getJSONObject(i); val n = c.optString("type_name")
                if (!isAdult(n)) classes += CmsClass(c.optString("type_id"), c.optString("type_pid", "0"), n) }
        }
        val list = mutableListOf<VodItem>()
        val arr = o.optJSONArray("list") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val v = arr.getJSONObject(i)
            val tn = v.optString("type_name")
            if (isAdult(tn) || isAdult(v.optString("vod_class")) ) continue
            list += VodItem(
                id = v.optString("vod_id"), name = v.optString("vod_name"), pic = v.optString("vod_pic"),
                remarks = v.optString("vod_remarks"), typeName = tn, year = v.optString("vod_year"), area = v.optString("vod_area"),
                score = v.optString("vod_score").let { if (it == "0.0" || it == "0") "" else it }, actor = v.optString("vod_actor"),
                director = v.optString("vod_director"), content = stripHtml(v.optString("vod_content")), lang = v.optString("vod_lang"),
                lines = parsePlay(v.optString("vod_play_from"), v.optString("vod_play_url"))
            )
        }
        return CmsPage(list, o.optString("page", "1").toIntOrNull() ?: 1, o.optString("pagecount", "1").toIntOrNull() ?: 1, classes)
    }

    fun stripHtml(s: String) = s.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace("&amp;", "&").trim()

    fun parsePlay(from: String, url: String): List<Line> {
        if (url.isBlank()) return emptyList()
        val froms = from.split("$$$"); val urls = url.split("$$$")
        val res = urls.mapIndexed { i, u ->
            val eps = u.split("#").mapNotNull { e ->
                val p = e.split("$")
                when { p.size >= 2 -> Ep(p[0].ifBlank { "正片" }, p[1].trim()); p.size == 1 && p[0].startsWith("http") -> Ep("正片", p[0].trim()); else -> null }
            }.filter { it.url.isNotBlank() }
            Line(froms.getOrNull(i)?.ifBlank { null } ?: "线路${i + 1}", eps)
        }.filter { it.eps.isNotEmpty() }
        // 优先可直接播放的 m3u8 线路
        return res.sortedBy { l -> if (l.eps.any { it.url.contains(".m3u8") || it.url.contains(".mp4") }) 0 else 1 }
    }

    /** 某一级分类对应的接口分类 */
    fun classesFor(top: String, classes: List<CmsClass>): List<CmsClass> = classes.filter { topOf(it.name) == top }

    fun regionOf(area: String): String = when {
        area.contains("大陆") || area.contains("内地") || area == "中国" || area.contains("国产") -> "内地"
        area.contains("香港") || area.contains("台湾") || area.contains("港") || area.contains("台") -> "港台"
        area.contains("韩") -> "韩国"
        area.contains("日本") -> "日本"
        area.contains("美") || area.contains("英") || area.contains("法") || area.contains("德") || area.contains("欧") -> "欧美"
        else -> "其他"
    }
}
