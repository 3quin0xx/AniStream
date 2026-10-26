package com.portolan.anistream.data.api

import com.portolan.anistream.data.models.SkipTimeResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AniSkipApi {

    /**
     * Returns intro (op) and outro (ed) timestamps for a given episode.
     * skipType "op"  = opening song
     * skipType "ed"  = ending / credits
     * Times are in seconds (Double).
     */
    @GET("v2/skip-times/{malId}/{episode}")
    suspend fun getSkipTimes(
        @Path("malId") malId: Int,
        @Path("episode") episode: Int,
        @Query("types[]") types: List<String> = listOf("op", "ed"),
        @Query("episodeLength") episodeLength: Double = 0.0
    ): SkipTimeResponse
}
