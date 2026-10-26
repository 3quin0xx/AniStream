package com.portolan.anistream.ui.home

import android.app.Application
import androidx.lifecycle.*
import com.portolan.anistream.data.models.AniListAnime
import com.portolan.anistream.data.models.RecentEpisode
import com.portolan.anistream.data.repository.AnimeRepository
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AnimeRepository(app)

    private val _trending = MutableLiveData<List<AniListAnime>>()
    val trending: LiveData<List<AniListAnime>> = _trending

    private val _popular = MutableLiveData<List<AniListAnime>>()
    val popular: LiveData<List<AniListAnime>> = _popular

    private val _recent = MutableLiveData<List<RecentEpisode>>()
    val recent: LiveData<List<RecentEpisode>> = _recent

    val continueWatching = repo.getRecentHistory()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    init { load() }

    fun load() = viewModelScope.launch {
        _loading.value = true
        try {
            _trending.value = repo.getTrending().results
            _popular.value = repo.getPopular().results
            _recent.value = repo.getRecentEpisodes().results
        } catch (_: Exception) {}
        _loading.value = false
    }
}
