package com.portolan.anistream.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
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
import java.net.URLEncoder

class AnimeRepository(context: Context) {

    private val skipApi = RetrofitClient.aniSkipApi
    private val http = RetrofitClient.httpClient
    private val db = AppDatabase.getInstance(context)
    private val gson = Gson()

    companion object {
        val PROVIDERS = listOf("allanime", "animepahe", "zoro", "9anime", "gogoanime")

        fun defaultRefererFor(provider: String): String = when (provider) {
            "allanime"  -> "https://allmanga.to/"
            "animepahe" -> "https://kwik.cx/"
            "zoro"      -> "https://megacloud.tv/"
            "9anime"    -> "https://aniwaves.ru/"
            else        -> "https://gogoanime.tel/"
        }
    }

    suspend fun getTrending(page: Int = 1): AniListResult = withContext(Dispatchers.IO) {
        val anilist = runCatching { queryAniListInline(page = page, sort = "TRENDING_DESC, POPULARITY_DESC") }.getOrNull()
        if (anilist != null && anilist.results.isNotEmpty()) return@withContext anilist
        queryJikanFallback("https://api.jikan.moe/v4/top/anime?filter=airing&page=$page")
    }

    suspend fun getPopular(page: Int = 1): AniListResult = withContext(Dispatchers.IO) {
        val anilist = runCatching { queryAniListInline(page = page, sort = "POPULARITY_DESC") }.getOrNull()
        if (anilist != null && anilist.results.isNotEmpty()) return@withContext anilist
        queryJikanFallback("https://api.jikan.moe/v4/top/anime?filter=bypopularity&page=$page")
    }

    suspend fun getRecentAnime(page: Int = 1): AniListResult = withContext(Dispatchers.IO) {
        val anilist = runCatching { queryAniListInline(page = page, sort = "UPDATED_AT_DESC, POPULARITY_DESC", status = "RELEASING") }.getOrNull()
        if (anilist != null && anilist.results.isNotEmpty()) return@withContext anilist
        queryJikanFallback("https://api.jikan.moe/v4/seasons/now?page=$page")
    }

    suspend fun search(query: String, page: Int = 1): AniListResult = withContext(Dispatchers.IO) {
        val anilist = runCatching { queryAniListInline(page = page, sort = "SEARCH_MATCH, POPULARITY_DESC", search = query) }.getOrNull()
        if (anilist != null && anilist.results.isNotEmpty()) return@withContext anilist
        val encoded = URLEncoder.encode(query, "UTF-8")
        queryJikanFallback("https://api.jikan.moe/v4/anime?q=$encoded&sfw=true&page=$page")
    }

    private fun queryAniListInline(page: Int, perPage: Int = 20, sort: String, search: String? = null, status: String? = null): AniListResult {
        val args = mutableListOf("type: ANIME", "isAdult: false", "sort: [$sort]")
        if (!search.isNullOrBlank()) args.add("search: \"${search.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        if (!status.isNullOrBlank()) args.add("status: $status")

        val queryStr = "query { Page(page: $page, perPage: $perPage) { pageInfo { currentPage hasNextPage } media(${args.joinToString(", ")}) { id idMal title { romaji english native userPreferred } coverImage { extraLarge large } bannerImage description(asHtml: false) status averageScore seasonYear format genres episodes duration nextAiringEpisode { episode } } } }"
        val bodyJson = JsonObject().apply { addProperty("query", queryStr) }
        val req = Request.Builder().url(RetrofitClient.ANILIST_GRAPHQL_URL).post(gson.toJson(bodyJson).toRequestBody("application/json; charset=utf-8".toMediaType())).header("Accept", "application/json").build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return AniListResult()
            if (!resp.isSuccessful) return AniListResult()
            val root = gson.fromJson(body, JsonObject::class.java) ?: return AniListResult()
            val mediaArr = root.getAsJsonObject("data")?.getAsJsonObject("Page")?.getAsJsonArray("media") ?: return AniListResult()
            val results = mediaArr.mapNotNull { if (it.isJsonObject) parseAniListAnime(it.asJsonObject) else null }
            return AniListResult(currentPage = page, hasNextPage = true, results = results)
        }
    }

    private fun queryJikanFallback(url: String): AniListResult {
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return AniListResult()
            if (!resp.isSuccessful) return AniListResult()
            val dataArr = gson.fromJson(body, JsonObject::class.java)?.getAsJsonArray("data") ?: return AniListResult()
            val results = dataArr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val obj = el.asJsonObject
                val malId = obj.get("mal_id")?.asIntOrNull() ?: return@mapNotNull null
                val titleEn = obj.get("title_english")?.asStringOrNull()
                val titleDef = obj.get("title")?.asStringOrNull()
                val image = obj.getAsJsonObject("images")?.getAsJsonObject("jpg")?.get("large_image_url")?.asStringOrNull()
                AniListAnime(
                    id = malId.toString(), malId = malId,
                    title = AniListTitle(titleDef, titleEn ?: titleDef, null, titleEn ?: titleDef),
                    image = image, cover = image, description = obj.get("synopsis")?.asStringOrNull(),
                    status = obj.get("status")?.asStringOrNull(), rating = obj.get("score")?.asDoubleOrNull()?.let { (it * 10).toInt() },
                    releaseDate = obj.get("year")?.asIntOrNull(), type = obj.get("type")?.asStringOrNull(),
                    genres = emptyList(), totalEpisodes = obj.get("episodes")?.asIntOrNull() ?: 12,
                    duration = null, hasSub = true, hasDub = true
                )
            }
            return AniListResult(currentPage = 1, hasNextPage = false, results = results)
        }
    }
    private fun queryMetadataById(id: String): AniListAnime? {
        val numericId = id.toIntOrNull() ?: return null
        val queryStr = "query { Media(id: $numericId, type: ANIME) { id idMal title { romaji english native userPreferred } coverImage { extraLarge large } bannerImage description(asHtml: false) status averageScore seasonYear format genres episodes duration nextAiringEpisode { episode } } }"
        val bodyJson = JsonObject().apply { addProperty("query", queryStr) }
        val req = Request.Builder().url(RetrofitClient.ANILIST_GRAPHQL_URL).post(gson.toJson(bodyJson).toRequestBody("application/json; charset=utf-8".toMediaType())).header("Accept", "application/json").build()

        runCatching {
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string()
                if (resp.isSuccessful && body != null) {
                    val mediaObj = gson.fromJson(body, JsonObject::class.java)?.getAsJsonObject("data")?.getAsJsonObject("Media")
                    if (mediaObj != null) return parseAniListAnime(mediaObj)
                }
            }
        }
        return null
    }

    suspend fun getAniListInfoWithProvider(id: String, preferredProvider: String = "allanime", isDub: Boolean = false): Pair<AniListAnimeInfo, String> = withContext(Dispatchers.IO) {
        val base = queryMetadataById(id)
        val searchTitle = base?.title?.english ?: base?.title?.romaji ?: "Anime"
        val allAnimeEps = runCatching { searchAllAnimeEpisodes(searchTitle, isDub, base?.image) }.getOrNull()

        if (!allAnimeEps.isNullOrEmpty()) {
            return@withContext Pair(buildAnimeInfo(base, id, allAnimeEps), "allanime")
        }
        val total = (base?.totalEpisodes ?: 12).coerceAtLeast(1)
        val fallbackEps = (1..total).map { AniListEpisode("allanime|$searchTitle|$it", "Episode $it", null, it, base?.image, null) }
        Pair(buildAnimeInfo(base, id, fallbackEps), preferredProvider)
    }

    private fun buildAnimeInfo(base: AniListAnime?, fallbackId: String, episodes: List<AniListEpisode>) = AniListAnimeInfo(
        id = base?.id ?: fallbackId, malId = base?.malId, title = base?.title ?: AniListTitle("Anime", "Anime", null, "Anime"),
        image = base?.image, cover = base?.cover ?: base?.image, description = base?.description ?: "",
        status = base?.status ?: "RELEASING", rating = base?.rating, genres = base?.genres ?: emptyList(),
        totalEpisodes = episodes.size, episodes = episodes, hasSub = true, hasDub = true
    )

    private fun searchAllAnimeEpisodes(title: String, isDub: Boolean, thumb: String?): List<AniListEpisode> {
        val mode = if (isDub) "dub" else "sub"
        val gql = "query { shows(search: { query: \"${title.replace("\"", "\\\"")}\", allowAdult: false }, limit: 1, page: 1, translationType: $mode) { edges { _id availableEpisodesDetail } } }"
        val req = Request.Builder().url("https://api.allanime.day/api?query=" + URLEncoder.encode(gql, "UTF-8")).header("Referer", "https://allmanga.to").build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return emptyList()
            if (!resp.isSuccessful) return emptyList()
            val edges = gson.fromJson(body, JsonObject::class.java)?.getAsJsonObject("data")?.getAsJsonObject("shows")?.getAsJsonArray("edges") ?: return emptyList()
            if (edges.size() == 0) return emptyList()
            val showId = edges[0].asJsonObject.get("_id")?.asStringOrNull() ?: return emptyList()
            val epArr = edges[0].asJsonObject.getAsJsonObject("availableEpisodesDetail")?.getAsJsonArray(mode) ?: return emptyList()
            return epArr.mapNotNull { it.asStringOrNull() }.sortedBy { it.toDoubleOrNull() ?: 0.0 }.mapIndexed { idx, epStr ->
                AniListEpisode("allanimeid|$showId|$epStr", "Episode $epStr", null, epStr.toIntOrNull() ?: (idx + 1), thumb, null)
            }
        }
    }

    suspend fun getHighDefStreams(episodeId: String, preferredProvider: String = "allanime", isDub: Boolean = false): List<StreamSource> = withContext(Dispatchers.IO) {
        val collected = mutableListOf<StreamSource>()
        if (episodeId.startsWith("allanime")) {
            val parts = episodeId.split("|")
            if (parts.size >= 3) {
                val gql = "query { episode(showId: \"${parts[1]}\", translationType: ${if(isDub)"dub" else "sub"}, episodeString: \"${parts[2]}\") { sourceUrls } }"
                val req = Request.Builder().url("https://api.allanime.day/api?query=" + URLEncoder.encode(gql, "UTF-8")).header("Referer", "https://allmanga.to").build()
                runCatching {
                    http.newCall(req).execute().use { resp ->
                        val body = resp.body?.string()
                        if (resp.isSuccessful && body != null) {
                            val urls = gson.fromJson(body, JsonObject::class.java)?.getAsJsonObject("data")?.getAsJsonObject("episode")?.getAsJsonArray("sourceUrls")
                            urls?.forEach { el ->
                                val raw = el.asJsonObject?.get("sourceUrl")?.asStringOrNull() ?: return@forEach
                                val decoded = if (raw.startsWith("--")) {
                                    val sb = StringBuilder()
                                    var i = 0
                                    val hex = raw.removePrefix("--")
                                    while (i + 1 < hex.length) {
                                        hex.substring(i, i + 2).toIntOrNull(16)?.let { sb.append((it xor 56).toChar()) }
                                        i += 2
                                    }
                                    sb.toString()
                                } else raw
                                if (decoded.startsWith("http") && (decoded.contains(".m3u8") || decoded.contains(".mp4"))) {
                                    collected.add(StreamSource(decoded, decoded.contains(".m3u8"), "1080p", "https://allmanga.to/", "allanime"))
                                }
                            }
                        }
                    }
                }
            }
        }
        collected.distinctBy { it.url }.sortedByDescending { it.qualityRank() }
    }

    private fun parseAniListAnime(obj: JsonObject): AniListAnime {
        val titleObj = obj.getAsJsonObject("title")
        val coverObj = obj.getAsJsonObject("coverImage")
        val image = coverObj?.get("extraLarge")?.asStringOrNull() ?: coverObj?.get("large")?.asStringOrNull()
        return AniListAnime(
            id = obj.get("id")?.asIntOrNull()?.toString() ?: "", malId = obj.get("idMal")?.asIntOrNull(),
            title = AniListTitle(titleObj?.get("romaji")?.asStringOrNull(), titleObj?.get("english")?.asStringOrNull(), null, titleObj?.get("userPreferred")?.asStringOrNull()),
            image = image, cover = image, description = obj.get("description")?.asStringOrNull(),
            status = obj.get("status")?.asStringOrNull(), rating = obj.get("averageScore")?.asIntOrNull(),
            releaseDate = obj.get("seasonYear")?.asIntOrNull(), type = obj.get("format")?.asStringOrNull(),
            genres = obj.getAsJsonArray("genres")?.mapNotNull { it.asStringOrNull() } ?: emptyList(),
            totalEpisodes = obj.get("episodes")?.asIntOrNull(), duration = obj.get("duration")?.asIntOrNull(),
            hasSub = true, hasDub = true
        )
    }

    private fun JsonElement?.asStringOrNull() = if (this != null && !this.isJsonNull) runCatching { this.asString }.getOrNull() else null
    private fun JsonElement?.asIntOrNull() = if (this != null && !this.isJsonNull) runCatching { this.asInt }.getOrNull() else null
    private fun JsonElement?.asDoubleOrNull() = if (this != null && !this.isJsonNull) runCatching { this.asDouble }.getOrNull() else null

    suspend fun getSkipTimes(malId: Int, episode: Int) = try { skipApi.getSkipTimes(malId, episode) } catch (_: Exception) { null }
    fun getRecentHistory() = db.watchHistoryDao().getRecentHistory()
    suspend fun saveProgress(history: WatchHistory) = db.watchHistoryDao().insertHistory(history)
    suspend fun getEpisodeProgress(epId: String) = db.watchHistoryDao().getHistoryForEpisode(epId)
    suspend fun getLastWatched(animeId: String) = db.watchHistoryDao().getLastWatchedEpisode(animeId)
    suspend fun getAnimeWatchHistory(animeId: String) = db.watchHistoryDao().getHistoryForAnime(animeId)
}
