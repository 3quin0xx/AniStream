package com.portolan.anistream.ui.detail

import android.app.Application
import androidx.lifecycle.*
import com.portolan.anistream.data.db.WatchHistory
import com.portolan.anistream.data.models.AniListAnimeInfo
import com.portolan.anistream.data.models.EpisodeItem
import com.portolan.anistream.data.repository.AnimeRepository
import kotlinx.coroutines.launch

class DetailViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AnimeRepository(app)

    private val _info = MutableLiveData<AniListAnimeInfo>()
    val info: LiveData<AniListAnimeInfo> = _info

    private val _episodes = MutableLiveData<List<EpisodeItem>>()
    val episodes: LiveData<List<EpisodeItem>> = _episodes

    private val _activeProvider = MutableLiveData("animepahe")
    val activeProvider: LiveData<String> = _activeProvider

    private val _lastWatched = MutableLiveData<WatchHistory?>()
    val lastWatched: LiveData<WatchHistory?> = _lastWatched

    private val _watchMap = MutableLiveData<Map<String, WatchHistory>>(emptyMap())
    val watchMap: LiveData<Map<String, WatchHistory>> = _watchMap

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun load(anilistId: String, provider: String = _activeProvider.value ?: "animepahe", isDub: Boolean = false) =
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val (result, usedProvider) = repo.getAniListInfoWithProvider(anilistId, provider, isDub)
                _activeProvider.value = usedProvider
                _info.value = result
                _episodes.value = result.episodes?.map { ep ->
                    EpisodeItem(ep.id, ep.number, ep.title, ep.image, usedProvider)
                } ?: emptyList()
                _lastWatched.value = repo.getLastWatched(anilistId)
                val history = repo.getAnimeWatchHistory(anilistId)
                _watchMap.value = history.associateBy { it.episodeId }
            } catch (e: Exception) {
                _error.value = "Failed to load: ${e.message}"
            }
            _loading.value = false
        }
}
