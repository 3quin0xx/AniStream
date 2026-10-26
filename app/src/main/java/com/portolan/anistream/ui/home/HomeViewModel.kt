package com.portolan.anistream.ui.home

import android.app.Application
import androidx.lifecycle.*
import com.portolan.anistream.data.models.AniListAnime
import com.portolan.anistream.data.repository.AnimeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AnimeRepository(app)

    private val _trending = MutableLiveData<List<AniListAnime>>()
    val trending: LiveData<List<AniListAnime>> = _trending

    private val _popular = MutableLiveData<List<AniListAnime>>()
    val popular: LiveData<List<AniListAnime>> = _popular

    private val _recent = MutableLiveData<List<AniListAnime>>()
    val recent: LiveData<List<AniListAnime>> = _recent

    val continueWatching = repo.getRecentHistory()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    init { load() }

    fun load() = viewModelScope.launch {
        _loading.value = true
        val trendingDeferred = async { runCatching { repo.getTrending().results }.getOrDefault(emptyList()) }
        val popularDeferred  = async { runCatching { repo.getPopular().results }.getOrDefault(emptyList()) }
        val recentDeferred   = async { runCatching { repo.getRecentAnime().results }.getOrDefault(emptyList()) }

        _trending.value = trendingDeferred.await()
        _popular.value  = popularDeferred.await()
        _recent.value   = recentDeferred.await()
        _loading.value  = false
    }
}
