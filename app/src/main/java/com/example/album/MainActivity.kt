package com.example.album

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.album.data.AlbumViewModel
import com.example.album.ui.AlbumTheme
import com.example.album.ui.PhotosApp

class MainActivity : ComponentActivity() {
    private val vm: AlbumViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AlbumTheme { PhotosApp(vm) } }
    }
}
