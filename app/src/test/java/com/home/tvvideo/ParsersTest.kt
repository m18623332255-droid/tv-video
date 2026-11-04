package com.home.tvvideo

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ParsersTest {
    private fun asset(n: String) = File("src/main/assets/$n").readText()

    @Test fun m3uBuiltin() {
        val p = Parsers.parseLive(asset("live.m3u"))
        val groups = p.channels.groupBy { it.group }.mapValues { it.value.size }
        println("channels=${p.channels.size} groups=$groups lines=${p.channels.sumOf { it.urls.size }}")
        assertTrue(p.channels.size > 100)
        assertTrue(groups.keys.containsAll(listOf("央视", "卫视")))
        val cctv1 = p.channels.first { it.key == "CCTV1" }
        assertTrue("CCTV1 多线路", cctv1.urls.size > 1)
        val cetv = p.channels.first { it.key == "CETV1" }
        assertTrue(cetv.urls.size >= 4); assertEquals("央视", cetv.group)
        assertFalse(p.channels.any { it.name.startsWith("2026-") })
    }
    @Test fun normName() {
        assertEquals("CCTV1", Parsers.normName("CCTV-1 综合"))
        assertEquals("CCTV5+", Parsers.normName("CCTV5+ 体育赛事"))
        assertEquals(Parsers.normName("湖南卫视 HD"), Parsers.normName("湖南卫视"))
        assertEquals("ANHUITV", Parsers.normName("Anhui TV [Geo-blocked]"))
    }
    @Test fun txtFormat() {
        val p = Parsers.parseLive("央视频道,#genre#\nCCTV-1,http://a/1.m3u8#http://b/1.m3u8\nCCTV1高清,http://c/1.m3u8\n卫视频道,#genre#\n浙江卫视,http://z.m3u8")
        assertEquals(2, p.channels.size)
        assertEquals(3, p.channels[0].urls.size); assertEquals("卫视", p.channels[1].group)
    }
    @Test fun epgHeader() {
        assertEquals("http://e.xml", Parsers.parseLive("#EXTM3U x-tvg-url=\"http://e.xml\"\n#EXTINF:-1,A\nhttp://a").epgUrl)
    }
    @Test fun cmsParse() {
        val json = """{"code":1,"page":"1","pagecount":5,"class":[{"type_id":1,"type_pid":0,"type_name":"电影片"},{"type_id":6,"type_pid":1,"type_name":"动作片"},{"type_id":2,"type_pid":0,"type_name":"连续剧"},{"type_id":30,"type_pid":0,"type_name":"伦理片"},{"type_id":31,"type_pid":0,"type_name":"里番动漫"},{"type_id":4,"type_pid":0,"type_name":"动漫片"},{"type_id":3,"type_pid":0,"type_name":"综艺片"},{"type_id":20,"type_pid":0,"type_name":"记录片"}],
          "list":[{"vod_id":9,"vod_name":"测试剧","type_name":"国产剧","vod_play_from":"abc$$${'$'}lzm3u8","vod_play_url":"第1集${'$'}http://x/1.html#第2集${'$'}http://x/2.html$$${'$'}第1集${'$'}http://y/1.m3u8#第2集${'$'}http://y/2.m3u8","vod_content":"<p>简介</p>"},
                  {"vod_id":10,"vod_name":"x","type_name":"福利写真"}]}"""
        val p = Parsers.parseCms(json)
        assertEquals(1, p.list.size); assertEquals(5, p.pageCount)
        assertFalse(p.classes.any { it.name.contains("伦理") || it.name.contains("里番") })
        val v = p.list[0]; assertEquals("简介", v.content)
        assertEquals("lzm3u8", v.lines[0].name); assertEquals(2, v.lines[0].eps.size); assertEquals("http://y/2.m3u8", v.lines[0].eps[1].url)
        assertEquals(listOf("6"), Parsers.classesFor("电影", p.classes).filter { it.pid != "0" }.map { it.id })
        assertEquals("电视剧", Parsers.topOf("国产剧")); assertEquals("动漫", Parsers.topOf("日韩动漫")); assertEquals("纪录片", Parsers.topOf("记录片")); assertEquals("电影", Parsers.topOf("喜剧片")); assertEquals("少儿", Parsers.topOf("动画片"))
        assertNull(Parsers.topOf("伦理片")); assertNull(Parsers.topOf("预告片"))
    }
    @Test fun adult() { listOf("伦理片", "里番", "福利", "写真", "AV", "国产AV").forEach { assertTrue(it, Parsers.isAdult(it)) }
        listOf("动作片", "CCTV-1", "Anhui TV", "Travel Channel").forEach { assertFalse(it, Parsers.isAdult(it)) } }

    @Test fun cmsCustomList() {
        val parsed = Cms.parseCustom("家里\n自建|https://example.com/api.php/provide/vod/\n坏的|not-a-url\n|https://example.com/x\n自建|https://example.com/api.php/provide/vod/")
        assertEquals(1, parsed.size)
        assertEquals("自建", parsed[0].name)
        assertEquals("https://example.com/api.php/provide/vod/", parsed[0].api)
    }
}
