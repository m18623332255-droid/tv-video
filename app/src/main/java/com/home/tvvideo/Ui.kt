@file:kotlin.OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.home.tvvideo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/** 原型按 1920x1080 px 设计，TV 一般 960dp 宽 → 除以 2 */
val Int.p: Dp get() = (this / 2f).dp
val Int.ps: TextUnit get() = (this / 2f).sp

val BG = Color(0xFF0A0A0F)
val GlassC = Color(0xCC282832)
val GlassBorder = Color(0x1AFFFFFF)
val Grads = listOf(
    listOf(0xFFFF6A3D, 0xFF7B1F5C), listOf(0xFF2BC0E4, 0xFF1A3A8A), listOf(0xFFA8E063, 0xFF1E6B4A), listOf(0xFFF7B733, 0xFFA8431B),
    listOf(0xFF8E54E9, 0xFF2B1166), listOf(0xFFFF5F8F, 0xFF3A1450), listOf(0xFF43CEA2, 0xFF185A9D), listOf(0xFF606C88, 0xFF1F2533)
).map { g -> Brush.linearGradient(g.map { Color(it) }) }
fun grad(seed: Any?) = Grads[Math.floorMod(seed.hashCode(), Grads.size)]

fun Modifier.glass(r: Int = 32) = this.clip(RoundedCornerShape(r.p)).background(GlassC).border(1.dp, GlassBorder, RoundedCornerShape(r.p))

fun Modifier.onKey(map: (Key) -> Boolean) = onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) map(it.key) else false }

/** 聚焦卡片（低端机版）：固定倍数缩放（graphicsLayer，不改变布局、无动画）+ 白描边，无阴影/渐变高光 */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier, w: Int, h: Int, radius: Int = 22, scale: Float = 1.06f, brush: Brush = Grads[0],
    focusRequester: FocusRequester? = null, onFocus: () -> Unit = {}, onLongClick: (() -> Unit)? = null, onClick: () -> Unit, content: @Composable BoxScope.(Boolean) -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val f by src.collectIsFocusedAsState()
    val cb by rememberUpdatedState(onFocus)
    LaunchedEffect(f) { if (f) cb() }
    val shape = RoundedCornerShape(radius.p)
    Box(
        modifier.size(w.p, h.p).zIndex(if (f) 5f else 0f)
            .graphicsLayer { val sc = if (f) scale else 1f; scaleX = sc; scaleY = sc }
            .clip(shape).background(brush)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (onLongClick != null) Modifier.combinedClickable(interactionSource = src, indication = null, onLongClick = onLongClick, onClick = onClick) else Modifier.clickable(src, null, onClick = onClick))
            .then(if (f) Modifier.border(3.dp, Color.White, shape) else Modifier)
    ) { content(f) }
}

/** 胶囊按钮/标签：聚焦时白底黑字（无动画无阴影） */
@Composable
fun Pill(text: String, selected: Boolean = false, modifier: Modifier = Modifier, size: Int = 24, padH: Int = 30, padV: Int = 12, radius: Int = 30,
         focusRequester: FocusRequester? = null, onFocus: () -> Unit = {}, onClick: () -> Unit = {}) {
    val src = remember { MutableInteractionSource() }
    val f by src.collectIsFocusedAsState()
    val cb by rememberUpdatedState(onFocus)
    LaunchedEffect(f) { if (f) cb() }
    val shape = RoundedCornerShape(radius.p)
    Box(
        modifier.clip(shape)
            .background(when { f -> Color(0xFFFFFFFF); selected -> Color(0x40FFFFFF); else -> Color.Transparent })
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(src, null, onClick = onClick).padding(horizontal = padH.p, vertical = padV.p)
    ) { Text(text, color = if (f) Color(0xFF111111) else Color.White.copy(if (selected) 1f else .75f), fontSize = size.ps, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, play: Boolean = false, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val f by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.p)
    Row(
        modifier.clip(shape)
            .background(if (f) Color.White else Color(0x2EFFFFFF))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(src, null, onClick = onClick).padding(horizontal = 40.p, vertical = 16.p), verticalAlignment = Alignment.CenterVertically
    ) { Text((if (play) "▶  " else "") + text, color = if (f) Color(0xFF111111) else Color.White, fontSize = 26.ps, fontWeight = FontWeight.SemiBold) }
}

/** 遥控器可操作的确认对话框，默认焦点在「取消」，返回键关闭 */
@Composable
fun ConfirmDialog(title: String, msg: String, okText: String = "确定", onOk: () -> Unit, onDismiss: () -> Unit) {
    val fr = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(820.p).clip(RoundedCornerShape(28.p)).background(Color(0xFF22222C)).padding(44.p)) {
            T(title, 36, FontWeight.Bold); T(msg, 24, alpha = .8f, lines = 3, modifier = Modifier.padding(top = 16.p, bottom = 32.p))
            Row(horizontalArrangement = Arrangement.spacedBy(20.p)) {
                Btn(okText) { onDismiss(); onOk() }
                Btn("取消", focusRequester = fr) { onDismiss() }
            }
        }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(50); fr.safeFocus() }
    }
}

/** 安全请求焦点：节点未挂载时不崩溃 */
fun FocusRequester.safeFocus(): Boolean = try { requestFocus(); true } catch (e: Throwable) { false }

@Composable
fun T(text: String, size: Int = 24, w: FontWeight = FontWeight.Normal, alpha: Float = 1f, color: Color = Color.White, lines: Int = 1, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, fontSize = size.ps, fontWeight = w, color = color.copy(alpha = alpha * color.alpha), maxLines = lines, overflow = TextOverflow.Ellipsis, lineHeight = (size * 1.5).toInt().ps)

@Composable
fun Tag(t: String) = Box(Modifier.padding(end = 12.p).border(2.p, Color(0xB3FFFFFF), RoundedCornerShape(8.p)).padding(horizontal = 12.p, vertical = 4.p)) { T(t, 20, FontWeight.Bold) }

@Composable
fun Clock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { val f = SimpleDateFormat("HH:mm", Locale.CHINA); while (true) { now = f.format(Date()); delay(60_000 - System.currentTimeMillis() % 60_000 + 50) } }
    T(now, 28, FontWeight.SemiBold, modifier = modifier)
}

@Composable
fun Poster(url: String?, seed: Any?, modifier: Modifier = Modifier, wPx: Int = 300, hPx: Int = 400) {
    Box(modifier.fillMaxSize().background(grad(seed))) {
        if (!url.isNullOrBlank()) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val req = remember(url, wPx, hPx) {
                coil.request.ImageRequest.Builder(ctx).data(url).size(wPx, hPx).crossfade(false)
                    .bitmapConfig(android.graphics.Bitmap.Config.RGB_565).allowRgb565(true).build()
            }
            AsyncImage(req, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
fun CardTitle(t: String, sub: String? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Column(Modifier.fillMaxWidth().background(Color(0x99000000)).padding(start = 20.p, end = 14.p, top = 8.p, bottom = 12.p)) {
            T(t, 26, FontWeight.Bold); if (sub != null) T(sub, 16, alpha = .7f)
        }
    }
}
@Composable
fun BoxScope.Badge(t: String, alignEnd: Boolean = false, color: Color = Color.White) {
    if (t.isBlank()) return
    Box(Modifier.align(if (alignEnd) Alignment.TopEnd else Alignment.TopStart).padding(14.p).clip(RoundedCornerShape(8.p)).background(Color(0x66000000)).padding(horizontal = 10.p, vertical = 4.p)) {
        T(t, if (alignEnd) 18 else 16, FontWeight.Bold, color = color)
    }
}
@Composable
fun BoxScope.ProgressBar(frac: Float) {
    Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.p, vertical = 12.p).fillMaxWidth().height(6.p).clip(RoundedCornerShape(3.p)).background(Color(0x4DFFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).background(Color.White))
    }
}
@Composable
fun Dot(color: Color, size: Int = 12) = Box(Modifier.size(size.p).clip(CircleShape).background(color))

@Composable
fun Empty(msg: String, modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().padding(60.p), contentAlignment = Alignment.Center) { T(msg, 26, alpha = .6f, lines = 4) }

fun fmtTime(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60) }
