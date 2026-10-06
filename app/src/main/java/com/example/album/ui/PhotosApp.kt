package com.example.album.ui

import android.app.Activity
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.album.data.AlbumViewModel
import com.example.album.data.MediaItem

@Composable
fun PhotosApp(vm: AlbumViewModel) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var collect by remember { mutableStateOf<Collect?>(null) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var granted by remember { mutableStateOf(hasMediaPermission(ctx)) }
    var asked by rememberSaveable { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        asked = true
        granted = hasMediaPermission(ctx)
        if (granted) vm.refresh()
    }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewer = null
            vm.refresh()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = hasMediaPermission(ctx)
        if (granted) vm.refresh()
    }

    val open: (List<MediaItem>, Int) -> Unit = { list, i -> viewer = ViewerRequest(list, i) }
    val bg = MaterialTheme.colorScheme.background

    Box(Modifier.fillMaxSize().background(bg)) {
        Scaffold(
            containerColor = bg,
            bottomBar = { AppBottomBar(tab) { tab = it } }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (!granted) {
                    PermissionScreen(
                        asked = asked,
                        onRequest = { permLauncher.launch(requiredPermissions()) },
                        onSettings = { openAppSettings(ctx) }
                    )
                } else {
                    when (tab) {
                        0 -> LibraryScreen(vm.items, vm.favorites, open) { collect = it }
                        1 -> ForYouScreen(vm.items, vm.favorites, open) { collect = it }
                        2 -> AlbumsScreen(vm.items, vm.favorites) { collect = it }
                        else -> SearchScreen(vm.items, vm.favorites, open)
                    }
                }
            }
        }

        collect?.let { c ->
            val list = remember(vm.items, vm.favorites, c) {
                vm.items.filter { c.predicate(it, vm.favorites) }
            }
            CollectionScreen(c.title, list, vm.favorites, onBack = { collect = null }, onOpen = open)
        }

        val last = remember { arrayOfNulls<ViewerRequest>(1) }
        if (viewer != null) last[0] = viewer
        AnimatedVisibility(
            visible = viewer != null,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(200)),
            exit = fadeOut(tween(150))
        ) {
            last[0]?.let { req ->
                Viewer(
                    req = req,
                    favorites = vm.favorites,
                    onToggleFav = vm::toggleFavorite,
                    onShare = { shareItem(ctx, it) },
                    onDelete = { item ->
                        runCatching {
                            val sender = MediaStore.createDeleteRequest(
                                ctx.contentResolver, listOf(item.uri)
                            ).intentSender
                            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        }
                    },
                    onPlay = { playVideo(ctx, it) },
                    onClose = { viewer = null }
                )
            }
        }
    }
}

@Composable
private fun CollectionScreen(
    title: String,
    items: List<MediaItem>,
    favorites: Set<Long>,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "返回", tint = AppleBlue)
            }
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${items.size} 项", color = SecondaryGray, fontSize = 14.sp, modifier = Modifier.padding(end = 16.dp))
        }
        PinchGrid(
            items, favorites,
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) { onOpen(items, it) }
    }
}

@Composable
private fun AppBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = AppleBlue,
        selectedTextColor = AppleBlue,
        indicatorColor = Color.Transparent,
        unselectedIconColor = SecondaryGray,
        unselectedTextColor = SecondaryGray,
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 0.dp
    ) {
        val tabs = listOf(
            Triple("图库", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
            Triple("为你推荐", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
            Triple("相簿", Icons.Filled.Collections, Icons.Outlined.Collections),
            Triple("搜索", Icons.Filled.Search, Icons.Outlined.Search),
        )
        tabs.forEachIndexed { i, (label, filled, outlined) ->
            NavigationBarItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                icon = { Icon(if (selected == i) filled else outlined, label) },
                label = { Text(label, fontSize = 11.sp) },
                colors = colors,
            )
        }
    }
}

@Composable
private fun PermissionScreen(asked: Boolean, onRequest: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("访问你的照片", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "需要读取相册中的照片和视频才能显示。所有内容只在本机处理，不会上传。",
            color = SecondaryGray, textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Button(onClick = if (asked) onSettings else onRequest) {
            Text(if (asked) "前往设置开启权限" else "允许访问")
        }
    }
}
