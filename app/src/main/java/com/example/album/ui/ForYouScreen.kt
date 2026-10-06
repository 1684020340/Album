package com.example.album.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.album.data.MediaItem
import java.time.LocalDate

@Composable
fun ForYouScreen(
    items: List<MediaItem>,
    favorites: Set<Long>,
    onOpen: (List<MediaItem>, Int) -> Unit,
    onCollect: (Collect) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val favs = remember(items, favorites) { items.filter { it.id in favorites } }
    val onThisDay = remember(items) {
        items.filter {
            it.date.monthValue == today.monthValue &&
                it.date.dayOfMonth == today.dayOfMonth &&
                it.date.year < today.year
        }.groupBy { it.date.year }.toList().sortedByDescending { it.first }
    }
    val memories = remember(items) {
        items.groupBy { it.date.year * 100 + it.date.monthValue }
            .filter { it.value.size >= 6 }
            .toList().sortedByDescending { it.first }.take(12)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("为你推荐") }
        if (favs.isEmpty() && onThisDay.isEmpty() && memories.isEmpty()) {
            item {
                Text(
                    "收藏照片、积累更多照片后，这里会出现精选和回忆。",
                    color = SecondaryGray, modifier = Modifier.padding(24.dp)
                )
            }
        }
        if (favs.isNotEmpty()) {
            item { SectionTitle("精选照片") }
            item {
                val shown = favs.take(12)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(shown, key = { _, m -> m.id }) { i, m ->
                        AsyncImage(
                            model = m.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(150.dp).height(200.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpen(favs, i) }
                        )
                    }
                }
            }
        }
        if (onThisDay.isNotEmpty()) {
            item { SectionTitle("那年今日") }
            items(onThisDay, key = { "d${it.first}" }) { (year, photos) ->
                CoverTile(
                    photos.first(), "${today.year - year} 年前的今天", "${photos.size} 项",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth().aspectRatio(1.5f)
                ) {
                    onCollect(Collect("${year}年${today.monthValue}月${today.dayOfMonth}日") { m, _ ->
                        m.date.year == year && m.date.monthValue == today.monthValue &&
                            m.date.dayOfMonth == today.dayOfMonth
                    })
                }
            }
        }
        if (memories.isNotEmpty()) {
            item { SectionTitle("回忆") }
            items(memories, key = { "m${it.first}" }) { (ym, photos) ->
                val y = ym / 100
                val mo = ym % 100
                CoverTile(
                    photos.first(), "${y}年${mo}月", "${photos.size} 项",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth().aspectRatio(1.5f)
                ) {
                    onCollect(Collect("${y}年${mo}月") { m, _ ->
                        m.date.year == y && m.date.monthValue == mo
                    })
                }
            }
        }
    }
}
