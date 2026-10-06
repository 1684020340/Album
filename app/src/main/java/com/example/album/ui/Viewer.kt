@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.album.ui

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.album.data.MediaItem
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ViewerRequest(val items: List<MediaItem>, val index: Int)

@Composable
fun Viewer(
    req: ViewerRequest,
    favorites: Set<Long>,
    onToggleFav: (Long) -> Unit,
    onShare: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(initialPage = req.index.coerceIn(0, req.items.lastIndex)) { req.items.size }
    var chrome by remember { mutableStateOf(true) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val progress = (dragY / 900f).coerceIn(0f, 1f)
    val bg by animateColorAsState(
        if (chrome) MaterialTheme.colorScheme.background else Color.Black, label = "viewerBg"
    )
    val current = req.items[pager.currentPage.coerceIn(0, req.items.lastIndex)]
    val fav = current.id in favorites
    val barBg = MaterialTheme.colorScheme.background.copy(alpha = 0.92f)

    Box(
        Modifier
            .fillMaxSize()
            .background(bg.copy(alpha = 1f - progress))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (dragY > 240f) onClose() else dragY = 0f },
                    onDragCancel = { dragY = 0f },
                ) { change, amount ->
                    change.consume()
                    dragY = (dragY + amount).coerceAtLeast(0f)
                }
            }
    ) {
        HorizontalPager(
            state = pager,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationY = dragY
                val s = 1f - progress * 0.25f
                scaleX = s
                scaleY = s
            }
        ) { page ->
            val item = req.items[page]
            ZoomablePage(item, onTap = { chrome = !chrome }, onPlay = { onPlay(item) })
        }

        AnimatedVisibility(
            visible = chrome && progress == 0f,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Row(
                Modifier.fillMaxWidth().background(barBg).statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "返回", tint = AppleBlue, modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.dateTitle(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(current.timeText(), fontSize = 12.sp, color = SecondaryGray)
                }
                Box(Modifier.size(48.dp))
            }
        }

        AnimatedVisibility(
            visible = chrome && progress == 0f,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Row(
                Modifier.fillMaxWidth().background(barBg).navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onShare(current) }) {
                    Icon(Icons.Outlined.Share, "分享", tint = AppleBlue)
                }
                IconButton(onClick = { onToggleFav(current.id) }) {
                    Icon(
                        if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        "收藏", tint = AppleBlue
                    )
                }
                IconButton(onClick = { onDelete(current) }) {
                    Icon(Icons.Outlined.Delete, "删除", tint = AppleBlue)
                }
            }
        }
    }
}

@Composable
private fun ZoomablePage(item: MediaItem, onTap: () -> Unit, onPlay: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var livePlaying by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f; offset = Offset.Zero
                        } else scale = 2.5f
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } >= 2
                        if (multi || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            val maxX = size.width * (scale - 1f) / 2f
                            val maxY = size.height * (scale - 1f) / 2f
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale < 1.05f) {
                        scale = 1f; offset = Offset.Zero
                    }
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(item.uri).size(2400).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
        )
        if (item.isVideo) {
            Box(
                Modifier.align(Alignment.Center).size(64.dp)
                    .clip(CircleShape).background(Color(0x99000000))
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, "播放", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
        if (item.isLivePhoto) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(16.dp).size(44.dp)
                    .clip(CircleShape)
                    .background(if (livePlaying) Color(0xCC3A76F2) else Color(0x99000000))
                    .clickable { livePlaying = !livePlaying },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (livePlaying) "×" else "实况",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            if (livePlaying) {
                MotionPhotoPlayer(item, onStop = { livePlaying = false })
            }
        }
    }
}

@Composable
private fun MotionPhotoPlayer(item: MediaItem, onStop: () -> Unit) {
    val context = LocalContext.current
    var path by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(item.uri) {
        path = withContext(Dispatchers.IO) { extractMotionVideo(context, item.uri) }
    }
    DisposableEffect(path) {
        onDispose { path?.let { runCatching { File(it).delete() } } }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onStop),
        contentAlignment = Alignment.Center
    ) {
        val p = path
        if (p != null) {
            key(p) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        VideoView(c).apply {
                            setVideoPath(p)
                            setOnPreparedListener { mp -> mp.isLooping = true; mp.start() }
                        }
                    }
                )
            }
        } else {
            Text("加载实况中…", color = Color.White, fontSize = 14.sp)
        }
    }
}

/** 从动态照片 JPG 中取出内嵌的 MP4 微视频段，写入缓存文件。 */
private fun extractMotionVideo(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { ins ->
            val all = ins.readBytes()
            val idx = lastIndexOfFtyp(all)
            if (idx <= 0 || idx + 4 >= all.size) return@use null
            val f = File(context.cacheDir, "motion_${System.nanoTime()}.mp4")
            FileOutputStream(f).use { out -> out.write(all, idx, all.size - idx) }
            f.absolutePath
        }
    } catch (_: Exception) {
        null
    }
}

private fun lastIndexOfFtyp(bytes: ByteArray): Int {
    var last = -1
    var i = 0
    while (i + 4 <= bytes.size) {
        if (bytes[i] == 0x66.toByte() && bytes[i + 1] == 0x74.toByte() &&
            bytes[i + 2] == 0x79.toByte() && bytes[i + 3] == 0x70.toByte()
        ) last = i
        i++
    }
    return last
}
