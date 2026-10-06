@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.album.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScaleimport androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.album.data.MediaItem

@Composable
fun Thumb(item: MediaItem, favorite: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(1f)
            .background(Color(0x22808080))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        val shadowStyle = LocalTextStyle.current.copy(
            shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f)
        )
        if (item.isVideo) {
            Text(
                item.durationMs.fmtDuration(),
                color = Color.White,
                fontSize = 11.sp,
                style = shadowStyle,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
            )
        }
        if (favorite) {
            Icon(
                Icons.Filled.Favorite, null, tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).size(12.dp)
            )
        }
    }
}

@Composable
fun SegmentedControl(    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val track = if (dark) Color(0xFF1C1C1E) else Color(0xFFEEEEF0)
    val pill = if (dark) Color(0xFF636366) else Color.White
    Row(modifier.clip(RoundedCornerShape(10.dp)).background(track).padding(2.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sel) pill else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun PageTitle(text: String) {
    Text(
        text,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {    Text(
        text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun CoverTile(
    item: MediaItem?,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x22808080))
            .clickable(onClick = onClick)
    ) {
        if (item != null) {
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier.matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xCCFFFFFF), fontSize = 13.sp)
        }
    }
}

/** 双指捏合切换列数（和苹果相册一样：捏合放大 = 列数变少）。 */
private fun Modifier.pinchToChangeColumns(columns: Int, onChange: (Int) -> Unit): Modifier =    pointerInput(columns) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var scale = 1f
            var done = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!done && event.changes.count { it.pressed } >= 2) {
                    scale *= event.calculateZoom()
                    if (scale > 1.35f && columns > 1) {
                        done = true; onChange(columns - 1)
                    } else if (scale < 0.75f && columns < 8) {
                        done = true; onChange(columns + 1)
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

@Composable
fun PinchGrid(
    items: List<MediaItem>,
    favorites: Set<Long>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    initialColumns: Int = 4,
    onOpen: (Int) -> Unit,
) {
    var columns by rememberSaveable { mutableIntStateOf(initialColumns) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize().pinchToChangeColumns(columns) { columns = it },
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        itemsIndexed(items, key = { _, m -> m.id }) { index, m ->
            Thumb(m, m.id in favorites) { onOpen(index) }
        }
    }
}
