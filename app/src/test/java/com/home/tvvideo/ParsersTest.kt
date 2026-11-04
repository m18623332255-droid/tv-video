package com.home.tvvideo

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ParsersTest {
    private fun asset(n: String) = File("src/main/assets/$n").readText()

    /** 公开版 live.m3u 为空模板：能正常解析且不含任何频道地址 */
    @Test fun m3uBuiltin() {
        val p = Parsers.parseLive(asset("live.m3u"))
        assertEquals(0, p.channels.size)
        val s = Parsers.parseLive("#EXTM3U\n#EXTINF:-1 group-title=\"央视\",CCTV-1\nhttp://a/1\n#EXTINF:-1,CCTV1 高清\nhttp://b/1\n#EXTINF:-1,浙江卫视\nhttp://c/1")
        assertEquals(2, s.channels.size); assertEquals(2, s.channels.first { it.key == "CCTV1" }.urls.size)
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
        assertEquals(5, p.pageCount)
        assertTrue(p.classes.any { it.name.contains("伦理") })
        val v = p.list[0]; assertEquals("简介", v.content)
        assertEquals("lzm3u8", v.lines[0].name); assertEquals(2, v.lines[0].eps.size); assertEquals("http://y/2.m3u8", v.lines[0].eps[1].url)
        assertEquals(listOf("6"), Parsers.classesFor("电影", p.classes).filter { it.pid != "0" }.map { it.id })
        assertEquals("电视剧", Parsers.topOf("国产剧")); assertEquals("动漫", Parsers.topOf("日韩动漫")); assertEquals("纪录片", Parsers.topOf("记录片")); assertEquals("电影", Parsers.topOf("喜剧片")); assertEquals("少儿", Parsers.topOf("动画片"))
        assertEquals("电影", Parsers.topOf("伦理片")); assertEquals("其他", Parsers.topOf("预告片")); assertEquals("其他", Parsers.topOf("福利"))
    }
    /** 不做任何内容过滤：分类、影片、直播频道全部原样保留 */
    @Test fun adult() {
        val p = Parsers.parseCms("""{"class":[{"type_id":"1","type_name":"福利"},{"type_id":"2","type_name":"写真"}],"list":[{"vod_id":"9","vod_name":"x","type_name":"伦理片","vod_play_from":"a","vod_play_url":"1${'$'}http://a/1.m3u8"}]}""")
        assertEquals(2, p.classes.size); assertEquals(1, p.list.size)
        assertEquals(1, Parsers.merge(listOf(Channel("成人频道", "", mutableListOf("http://a")))).size)
        listOf("福利", "写真", "AV").forEach { assertNotNull(it, Parsers.topOf(it)) }
    }

    /** 真实接口：设置环境变量 CMS_TEST_API 后运行 */
    @Test fun cmsLive() {
        val api = System.getenv("CMS_TEST_API") ?: run { println("skip: 未设置 CMS_TEST_API"); return }
        val txt = try { java.net.URL("$api?ac=list").readText() } catch (e: Exception) { println("skip net: $e"); return }
        val p = Parsers.parseCms(txt)
        println("classes=" + Parsers.TOP.associateWith { t -> Parsers.classesFor(t, p.classes).map { it.name } })
        assertTrue(p.classes.isNotEmpty()); assertTrue(p.classes.isNotEmpty())
        val d = Parsers.parseCms(java.net.URL("$api?ac=detail&pg=1").readText())
        println("detail first=${d.list.firstOrNull()?.name} lines=${d.list.firstOrNull()?.lines?.map { it.name + ":" + it.eps.size }}")
        assertTrue(d.list.isNotEmpty() && d.list[0].lines.isNotEmpty())
    }
}
