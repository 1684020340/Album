package com.example.album.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.time.Instant
import java.time.ZoneId

object MediaRepository {
    private const val TYPE_IMAGE = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
    private const val TYPE_VIDEO = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

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
                    result.add(
                        MediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(base, id),
                            name = c.getString(nameCol) ?: "",
                            isVideo = isVideo,
                            takenMillis = millis,
                            durationMs = c.getLong(durCol),
                            bucket = c.getString(bucketCol) ?: "",
                            relPath = c.getString(pathCol) ?: "",
                            date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(),
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        result.sortByDescending { it.takenMillis }
        return result
    }
}
