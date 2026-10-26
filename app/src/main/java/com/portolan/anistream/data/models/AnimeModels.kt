package com.portolan.anistream.data.models

import com.google.gson.annotations.SerializedName

data class AniListResult(
    @SerializedName("currentPage") val currentPage: Int = 1,
    @SerializedName("hasNextPage") val hasNextPage: Boolean = false,
    @SerializedName("results") val results: List<AniListAnime> = emptyList()
)

data class AniListAnime(
    @SerializedName("id") val id: String,
    @SerializedName("malId") val malId: Int?,
    @SerializedName("title") val title: AniListTitle?,
    @SerializedName("image") val image: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("rating") val rating: Int?,
    @SerializedName("releaseDate") val releaseDate: Int?,
    @SerializedName("type") val type: String?,
    @SerializedName("genres") val genres: List<String>?,
    @SerializedName("totalEpisodes") val totalEpisodes: Int?,
    @SerializedName("duration") val duration: Int?,
    @SerializedName("hasSub") val hasSub: Boolean?,
    @SerializedName("hasDub") val hasDub: Boolean?
)

data class AniListTitle(
    @SerializedName("romaji") val romaji: String?,
    @SerializedName("english") val english: String?,
    @SerializedName("native") val native_: String?,
    @SerializedName("userPreferred") val userPreferred: String?
) {
    fun display() = english ?: romaji ?: userPreferred ?: "Unknown"
}

data class AniListAnimeInfo(
    @SerializedName("id") val id: String,
    @SerializedName("malId") val malId: Int?,
    @SerializedName("title") val title: AniListTitle?,
    @SerializedName("image") val image: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("rating") val rating: Int?,
    @SerializedName("genres") val genres: List<String>?,
    @SerializedName("totalEpisodes") val totalEpisodes: Int?,
    @SerializedName("episodes") val episodes: List<AniListEpisode>?,
    @SerializedName("hasSub") val hasSub: Boolean?,
    @SerializedName("hasDub") val hasDub: Boolean?
)

data class AniListEpisode(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("number") val number: Int,
    @SerializedName("image") val image: String?,
    @SerializedName("airDate") val airDate: String?
)

data class AnimeInfo(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("image") val image: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("subOrDub") val subOrDub: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("totalEpisodes") val totalEpisodes: Int?,
    @SerializedName("episodes") val episodes: List<EpisodeInfo>?
)

data class EpisodeInfo(
    @SerializedName("id") val id: String,
    @SerializedName("number") val number: Int,
    @SerializedName("url") val url: String?
)

data class StreamResponse(
    @SerializedName("headers") val headers: Map<String, String>?,
    @SerializedName("sources") val sources: List<StreamSource>?,
    @SerializedName("download") val download: String?
)

data class StreamSource(
    @SerializedName("url") val url: String,
    @SerializedName("isM3U8") val isM3U8: Boolean = true,
    @SerializedName("quality") val quality: String?,
    val referer: String? = null,
    val provider: String? = null
) {
    fun normalizedQuality(): String {
        val q = quality?.lowercase()?.trim() ?: ""
        return when {
            q.contains("1080") || q.contains("fhd") -> "1080p"
            q.contains("720") || q.contains("hd") -> "720p"
            q.contains("default") || q.contains("auto") || q.contains("master") || q.isEmpty() -> "Auto"
            q.contains("480") -> "480p"
            q.contains("360") -> "360p"
            else -> quality ?: "Auto"
        }
    }

    /** Prioritizes 1080p first, then 720p, then Auto */
    fun qualityRank(): Int = when (normalizedQuality()) {
        "1080p" -> 1080
        "720p"  -> 720
        "Auto"  -> 500
        "480p"  -> 480
        "360p"  -> 360
        else    -> 100
    }
}

data class RecentEpisodesResult(
    @SerializedName("currentPage") val currentPage: Int = 1,
    @SerializedName("hasNextPage") val hasNextPage: Boolean = false,
    @SerializedName("results") val results: List<RecentEpisode> = emptyList()
)

data class RecentEpisode(
    @SerializedName("id") val id: String,
    @SerializedName("episodeId") val episodeId: String?,
    @SerializedName("episodeNumber") val episodeNumber: Int?,
    @SerializedName("title") val title: String,
    @SerializedName("image") val image: String?,
    @SerializedName("url") val url: String?
)

data class SkipTimeResponse(
    @SerializedName("found") val found: Boolean,
    @SerializedName("results") val results: List<SkipTimeResult>?,
    @SerializedName("statusCode") val statusCode: Int
)

data class SkipTimeResult(
    @SerializedName("interval") val interval: SkipInterval,
    @SerializedName("skipType") val skipType: String,
    @SerializedName("skipId") val skipId: String,
    @SerializedName("episodeLength") val episodeLength: Double
)

data class SkipInterval(
    @SerializedName("startTime") val startTime: Double,
    @SerializedName("endTime") val endTime: Double
)

data class EpisodeItem(
    val id: String,
    val number: Int,
    val title: String?,
    val image: String?,
    val provider: String = "animepahe"
)
