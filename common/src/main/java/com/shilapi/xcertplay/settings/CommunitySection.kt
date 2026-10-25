package com.shilapi.xcertplay.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private data class CommunityLink(
    val title: String,
    val detail: String,
    val url: String,
    val asset: String,
    val crop: List<Float>,
)

// Links decoded locally from the author's original cards; QR pixels are never regenerated.
private val authorLink = CommunityLink(
    "作者小红书", "memphis · 9540586076", "http://xhslink.com/m/9x4l15OGreE",
    "community/profile.jpg", listOf(659f / 987, 1022f / 1347, 260f / 987, 260f / 1347),
)
private val groupLink = CommunityLink(
    "加入交流群", "WheelPlay 交流与反馈", "http://xhslink.com/m/6S7kpnDnJmC",
    "community/group.jpg", listOf(204f / 840, 513f / 1434, 432f / 840, 432f / 1434),
)

@Composable
internal fun CommunitySection() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var selected by remember { mutableStateOf<CommunityLink?>(null) }
    val expired = LocalDate.now(ZoneId.of("Asia/Shanghai")) >= LocalDate.of(2026, 11, 22)
    fun copy(link: CommunityLink) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(link.title, link.url))
        Toast.makeText(context, "链接已复制，可粘贴到小红书或浏览器打开", Toast.LENGTH_SHORT).show()
    }
    fun open(link: CommunityLink) {
        runCatching { uriHandler.openUri(link.url) }.onFailure { copy(link) }
    }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        HorizontalDivider()
        Text("作者与交流群", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Text("点击二维码放大，使用小红书扫一扫；也可直接打开链接。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(authorLink, groupLink).forEach { link ->
                Card(onClick = { selected = link }, modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(link.title, style = MaterialTheme.typography.titleSmall,
                            minLines = 2, maxLines = 2)
                        CommunityQr(link, Modifier.fillMaxWidth().aspectRatio(1f))
                        Text(link.detail, style = MaterialTheme.typography.bodySmall,
                            minLines = 2, modifier = Modifier.padding(top = 10.dp))
                        TextButton(onClick = { open(link) }, contentPadding = PaddingValues(0.dp)) {
                            Text(if (link == authorLink) "打开主页" else "打开群链接")
                        }
                    }
                }
            }
        }
        Text(if (expired) "群二维码可能已过期，请到作者小红书主页获取新入口。"
            else "群二维码有效期：2026 年 11 月 22 日前。过期后请到作者主页获取新入口。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp))
    }
    selected?.let { link ->
        AlertDialog(onDismissRequest = { selected = null },
            title = { Text(link.title) },
            text = {
                Column {
                    CommunityQr(link, Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(link.detail, modifier = Modifier.padding(top = 12.dp))
                    if (link == groupLink) Text(
                        if (expired) "二维码可能已过期，请到作者主页获取最新入口。" else "2026 年 11 月 22 日前有效",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { copy(link) }) { Text("复制链接") }
                    if (link == groupLink && expired)
                        TextButton(onClick = { open(authorLink) }) { Text("前往作者主页") }
                }
            },
            confirmButton = { TextButton(onClick = { open(link) }) { Text("打开链接") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("关闭") } })
    }
}

@Composable
private fun CommunityQr(link: CommunityLink, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(link.asset) {
        context.assets.open(link.asset).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }
    // Crop only at render time: retain original supplied files and their quiet zones.
    val crop = link.crop
    Canvas(modifier.background(Color.White).semantics { contentDescription = "${link.title}二维码，点击卡片放大" }) {
        drawImage(bitmap,
            srcOffset = IntOffset((crop[0] * bitmap.width).roundToInt(), (crop[1] * bitmap.height).roundToInt()),
            srcSize = IntSize((crop[2] * bitmap.width).roundToInt(), (crop[3] * bitmap.height).roundToInt()),
            dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None)
    }
}
