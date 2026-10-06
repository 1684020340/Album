package com.example.album.data

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumViewModel(app: Application) : AndroidViewModel(app) {
    var items by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var favorites by mutableStateOf<Set<Long>>(emptySet())
        private set

    private val prefs = app.getSharedPreferences("album", Context.MODE_PRIVATE)

    init {
        favorites = (prefs.getStringSet("fav", emptySet()) ?: emptySet())
            .mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun refresh() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { MediaRepository.load(ctx) }
            items = list
        }
    }

    fun toggleFavorite(id: Long) {
        favorites = if (id in favorites) favorites - id else favorites + id
        prefs.edit().putStringSet("fav", favorites.map { it.toString() }.toSet()).apply()
    }
}
