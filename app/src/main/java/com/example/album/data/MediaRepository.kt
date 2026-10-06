package com.example.album.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.time.Instant
import java.time.ZoneId

object MediaRepository {
    private const val TYPE_IMAGE = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
    private const val TYPE_VIDEO = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
    private const val MAX_MOTION_SCAN_SIZE = 60 * 1024 * 1024

    fun load(context: Context): List<MediaItem> {
        val result = ArrayList<MediaItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(TYPE_IMAGE.toString(), TYPE_VIDEO.toString())
        val zone = ZoneId.systemDefault()

        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection, selection, args, null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val takenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val durCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val bucketCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val pathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val isVideo = c.getInt(typeCol) == TYPE_VIDEO
                    val taken = c.getLong(takenCol)
                    val added = c.getLong(addedCol)
                    val millis = if (taken > 0) taken else added * 1000
                    val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val uri = ContentUris.withAppendedId(base, id)
                    val name = c.getString(nameCol) ?: ""
                    val lower = name.lowercase()
                    val isLive = !isVideo &&
                        (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) &&
                        isMotionPhoto(context, uri)
                    result.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            name = name,
                            isVideo = isVideo,
                            takenMillis = millis,
                            durationMs = c.getLong(durCol),
                            bucket = c.getString(bucketCol) ?: "",
                            relPath = c.getString(pathCol) ?: "",
                            date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(),
                            isLivePhoto = isLive,
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        result.sortByDescending { it.takenMillis }
        return result
    }

    /** 检测 JPG 是否为动态照片（小米/Google 格式：文件末尾内嵌一段 MP4 微视频）。 */
    private fun isMotionPhoto(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val head = ByteArray(2)
                if (ins.read(head) != 2 || head[0] != 0xFF.toByte() || head[1] != 0xD8.toByte()) {
                    return@use false
                }
                val rest = ins.readBytes()
                if (rest.size > MAX_MOTION_SCAN_SIZE) return@use false
                hasFtyp(rest)
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** 在字节流中查找 MP4 的 "ftyp" box 起始标记。 */
    private fun hasFtyp(bytes: ByteArray): Boolean {
        val size = bytes.size
        var i = 0
        while (i + 4 <= size) {
            if (bytes[i].toInt() == 0x66 && bytes[i + 1].toInt() == 0x74 &&
                bytes[i + 2].toInt() == 0x79 && bytes[i + 3].toInt() == 0x70
            ) return true
            i++
        }
        return false
    }
}
