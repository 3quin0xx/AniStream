package com.portolan.anistream.data.repository

import android.content.Context
import com.portolan.anistream.data.api.RetrofitClient
import com.portolan.anistream.data.db.AppDatabase
import com.portolan.anistream.data.db.WatchHistory
import com.portolan.anistream.data.models.AniListAnimeInfo
import com.portolan.anistream.data.models.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URI

class AnimeRepository(context: Context) {

    private val api = RetrofitClient.consumetApi
    private val skipApi = RetrofitClient.aniSkipApi
    private val http = RetrofitClient.httpClient
    private val db = AppDatabase.getInstance(context)

    companion object {
        // Providers powering AnimePahe, Miruro/Yomi (Zoro/HiAnime), AniWave (9anime), and Gogoanime
        val PROVIDERS = listOf("animepahe", "zoro", "9anime", "gogoanime")

        fun defaultRefererFor(provider: String): String = when (provider) {
            "animepahe" -> "https://kwik.cx/"
            "zoro"      -> "https://megacloud.tv/"
            "9anime"    -> "https://aniwaves.ru/"
            else        -> "https://gogoanime.tel/"
        }
    }

    suspend fun getTrending(page: Int = 1) = api.getTrending(page = page)
    suspend fun getPopular(page: Int = 1) = api.getPopular(page = page)
    suspend fun search(query: String, page: Int = 1) = api.searchAniList(query, page)
    suspend fun getAnimeInfo(gogoId: String) = api.getAnimeInfo(gogoId)
    suspend fun getRecentEpisodes(page: Int = 1, type: Int = 1) = api.getRecentEpisodes(page, type)

    /**
     * Loads AniList info + episodes for the preferred provider, falling back automatically
     * across AnimePahe, Zoro (Miruro/Yomi), AniWave (9anime), and Gogoanime if empty.
     */
    suspend fun getAniListInfoWithProvider(
        id: String,
        preferredProvider: String = "animepahe",
        isDub: Boolean = false
    ): Pair<AniListAnimeInfo, String> {
        val ordered = listOf(preferredProvider) + PROVIDERS.filter { it != preferredProvider }
        var lastError: Exception? = null
        var fallbackInfo: AniListAnimeInfo? = null

        for (prov in ordered) {
            try {
                val info = api.getAniListInfo(id, prov, isDub)
                if (!info.episodes.isNullOrEmpty()) {
                    return Pair(info, prov)
                }
                if (fallbackInfo == null) fallbackInfo = info
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (fallbackInfo != null) return Pair(fallbackInfo, preferredProvider)
        throw (lastError ?: Exception("All providers failed"))
    }

    /**
     * Fetches streams from the selected provider (or falls back across all providers)
     * and expands any master M3U8 playlist into explicit 1080p, 720p, and Auto sources.
     */
    suspend fun getHighDefStreams(
        episodeId: String,
        preferredProvider: String = "animepahe",
        isDub: Boolean = false
    ): List<StreamSource> = withContext(Dispatchers.IO) {
        val ordered = listOf(preferredProvider) + PROVIDERS.filter { it != preferredProvider }
        val collected = mutableListOf<StreamSource>()

        for (prov in ordered) {
            val resp = tryFetchFromProvider(episodeId, prov, isDub)
            val rawSources = resp?.sources
            if (!rawSources.isNullOrEmpty()) {
                val ref = resp.headers?.get("Referer")
                    ?: resp.headers?.get("referer")
                    ?: defaultRefererFor(prov)

                for (src in rawSources) {
                    val norm = src.normalizedQuality()
                    val tagged = src.copy(
                        quality = norm,
                        referer = ref,
                        provider = prov
                    )
                    collected.add(tagged)

                    // If this is a Master M3U8 ("Auto"), parse it to extract direct 1080p & 720p links
                    if (src.isM3U8 && (norm == "Auto" || rawSources.size == 1)) {
                        collected.addAll(expandMasterM3u8(src.url, ref, prov))
                    }
                }
                // Stop searching once we have at least one 1080p or valid stream set
                if (collected.any { it.normalizedQuality() == "1080p" }) break
            }
        }

        // Keep only 1080p, 720p, and Auto when available, sorted with 1080p first
        val filtered = collected
            .distinctBy { "${it.normalizedQuality()}_${it.url}" }
            .sortedByDescending { it.qualityRank() }

        val topTier = filtered.filter {
            it.normalizedQuality() in listOf("1080p", "720p", "Auto")
        }
        topTier.ifEmpty { filtered }
    }

    private suspend fun tryFetchFromProvider(
        episodeId: String,
        provider: String,
        isDub: Boolean
    ) = try {
        api.getMetaStreamLinks(episodeId, provider, isDub)
    } catch (_: Exception) {
        try {
            api.getProviderStreamPath(provider, episodeId)
        } catch (_: Exception) {
            try {
                api.getProviderStreamQuery(provider, episodeId)
            } catch (_: Exception) {
                try {
                    api.getStreamLinks(episodeId, "gogocdn")
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Downloads a master HLS .m3u8 manifest and extracts 1080p and 720p variant streams.
     */
    private fun expandMasterM3u8(
        masterUrl: String,
        referer: String,
        provider: String
    ): List<StreamSource> {
        val extracted = mutableListOf<StreamSource>()
        try {
            val req = Request.Builder()
                .url(masterUrl)
                .header("Referer", referer)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val lines = body.lines()
                val baseUri = URI(masterUrl)

                var pendingQuality: String? = null
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                        pendingQuality = when {
                            trimmed.contains("1920x1080") || trimmed.contains("1080") -> "1080p"
                            trimmed.contains("1280x720")  || trimmed.contains("720")  -> "720p"
                            trimmed.contains("854x480")   || trimmed.contains("480")  -> "480p"
                            else -> null
                        }
                    } else if (pendingQuality != null && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val resolvedUrl = if (trimmed.startsWith("http")) {
                            trimmed
                        } else {
                            baseUri.resolve(trimmed).toString()
                        }
                        extracted.add(
                            StreamSource(
                                url = resolvedUrl,
                                isM3U8 = true,
                                quality = pendingQuality,
                                referer = referer,
                                provider = provider
                            )
                        )
                        pendingQuality = null
                    }
                }
            }
        } catch (_: Exception) {}
        return extracted
    }

    suspend fun getSkipTimes(malId: Int, episode: Int) =
        try { skipApi.getSkipTimes(malId, episode) } catch (_: Exception) { null }

    fun getRecentHistory() = db.watchHistoryDao().getRecentHistory()
    suspend fun saveProgress(history: WatchHistory) = db.watchHistoryDao().insertHistory(history)
    suspend fun getEpisodeProgress(episodeId: String) = db.watchHistoryDao().getHistoryForEpisode(episodeId)
    suspend fun getLastWatched(animeId: String) = db.watchHistoryDao().getLastWatchedEpisode(animeId)
    suspend fun getAnimeWatchHistory(animeId: String) = db.watchHistoryDao().getHistoryForAnime(animeId)
}
