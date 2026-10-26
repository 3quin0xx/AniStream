package com.portolan.anistream.data.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface WatchHistoryDao {

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun getAllHistory(): LiveData<List<WatchHistory>>

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentHistory(): LiveData<List<WatchHistory>>

    @Query("SELECT * FROM watch_history WHERE episodeId = :episodeId")
    suspend fun getHistoryForEpisode(episodeId: String): WatchHistory?

    @Query("SELECT * FROM watch_history WHERE animeId = :animeId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastWatchedEpisode(animeId: String): WatchHistory?

    @Query("SELECT * FROM watch_history WHERE animeId = :animeId")
    suspend fun getHistoryForAnime(animeId: String): List<WatchHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: WatchHistory)

    @Query("DELETE FROM watch_history WHERE episodeId = :episodeId")
    suspend fun deleteHistory(episodeId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}
