package com.example.album.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.album.data.MediaItem

fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 34) arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    ) else if (Build.VERSION.SDK_INT >= 33) arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    ) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

fun hasMediaPermission(c: Context): Boolean {
    fun ok(p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 34 -> ok(Manifest.permission.READ_MEDIA_IMAGES) ||
            ok(Manifest.permission.READ_MEDIA_VIDEO) ||
            ok(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> ok(Manifest.permission.READ_MEDIA_IMAGES) ||
            ok(Manifest.permission.READ_MEDIA_VIDEO)
        else -> ok(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

fun openAppSettings(c: Context) {
    c.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun shareItem(c: Context, item: MediaItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = if (item.isVideo) "video/*" else "image/*"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    c.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun playVideo(c: Context, item: MediaItem) {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        c.startActivity(view)
    } catch (_: Exception) {
    }
}
