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

    private val _lastWatched = MutableLiveData<WatchHistory?>()
    val lastWatched: LiveData<WatchHistory?> = _lastWatched

    private val _watchMap = MutableLiveData<Map<String, WatchHistory>>(emptyMap())
    val watchMap: LiveData<Map<String, WatchHistory>> = _watchMap

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun load(anilistId: String) = viewModelScope.launch {
        _loading.value = true
        try {
            val result = repo.getAniListInfo(anilistId)
            _info.value = result
            _episodes.value = result.episodes?.map { ep ->
                EpisodeItem(ep.id, ep.number, ep.title, ep.image)
            } ?: emptyList()
            _lastWatched.value = repo.getLastWatched(anilistId)
            val history = repo.getAnimeWatchHistory(anilistId)
            _watchMap.value = history.associateBy { it.episodeId }
        } catch (e: Exception) {
            _error.value = "Failed to load: ${e.message}"
        }
        _loading.value = false
    }

    fun loadDubEpisodes(firstSubEpisodeId: String) = viewModelScope.launch {
        val animeSlug = firstSubEpisodeId.replace(Regex("-episode-\\d+$"), "")
        val dubSlug = "$animeSlug-dub"
        try {
            val result = repo.getAnimeInfo(dubSlug)
            _episodes.value = result.episodes?.map { ep ->
                EpisodeItem(ep.id, ep.number, null, null)
            } ?: emptyList()
        } catch (_: Exception) {
            _error.value = "Dub not available for this anime"
        }
    }
}
