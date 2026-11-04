package com.home.tvvideo

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

/**
 * 只读暴露“继续观看”给桌面（清爽桌面 com.cn.tvlauncher）：content://com.home.tvvideo.continue/items
 * 列：key,title,pic,epName,pos,dur,time（只含未看完的记录，最多 20 条）
 */
class ContinueProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val c = MatrixCursor(arrayOf("key", "title", "pic", "epName", "pos", "dur", "time"))
        val ctx = context ?: return c
        val raw = runCatching { runBlocking { ctx.ds.data.first()[stringPreferencesKey("history")] } }.getOrNull() ?: "[]"
        runCatching {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val h = History.of(a.getJSONObject(i))
                if (h.dur > 0 && h.pos > h.dur * 0.95) continue
                c.addRow(arrayOf(h.key, h.title, h.pic, h.epName, h.pos.toString(), h.dur.toString(), h.time.toString()))
                if (c.count >= 20) break
            }
        }
        return c
    }
    override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.com.home.tvvideo.continue"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
