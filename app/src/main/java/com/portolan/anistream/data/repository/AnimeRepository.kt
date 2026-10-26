package com.portolan.anistream.data.repository

import android.content.Context
import com.portolan.anistream.data.api.RetrofitClient
import com.portolan.anistream.data.db.AppDatabase
import com.portolan.anistream.data.db.WatchHistory
import com.portolan.anistream.data.models.StreamResponse

class AnimeRepository(context: Context) {

    private val api = RetrofitClient.consumetApi
    private val skipApi = RetrofitClient.aniSkipApi
    private val db = AppDatabase.getInstance(context)

    // ── AniList ───────────────────────────────────────────────────────────
    suspend fun getTrending(page: Int = 1) = api.getTrending(page = page)
    suspend fun getPopular(page: Int = 1) = api.getPopular(page = page)
    suspend fun search(query: String, page: Int = 1) = api.searchAniList(query, page)
    suspend fun getAniListInfo(id: String) = api.getAniListInfo(id)

    // ── Gogoanime (dub) ───────────────────────────────────────────────────
    suspend fun getAnimeInfo(gogoId: String) = api.getAnimeInfo(gogoId)
    suspend fun getRecentEpisodes(page: Int = 1, type: Int = 1) =
        api.getRecentEpisodes(page, type)

    // ── Streaming — tries gogocdn first, falls back to streamsb ──────────
    suspend fun getStreamLinks(episodeId: String): StreamResponse =
        try { api.getStreamLinks(episodeId, "gogocdn") }
        catch (e: Exception) { api.getStreamLinks(episodeId, "streamsb") }

    // ── AniSkip ───────────────────────────────────────────────────────────
    suspend fun getSkipTimes(malId: Int, episode: Int) =
        try { skipApi.getSkipTimes(malId, episode) } catch (e: Exception) { null }

    // ── Watch history ─────────────────────────────────────────────────────
    fun getRecentHistory() = db.watchHistoryDao().getRecentHistory()

    suspend fun saveProgress(history: WatchHistory) =
        db.watchHistoryDao().insertHistory(history)

    suspend fun getEpisodeProgress(episodeId: String) =
        db.watchHistoryDao().getHistoryForEpisode(episodeId)

    suspend fun getLastWatched(animeId: String) =
        db.watchHistoryDao().getLastWatchedEpisode(animeId)

    suspend fun getAnimeWatchHistory(animeId: String) =
        db.watchHistoryDao().getHistoryForAnime(animeId)
}
