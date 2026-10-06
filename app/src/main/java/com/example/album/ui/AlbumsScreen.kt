package com.example.album.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.album.data.MediaItem

@Composable
fun AlbumsScreen(
    items: List<MediaItem>,
    favorites: Set<Long>,
    onCollect: (Collect) -> Unit,
) {
    val buckets = remember(items) {
        items.groupBy { it.bucket }.filterKeys { it.isNotBlank() }
            .toList().sortedByDescending { it.second.size }
    }
    val favs = remember(items, favorites) { items.filter { it.id in favorites } }
    val videos = remember(items) { items.filter { it.isVideo } }
    val shots = remember(items) { items.filter { it.isScreenshot } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("相簿", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("我的相簿", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        item { AlbumCard("最近项目", items) { onCollect(Collect("最近项目") { _, _ -> true }) } }
        item { AlbumCard("收藏", favs) { onCollect(Collect("收藏") { m, f -> m.id in f }) } }
        items(buckets, key = { "b${it.first}" }) { (name, photos) ->
            AlbumCard(name, photos) { onCollect(Collect(name) { m, _ -> m.bucket == name }) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("媒体类型", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        item { AlbumCard("视频", videos) { onCollect(Collect("视频") { m, _ -> m.isVideo }) } }
        item { AlbumCard("截屏", shots) { onCollect(Collect("截屏") { m, _ -> m.isScreenshot }) } }
    }
}

@Composable
private fun AlbumCard(title: String, photos: List<MediaItem>, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x22808080))
        ) {
            photos.firstOrNull()?.let {
                AsyncImage(
                    model = it.uri, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(title, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        Text("${photos.size}", fontSize = 15.sp, color = SecondaryGray)
    }
}
