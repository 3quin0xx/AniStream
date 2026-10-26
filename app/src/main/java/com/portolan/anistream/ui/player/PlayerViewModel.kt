package com.portolan.anistream.ui.player

import android.app.Application
import androidx.lifecycle.*
import com.portolan.anistream.data.db.WatchHistory
import com.portolan.anistream.data.models.*
import com.portolan.anistream.data.repository.AnimeRepository
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AnimeRepository(app)

    private val _sources = MutableLiveData<List<StreamSource>>()
    val sources: LiveData<List<StreamSource>> = _sources

    private val _skipTimes = MutableLiveData<SkipTimeResponse?>()
    val skipTimes: LiveData<SkipTimeResponse?> = _skipTimes

    private val _savedProgress = MutableLiveData<WatchHistory?>()
    val savedProgress: LiveData<WatchHistory?> = _savedProgress

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    var episodeId = ""; var animeId = ""; var animeTitle = ""
    var animeImage: String? = null; var episodeNumber = 1
    var subOrDub = "sub"; var malId = -1

    fun loadEpisode(
        epId: String, malId_: Int, epNum: Int,
        anId: String, anTitle: String, anImage: String?, sod: String
    ) {
        episodeId = epId; malId = malId_; episodeNumber = epNum
        animeId = anId; animeTitle = anTitle; animeImage = anImage; subOrDub = sod

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _savedProgress.value = repo.getEpisodeProgress(epId)
            try {
                val resp = repo.getStreamLinks(epId)
                val sorted = resp.sources?.sortedByDescending { it.qualityRank() } ?: emptyList()
                _sources.value = sorted
            } catch (e: Exception) {
                _error.value = "Could not load stream: ${e.message}"
            }
            _loading.value = false
            if (malId_ > 0) {
                try { _skipTimes.value = repo.getSkipTimes(malId_, epNum) }
                catch (_: Exception) { _skipTimes.value = null }
            }
        }
    }

    fun saveProgress(position: Long, duration: Long) = viewModelScope.launch {
        if (position < 1000 || duration < 1000) return@launch
        repo.saveProgress(WatchHistory(
            episodeId = episodeId, animeId = animeId, animeTitle = animeTitle,
            animeImage = animeImage, episodeNumber = episodeNumber,
            watchedPosition = position, totalDuration = duration, subOrDub = subOrDub
        ))
    }
}
