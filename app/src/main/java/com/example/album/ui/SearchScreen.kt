package com.example.album.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.album.data.MediaItem
import java.util.Locale

private fun search(items: List<MediaItem>, favs: Set<Long>, q: String): List<MediaItem> {
    val t = q.trim().lowercase(Locale.ROOT)
    if (t.isEmpty()) return emptyList()
    return items.filter { m ->
        m.name.lowercase(Locale.ROOT).contains(t) ||
            m.bucket.lowercase(Locale.ROOT).contains(t) ||
            m.date.fmtFull().contains(t) ||
            (t in listOf("视频", "video") && m.isVideo) ||
            (t in listOf("照片", "photo") && !m.isVideo) ||
            (t in listOf("截屏", "截图", "screenshot") && m.isScreenshot) ||
            (t in listOf("收藏", "favorite") && m.id in favs)
    }
}

@Composable
fun SearchScreen(
    items: List<MediaItem>,
    favorites: Set<Long>,
    onOpen: (List<MediaItem>, Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, items, favorites) { search(items, favorites, query) }
    val fieldBg = MaterialTheme.colorScheme.surface

    Column(Modifier.fillMaxSize()) {
        PageTitle("搜索")
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
            cursorBrush = SolidColor(AppleBlue),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(fieldBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = SecondaryGray, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Box {
                        if (query.isEmpty()) Text("照片、日期、文件夹", color = SecondaryGray, fontSize = 16.sp)
                        inner()
                    }
                }
            }
        )
        when {
            query.isBlank() -> {
                Text("快捷搜索", fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("视频", "截屏", "收藏", "照片").forEach { chip ->
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(fieldBg)
                                .clickable { query = chip }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text(chip, fontSize = 15.sp, color = AppleBlue) }
                    }
                }
                Text(
                    "也可以输入「2025年3月」或文件名、文件夹名。",
                    color = SecondaryGray, fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
            results.isEmpty() -> Text("无结果", color = SecondaryGray, modifier = Modifier.padding(24.dp))
            else -> PinchGrid(results, favorites) { onOpen(results, it) }
        }
    }
}
