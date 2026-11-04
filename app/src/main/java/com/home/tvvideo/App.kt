package com.home.tvvideo

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

class App : Application(), ImageLoaderFactory {
    override fun onCreate() { super.onCreate(); CrashGuard.install(this) }

    /** 低端电视：小内存缓存、RGB_565、无淡入动画 */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.12).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(80L * 1024 * 1024).build() }
        .bitmapConfig(Bitmap.Config.RGB_565).allowRgb565(true).crossfade(false)
        .build()
}

/** 全局崩溃捕获：写入文件，下次启动显示安全模式页面 */
object CrashGuard {
    private fun f(c: Context) = File(c.filesDir, "last_crash.txt")
    fun install(app: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
                f(app).writeText("时间：$time\n线程：${t.name}\n版本：${BuildConfig.VERSION_NAME}\n\n$sw")
            }
            if (t === android.os.Looper.getMainLooper().thread) { Process.killProcess(Process.myPid()); exitProcess(10) }
            else prev?.uncaughtException(t, e)
        }
    }
    fun pending(c: Context): String? = runCatching { f(c).takeIf { it.exists() }?.readText() }.getOrNull()
    fun clear(c: Context) { runCatching { f(c).delete() } }
}

@Composable
fun SafeModeScreen(err: String, onContinue: () -> Unit, onExit: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.safeFocus() }
    Column(Modifier.fillMaxSize().background(BG).padding(start = 96.p, end = 96.p, top = 80.p, bottom = 60.p)) {
        T("家庭影视 · 安全模式", 56, FontWeight.Bold)
        T("上次运行时程序出错退出了，错误信息如下（可拍照发给开发者）：", 26, alpha = .8f, modifier = Modifier.padding(vertical = 20.p))
        Row(horizontalArrangement = Arrangement.spacedBy(20.p)) {
            Btn("重新进入", play = true, focusRequester = fr, onClick = onContinue)
            Btn("退出", onClick = onExit)
        }
        Box(Modifier.padding(top = 24.p).fillMaxWidth().weight(1f).background(Color(0xFF1A1A22)).padding(24.p).verticalScroll(rememberScrollState())) {
            T(err.take(6000), 18, alpha = .9f, lines = 200)
        }
    }
}
