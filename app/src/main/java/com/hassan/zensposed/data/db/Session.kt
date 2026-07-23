package com.hassan.zensposed.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "space_name") val spaceName: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    // Actual focused milliseconds completed (may be less than planned if exited early).
    @ColumnInfo(name = "focused_ms") val focusedMs: Long,
    @ColumnInfo(name = "planned_ms") val plannedMs: Long,
    @ColumnInfo(name = "completed") val completed: Boolean
)
