package com.hassan.zensposed.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CategoryTotal(
    @androidx.room.ColumnInfo(name = "space_name") val spaceName: String,
    @androidx.room.ColumnInfo(name = "total_ms") val totalMs: Long
)

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions ORDER BY start_time DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT COALESCE(SUM(focused_ms), 0) FROM sessions WHERE start_time >= :from AND start_time < :to")
    fun observeFocusedMsBetween(from: Long, to: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM sessions WHERE start_time >= :from AND start_time < :to")
    fun observeSessionCountBetween(from: Long, to: Long): Flow<Int>

    @Query(
        "SELECT space_name AS space_name, COALESCE(SUM(focused_ms),0) AS total_ms FROM sessions " +
            "WHERE start_time >= :from AND start_time < :to GROUP BY space_name ORDER BY total_ms DESC"
    )
    fun observeCategoryTotalsBetween(from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM sessions WHERE start_time >= :from AND start_time < :to ORDER BY start_time DESC")
    fun observeSessionsBetween(from: Long, to: Long): Flow<List<SessionEntity>>
}
