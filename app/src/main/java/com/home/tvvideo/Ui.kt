package com.home.tvvideo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
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

/** tvOS 风格聚焦卡片：放大 + 阴影 + 白描边 + 左上高光 */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier, w: Int, h: Int, radius: Int = 22, scale: Float = 1.1f, brush: Brush = Grads[0],
    focusRequester: FocusRequester? = null, onFocus: () -> Unit = {}, onClick: () -> Unit, content: @Composable BoxScope.(Boolean) -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val f by src.collectIsFocusedAsState()
    LaunchedEffect(f) { if (f) onFocus() }
    val s by animateFloatAsState(if (f) scale else 1f, label = "s")
    val shape = RoundedCornerShape(radius.p)
    Box(
        modifier.size(w.p, h.p).zIndex(if (f) 5f else 0f)
            .graphicsLayer { scaleX = s; scaleY = s }
            .shadow(if (f) 30.dp else 6.dp, shape)
            .clip(shape).background(brush)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(src, null, onClick = onClick)
            .then(if (f) Modifier.border(2.dp, Color(0xD9FFFFFF), shape) else Modifier)
    ) {
        content(f)
        if (!f) Box(Modifier.matchParentSize().background(Color(0x33000000)))
        else Box(Modifier.matchParentSize().background(Brush.linearGradient(0f to Color(0x55FFFFFF), 0.5f to Color.Transparent)))
    }
}

/** 胶囊按钮/标签：聚焦时白底黑字 */
@Composable
fun Pill(text: String, selected: Boolean = false, modifier: Modifier = Modifier, size: Int = 24, padH: Int = 30, padV: Int = 12, radius: Int = 30,
         focusRequester: FocusRequester? = null, onFocus: () -> Unit = {}, onClick: () -> Unit = {}) {
    val src = remember { MutableInteractionSource() }
    val f by src.collectIsFocusedAsState()
    LaunchedEffect(f) { if (f) onFocus() }
    val s by animateFloatAsState(if (f) 1.08f else 1f, label = "p")
    val shape = RoundedCornerShape(radius.p)
    Box(
        modifier.graphicsLayer { scaleX = s; scaleY = s }.then(if (f) Modifier.shadow(18.dp, shape) else Modifier).clip(shape)
            .background(when { f -> Color(0xFFFFFFFF); selected -> Color(0x40FFFFFF); else -> Color.Transparent })
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(src, null, onClick = onClick).padding(horizontal = padH.p, vertical = padV.p)
    ) { Text(text, color = if (f) Color(0xFF111111) else Color.White.copy(if (selected) 1f else .75f), fontSize = size.ps, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, play: Boolean = false, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val f by src.collectIsFocusedAsState()
    val s by animateFloatAsState(if (f) 1.08f else 1f, label = "b")
    val shape = RoundedCornerShape(16.p)
    Row(
        modifier.graphicsLayer { scaleX = s; scaleY = s }.then(if (f) Modifier.shadow(20.dp, shape) else Modifier).clip(shape)
            .background(if (f) Color.White else Color(0x2EFFFFFF))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(src, null, onClick = onClick).padding(horizontal = 40.p, vertical = 16.p), verticalAlignment = Alignment.CenterVertically
    ) { Text((if (play) "▶  " else "") + text, color = if (f) Color(0xFF111111) else Color.White, fontSize = 26.ps, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun T(text: String, size: Int = 24, w: FontWeight = FontWeight.Normal, alpha: Float = 1f, color: Color = Color.White, lines: Int = 1, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, fontSize = size.ps, fontWeight = w, color = color.copy(alpha = alpha * color.alpha), maxLines = lines, overflow = TextOverflow.Ellipsis, lineHeight = (size * 1.5).toInt().ps)

@Composable
fun Tag(t: String) = Box(Modifier.padding(end = 12.p).border(2.p, Color(0xB3FFFFFF), RoundedCornerShape(8.p)).padding(horizontal = 12.p, vertical = 4.p)) { T(t, 20, FontWeight.Bold) }

@Composable
fun Clock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { now = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date()); delay(10_000) } }
    T(now, 28, FontWeight.SemiBold, modifier = modifier)
}

@Composable
fun Poster(url: String?, seed: Any?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(grad(seed))) {
        if (!url.isNullOrBlank()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun CardTitle(t: String, sub: String? = null) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xAA000000)))))
    Column(Modifier.fillMaxSize().padding(start = 20.p, end = 14.p, bottom = 18.p), verticalArrangement = Arrangement.Bottom) {
        T(t, 26, FontWeight.Bold); if (sub != null) T(sub, 16, alpha = .7f)
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
