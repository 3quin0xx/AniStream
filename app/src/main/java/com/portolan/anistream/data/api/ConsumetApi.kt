package com.portolan.anistream.data.api

import com.portolan.anistream.data.models.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ConsumetApi {

    // ── AniList meta (trending, popular, search, detail) ──────────────────
    @GET("meta/anilist/trending")
    suspend fun getTrending(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 20
    ): AniListResult

    @GET("meta/anilist/popular")
    suspend fun getPopular(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = 20
    ): AniListResult

    @GET("meta/anilist/{query}")
    suspend fun searchAniList(
        @Path("query") query: String,
        @Query("page") page: Int = 1
    ): AniListResult

    /**
     * Returns AniList info PLUS episodes with gogoanime streaming IDs.
     * provider=gogoanime maps AniList → gogoanime for episode IDs.
     */
    @GET("meta/anilist/info/{id}")
    suspend fun getAniListInfo(
        @Path("id") id: String,
        @Query("provider") provider: String = "gogoanime"
    ): AniListAnimeInfo

    // ── Direct gogoanime (for dub episodes) ───────────────────────────────
    @GET("anime/gogoanime/info/{id}")
    suspend fun getAnimeInfo(
        @Path("id") id: String
    ): AnimeInfo

    // ── Stream links ──────────────────────────────────────────────────────
    @GET("anime/gogoanime/watch/{episodeId}")
    suspend fun getStreamLinks(
        @Path("episodeId") episodeId: String,
        @Query("server") server: String = "gogocdn"
    ): StreamResponse

    // ── Recent episodes ───────────────────────────────────────────────────
    @GET("anime/gogoanime/recent-episodes")
    suspend fun getRecentEpisodes(
        @Query("page") page: Int = 1,
        @Query("type") type: Int = 1    // 1=sub, 2=dub
    ): RecentEpisodesResult
}
