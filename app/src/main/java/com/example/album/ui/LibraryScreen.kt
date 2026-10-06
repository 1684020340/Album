package com.example.album.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.album.data.MediaItem

class Collect(val title: String, val predicate: (MediaItem, Set<Long>) -> Boolean)

private sealed interface Entry {
    data class Header(val text: String) : Entry
    data class Photo(val item: MediaItem, val index: Int) : Entry
}

@Composable
fun LibraryScreen(
    items: List<MediaItem>,
    favorites: Set<Long>,
    onOpen: (List<MediaItem>, Int) -> Unit,
    onCollect: (Collect) -> Unit,
) {
    var mode by rememberSaveable { mutableIntStateOf(3) }
    Column(Modifier.fillMaxSize()) {
        PageTitle("图库")
        SegmentedControl(
            listOf("年度", "月", "日", "所有照片"), mode, { mode = it },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (items.isEmpty()) {
            Text("没有照片或视频", color = SecondaryGray, modifier = Modifier.padding(24.dp))
        } else {
            when (mode) {
                0 -> YearsView(items, onCollect)
                1 -> MonthsView(items, onCollect)
                2 -> DaysView(items, favorites, onOpen)
                else -> PinchGrid(items, favorites) { onOpen(items, it) }
            }
        }
    }
}

@Composable
private fun YearsView(items: List<MediaItem>, onCollect: (Collect) -> Unit) {
    val years = remember(items) {
        items.groupBy { it.date.year }.toList().sortedByDescending { it.first }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        gridItems(years, key = { it.first }) { (year, photos) ->
            CoverTile(photos.first(), "$year", "${photos.size} 项", Modifier.aspectRatio(1f)) {
                onCollect(Collect("${year}年") { m, _ -> m.date.year == year })
            }
        }
    }
}

@Composable
private fun MonthsView(items: List<MediaItem>, onCollect: (Collect) -> Unit) {
    val months = remember(items) {
        items.groupBy { it.date.year * 100 + it.date.monthValue }
            .toList().sortedByDescending { it.first }
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(months, key = { it.first }) { (ym, photos) ->
            val y = ym / 100
            val mo = ym % 100
            CoverTile(
                photos.first(), "${y}年${mo}月", "${photos.size} 项",
                Modifier.fillMaxWidth().aspectRatio(1.6f)
            ) {
                onCollect(Collect("${y}年${mo}月") { m, _ -> m.date.year == y && m.date.monthValue == mo })
            }
        }
    }
}

@Composable
private fun DaysView(
    items: List<MediaItem>,
    favorites: Set<Long>,
    onOpen: (List<MediaItem>, Int) -> Unit,
) {
    val entries = remember(items) {
        val out = ArrayList<Entry>()
        var idx = 0
        for ((d, photos) in items.groupBy { it.date }) {
            out.add(Entry.Header(d.fmtDay()))
            for (p in photos) out.add(Entry.Photo(p, idx++))
        }
        out
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        gridItems(
            entries,
            key = { e ->
                when (e) {
                    is Entry.Header -> "h${e.text}"
                    is Entry.Photo -> "p${e.item.id}"
                }
            },
            span = { e ->
                if (e is Entry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
            }
        ) { e ->
            when (e) {
                is Entry.Header -> Text(
                    e.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
                )
                is Entry.Photo -> Thumb(e.item, e.item.id in favorites) { onOpen(items, e.index) }
            }
        }
    }
}
