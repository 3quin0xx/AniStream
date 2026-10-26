package com.portolan.anistream.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.portolan.anistream.data.api.RetrofitClient
import com.portolan.anistream.data.db.AppDatabase
import com.portolan.anistream.data.db.WatchHistory
import com.portolan.anistream.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI

class AnimeRepository(context: Context) {

    private val skipApi = RetrofitClient.aniSkipApi
    private val http = RetrofitClient.httpClient
    private val db = AppDatabase.getInstance(context)
    private val gson = Gson()

    companion object {
        val PROVIDERS = listOf("animepahe", "zoro", "9anime", "gogoanime")

        fun defaultRefererFor(provider: String): String = when (provider) {
            "animepahe" -> "https://kwik.cx/"
            "zoro"      -> "https://megacloud.tv/"
            "9anime"    -> "https://aniwaves.ru/"
            else        -> "https://gogoanime.tel/"
        }

        private const val PAGE_MEDIA_QUERY = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [MediaSort], ${'$'}search: String, ${'$'}status: MediaStatus) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { currentPage hasNextPage }
                media(type: ANIME, isAdult: false, sort: ${'$'}sort, search: ${'$'}search, status: ${'$'}status) {
                  id
                  idMal
                  title { romaji english native userPreferred }
                  coverImage { extraLarge large }
                  bannerImage
                  description(asHtml: false)
                  status
                  averageScore
                  seasonYear
                  format
                  genres
                  episodes
                  duration
                  nextAiringEpisode { episode }
                }
              }
            }
        """

        private const val DETAILS_MEDIA_QUERY = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                id
                idMal
                title { romaji english native userPreferred }
                coverImage { extraLarge large }
                bannerImage
                description(asHtml: false)
                status
                averageScore
                seasonYear
                format
                genres
                episodes
                duration
                nextAiringEpisode { episode }
                streamingEpisodes { title thumbnail url }
              }
            }
        """
    }

    // ── Direct Official AniList GraphQL Queries (Trending, Popular, Recent, Search) ──

    suspend fun getTrending(page: Int = 1): AniListResult =
        queryAniListPage(page = page, sort = listOf("TRENDING_DESC", "POPULARITY_DESC"))

    suspend fun getPopular(page: Int = 1): AniListResult =
        queryAniListPage(page = page, sort = listOf("POPULARITY_DESC"))

    suspend fun getRecentAnime(page: Int = 1): AniListResult =
        queryAniListPage(page = page, sort = listOf("UPDATED_AT_DESC", "POPULARITY_DESC"), status = "RELEASING")

    suspend fun search(query: String, page: Int = 1): AniListResult =
        queryAniListPage(page = page, sort = listOf("SEARCH_MATCH", "POPULARITY_DESC"), search = query)

    private suspend fun queryAniListPage(
        page: Int,
        perPage: Int = 20,
        sort: List<String>,
        search: String? = null,
        status: String? = null
    ): AniListResult = withContext(Dispatchers.IO) {
        val variables = mutableMapOf<String, Any>(
            "page" to page,
            "perPage" to perPage,
            "sort" to sort
        )
        if (!search.isNullOrBlank()) variables["search"] = search
        if (!status.isNullOrBlank()) variables["status"] = status

        val payload = gson.toJson(mapOf("query" to PAGE_MEDIA_QUERY, "variables" to variables))
        val req = Request.Builder()
            .url(RetrofitClient.ANILIST_GRAPHQL_URL)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return@withContext AniListResult()
            if (!resp.isSuccessful) return@withContext AniListResult()

            val root = JsonParser.parseString(body).asJsonObject
            val pageObj = root.getAsJsonObject("data")?.getAsJsonObject("Page")
                ?: return@withContext AniListResult()

            val pageInfo = pageObj.getAsJsonObject("pageInfo")
            val currentPage = pageInfo?.get("currentPage")?.asIntOrNull() ?: page
            val hasNextPage = pageInfo?.get("hasNextPage")?.asBooleanOrNull() ?: false

            val mediaArr = pageObj.getAsJsonArray("media")
            val results = mutableListOf<AniListAnime>()
            if (mediaArr != null) {
                for (el in mediaArr) {
                    if (el.isJsonObject) {
                        results.add(parseAniListAnime(el.asJsonObject))
                    }
                }
            }
            AniListResult(currentPage = currentPage, hasNextPage = hasNextPage, results = results)
        }
    }

    private suspend fun queryAniListMediaById(id: String): AniListAnimeInfo? = withContext(Dispatchers.IO) {
        val numericId = id.toIntOrNull() ?: return@withContext null
        val payload = gson.toJson(
            mapOf(
                "query" to DETAILS_MEDIA_QUERY,
                "variables" to mapOf("id" to numericId)
            )
        )
        val req = Request.Builder()
            .url(RetrofitClient.ANILIST_GRAPHQL_URL)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return@withContext null
            if (!resp.isSuccessful) return@withContext null
            val mediaObj = JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("data")
                ?.getAsJsonObject("Media") ?: return@withContext null

            val base = parseAniListAnime(mediaObj)
            val totalEps = (base.totalEpisodes ?: 12).coerceAtLeast(1)
            val slug = (base.title?.romaji ?: base.title?.english ?: "anime")
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')

            val fallbackEpisodes = (1..totalEps).map { num ->
                AniListEpisode(
                    id = "$slug-episode-$num",
                    title = "Episode $num",
                    description = null,
                    number = num,
                    image = base.image,
                    airDate = null
                )
            }

            AniListAnimeInfo(
                id = base.id,
                malId = base.malId,
                title = base.title,
                image = base.image,
                cover = base.cover,
                description = base.description,
                status = base.status,
                rating = base.rating,
                genres = base.genres,
                totalEpisodes = totalEps,
                episodes = fallbackEpisodes,
                hasSub = true,
                hasDub = true
            )
        }
    }

    private fun parseAniListAnime(obj: JsonObject): AniListAnime {
        val titleObj = obj.getAsJsonObject("title")
        val coverObj = obj.getAsJsonObject("coverImage")
        val nextAiringObj = obj.getAsJsonObject("nextAiringEpisode")

        val nextEp = nextAiringObj?.get("episode")?.asIntOrNull()
        val totalEps = obj.get("episodes")?.asIntOrNull()
            ?: (if (nextEp != null && nextEp > 1) nextEp - 1 else null)

        val image = coverObj?.get("extraLarge")?.asStringOrNull()
            ?: coverObj?.get("large")?.asStringOrNull()
        val banner = obj.get("bannerImage")?.asStringOrNull() ?: image

        val genres = obj.getAsJsonArray("genres")?.mapNotNull { it.asStringOrNull() } ?: emptyList()

        return AniListAnime(
            id = obj.get("id")?.asIntOrNull()?.toString() ?: "",
            malId = obj.get("idMal")?.asIntOrNull(),
            title = AniListTitle(
                romaji = titleObj?.get("romaji")?.asStringOrNull(),
                english = titleObj?.get("english")?.asStringOrNull(),
                native_ = titleObj?.get("native")?.asStringOrNull(),
                userPreferred = titleObj?.get("userPreferred")?.asStringOrNull()
            ),
            image = image,
            cover = banner,
            description = obj.get("description")?.asStringOrNull(),
            status = obj.get("status")?.asStringOrNull(),
            rating = obj.get("averageScore")?.asIntOrNull(),
            releaseDate = obj.get("seasonYear")?.asIntOrNull(),
            type = obj.get("format")?.asStringOrNull(),
            genres = genres,
            totalEpisodes = totalEps,
            duration = obj.get("duration")?.asIntOrNull(),
            hasSub = true,
            hasDub = true
        )
    }

    private fun JsonElement?.asStringOrNull(): String? =
        if (this != null && !this.isJsonNull) runCatching { this.asString }.getOrNull() else null

    private fun JsonElement?.asIntOrNull(): Int? =
        if (this != null && !this.isJsonNull) runCatching { this.asInt }.getOrNull() else null

    private fun JsonElement?.asBooleanOrNull(): Boolean? =
        if (this != null && !this.isJsonNull) runCatching { this.asBoolean }.getOrNull() else null

    // ── Detail Info + Provider Episode Mapping ───────────────────────────────

    suspend fun getAniListInfoWithProvider(
        id: String,
        preferredProvider: String = "animepahe",
        isDub: Boolean = false
    ): Pair<AniListAnimeInfo, String> = withContext(Dispatchers.IO) {
        val baseInfo = runCatching { queryAniListMediaById(id) }.getOrNull()
        val orderedProviders = listOf(preferredProvider) + PROVIDERS.filter { it != preferredProvider }

        for (mirrorUrl in RetrofitClient.CONSUMET_MIRRORS) {
            val mirrorApi = RetrofitClient.createConsumetApi(mirrorUrl)
            for (prov in orderedProviders) {
                try {
                    val remoteInfo = mirrorApi.getAniListInfo(id, prov, isDub)
                    if (!remoteInfo.episodes.isNullOrEmpty()) {
                        val merged = if (baseInfo != null) {
                            baseInfo.copy(episodes = remoteInfo.episodes)
                        } else {
                            remoteInfo
                        }
                        return@withContext Pair(merged, prov)
                    }
                } catch (_: Exception) {}
            }
        }

        if (baseInfo != null) {
            return@withContext Pair(baseInfo, preferredProvider)
        }
        throw Exception("Could not load anime details. Check your internet connection.")
    }

    // ── High-Def 1080p / 720p / Auto Stream Resolution across Mirrors ────────

    suspend fun getHighDefStreams(
        episodeId: String,
        preferredProvider: String = "animepahe",
        isDub: Boolean = false
    ): List<StreamSource> = withContext(Dispatchers.IO) {
        val orderedProviders = listOf(preferredProvider) + PROVIDERS.filter { it != preferredProvider }
        val collected = mutableListOf<StreamSource>()

        for (mirrorUrl in RetrofitClient.CONSUMET_MIRRORS) {
            val mirrorApi = RetrofitClient.createConsumetApi(mirrorUrl)
            for (prov in orderedProviders) {
                val resp = tryFetchFromProvider(mirrorApi, episodeId, prov, isDub)
                val rawSources = resp?.sources
                if (!rawSources.isNullOrEmpty()) {
                    val ref = resp.headers?.get("Referer")
                        ?: resp.headers?.get("referer")
                        ?: defaultRefererFor(prov)

                    for (src in rawSources) {
                        val norm = src.normalizedQuality()
                        val tagged = src.copy(quality = norm, referer = ref, provider = prov)
                        collected.add(tagged)

                        if (src.isM3U8 && (norm == "Auto" || rawSources.size == 1)) {
                            collected.addAll(expandMasterM3u8(src.url, ref, prov))
                        }
                    }
                    if (collected.any { it.normalizedQuality() == "1080p" }) break
                }
            }
            if (collected.isNotEmpty()) break
        }

        val filtered = collected
            .distinctBy { "${it.normalizedQuality()}_${it.url}" }
            .sortedByDescending { it.qualityRank() }

        val topTier = filtered.filter { it.normalizedQuality() in listOf("1080p", "720p", "Auto") }
        topTier.ifEmpty { filtered }
    }

    private suspend fun tryFetchFromProvider(
        mirrorApi: com.portolan.anistream.data.api.ConsumetApi,
        episodeId: String,
        provider: String,
        isDub: Boolean
    ) = try {
        mirrorApi.getMetaStreamLinks(episodeId, provider, isDub)
    } catch (_: Exception) {
        try {
            mirrorApi.getProviderStreamPath(provider, episodeId)
        } catch (_: Exception) {
            try {
                mirrorApi.getProviderStreamQuery(provider, episodeId)
            } catch (_: Exception) {
                try {
                    mirrorApi.getStreamLinks(episodeId, "gogocdn")
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

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
                val baseUri = URI(masterUrl)

                var pendingQuality: String? = null
                for (line in body.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                        pendingQuality = when {
                            trimmed.contains("1920x1080") || trimmed.contains("1080") -> "1080p"
                            trimmed.contains("1280x720")  || trimmed.contains("720")  -> "720p"
                            trimmed.contains("854x480")   || trimmed.contains("480")  -> "480p"
                            else -> null
                        }
                    } else if (pendingQuality != null && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val resolvedUrl = if (trimmed.startsWith("http")) trimmed else baseUri.resolve(trimmed).toString()
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
