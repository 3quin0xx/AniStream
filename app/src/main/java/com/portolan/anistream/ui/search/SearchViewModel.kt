package com.portolan.anistream.ui.search

import android.app.Application
import androidx.lifecycle.*
import com.portolan.anistream.data.models.AniListAnime
import com.portolan.anistream.data.repository.AnimeRepository
import kotlinx.coroutines.*

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AnimeRepository(app)

    private val _results = MutableLiveData<List<AniListAnime>>(emptyList())
    val results: LiveData<List<AniListAnime>> = _results

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private var job: Job? = null

    fun search(q: String) {
        job?.cancel()
        if (q.length < 2) return
        job = viewModelScope.launch {
            delay(350)
            _loading.value = true
            try { _results.value = repo.search(q).results }
            catch (_: Exception) { _results.value = emptyList() }
            _loading.value = false
        }
    }
}
