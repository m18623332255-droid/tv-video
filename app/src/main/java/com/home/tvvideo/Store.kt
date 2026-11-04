package com.home.tvvideo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

val Context.ds by preferencesDataStore("tvvideo")

data class History(
    val key: String, val title: String, val pic: String, val kind: String, // cms|jf|dav|smb
    val ref: String, val line: Int, val ep: Int, val epName: String, val pos: Long, val dur: Long, val time: Long
) {
    fun json() = JSONObject().put("key", key).put("title", title).put("pic", pic).put("kind", kind).put("ref", ref).put("line", line)
        .put("ep", ep).put("epName", epName).put("pos", pos).put("dur", dur).put("time", time)
    companion object { fun of(o: JSONObject) = History(o.optString("key"), o.optString("title"), o.optString("pic"), o.optString("kind"), o.optString("ref"),
        o.optInt("line"), o.optInt("ep"), o.optString("epName"), o.optLong("pos"), o.optLong("dur"), o.optLong("time")) }
}

/** 所有设置/收藏/记录，DataStore 持久化，内存中以 StateFlow 暴露 */
object Store {
    private lateinit var ctx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val s = MutableStateFlow<Map<String, String>>(emptyMap())
    val history = MutableStateFlow<List<History>>(emptyList())
    val favs = MutableStateFlow<Set<String>>(emptySet())

    suspend fun init(c: Context) {
        ctx = c.applicationContext
        val p = ctx.ds.data.first()
        s.value = p.asMap().entries.associate { it.key.name to it.value.toString() }
        history.value = runCatching { JSONArray(get("history", "[]")).let { a -> (0 until a.length()).map { History.of(a.getJSONObject(it)) } } }.getOrDefault(emptyList())
        favs.value = get("favs", "").split("\n").filter { it.isNotBlank() }.toSet()
    }
    fun get(k: String, d: String = "") = s.value[k] ?: d
    fun set(k: String, v: String) {
        s.value = s.value + (k to v)
        scope.launch { ctx.ds.edit { it[stringPreferencesKey(k)] = v } }
    }
    fun toggleFav(key: String) {
        favs.value = if (key in favs.value) favs.value - key else favs.value + key
        set("favs", favs.value.joinToString("\n"))
    }
    fun saveHistory(h: History) {
        history.value = (listOf(h) + history.value.filter { it.key != h.key }).take(60)
        set("history", JSONArray().apply { history.value.forEach { put(it.json()) } }.toString())
    }
    fun findHistory(key: String) = history.value.firstOrNull { it.key == key }

    var liveUrls: List<String>
        get() = get("liveUrls").split("\n").filter { it.isNotBlank() }
        set(v) = set("liveUrls", v.joinToString("\n"))
}
