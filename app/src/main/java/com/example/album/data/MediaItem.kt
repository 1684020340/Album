package com.example.album.data

import android.net.Uri
import java.time.LocalDate

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val takenMillis: Long,
    val durationMs: Long,
    val bucket: String,
    val relPath: String,
    val date: LocalDate,
) {
    val isScreenshot: Boolean
        get() = relPath.contains("screenshot", true) ||
            bucket.contains("screenshot", true) ||
            bucket.contains("截屏")
}
