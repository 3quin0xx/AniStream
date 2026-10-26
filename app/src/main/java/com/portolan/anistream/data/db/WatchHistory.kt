package com.portolan.anistream.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey val episodeId: String,
    val animeId: String,
    val animeTitle: String,
    val animeImage: String?,
    val episodeNumber: Int,
    val watchedPosition: Long,      // milliseconds
    val totalDuration: Long,        // milliseconds
    val timestamp: Long = System.currentTimeMillis(),
    val subOrDub: String = "sub"
) {
    fun progressPercent(): Int =
        if (totalDuration > 0) (watchedPosition * 100 / totalDuration).toInt() else 0

    fun isFinished(): Boolean = progressPercent() > 85
}
