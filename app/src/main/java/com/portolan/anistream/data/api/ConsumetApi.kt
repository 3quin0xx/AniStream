package com.portolan.anistream.data.api

import com.portolan.anistream.data.models.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ConsumetApi {

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

    @GET("meta/anilist/info/{id}")
    suspend fun getAniListInfo(
        @Path("id") id: String,
        @Query("provider") provider: String = "animepahe",
        @Query("dub") dub: Boolean = false
    ): AniListAnimeInfo

    @GET("meta/anilist/watch/{episodeId}")
    suspend fun getMetaStreamLinks(
        @Path(value = "episodeId", encoded = true) episodeId: String,
        @Query("provider") provider: String = "animepahe",
        @Query("dub") dub: Boolean = false
    ): StreamResponse

    @GET("anime/{provider}/watch/{episodeId}")
    suspend fun getProviderStreamPath(
        @Path("provider") provider: String,
        @Path(value = "episodeId", encoded = true) episodeId: String,
        @Query("server") server: String? = null
    ): StreamResponse

    @GET("anime/{provider}/watch")
    suspend fun getProviderStreamQuery(
        @Path("provider") provider: String,
        @Query("episodeId") episodeId: String,
        @Query("server") server: String? = null
    ): StreamResponse

    @GET("anime/gogoanime/info/{id}")
    suspend fun getAnimeInfo(
        @Path("id") id: String
    ): AnimeInfo

    @GET("anime/gogoanime/watch/{episodeId}")
    suspend fun getStreamLinks(
        @Path("episodeId") episodeId: String,
        @Query("server") server: String = "gogocdn"
    ): StreamResponse

    @GET("anime/gogoanime/recent-episodes")
    suspend fun getRecentEpisodes(
        @Query("page") page: Int = 1,
        @Query("type") type: Int = 1
    ): RecentEpisodesResult
}
