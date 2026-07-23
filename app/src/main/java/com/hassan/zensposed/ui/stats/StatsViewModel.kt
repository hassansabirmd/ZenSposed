package com.hassan.zensposed.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hassan.zensposed.ZenSposedApp
import com.hassan.zensposed.data.db.CategoryTotal
import com.hassan.zensposed.data.db.SessionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class StatsUiState(
    val dayFocusedMs: Long = 0L,
    val daySessions: Int = 0,
    val weekFocusedMs: Long = 0L,
    val weekSessions: Int = 0,
    val monthFocusedMs: Long = 0L,
    val monthSessions: Int = 0,
    val monthCategories: List<CategoryTotal> = emptyList(),
    val history: List<SessionEntity> = emptyList()
)

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as ZenSposedApp).database.sessionDao()

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfTomorrow(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    private fun startOfWeek(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    }.timeInMillis

    private fun startOfNextWeek(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        add(Calendar.WEEK_OF_YEAR, 1)
    }.timeInMillis

    private fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfNextMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, 1)
    }.timeInMillis

    val state: StateFlow<StatsUiState> = run {
        val dayStart = startOfToday()
        val dayEnd = startOfTomorrow()
        val weekStart = startOfWeek()
        val weekEnd = startOfNextWeek()
        val monthStart = startOfMonth()
        val monthEnd = startOfNextMonth()

        val dayWeek = combine(
            dao.observeFocusedMsBetween(dayStart, dayEnd),
            dao.observeSessionCountBetween(dayStart, dayEnd),
            dao.observeFocusedMsBetween(weekStart, weekEnd),
            dao.observeSessionCountBetween(weekStart, weekEnd)
        ) { dayMs, dayCount, weekMs, weekCount ->
            arrayOf(dayMs, dayCount.toLong(), weekMs, weekCount.toLong())
        }

        val month = combine(
            dao.observeFocusedMsBetween(monthStart, monthEnd),
            dao.observeSessionCountBetween(monthStart, monthEnd),
            dao.observeCategoryTotalsBetween(monthStart, monthEnd)
        ) { monthMs, monthCount, cats ->
            Triple(monthMs, monthCount, cats)
        }

        combine(dayWeek, month, dao.observeAll()) { dw, m, history ->
            StatsUiState(
                dayFocusedMs = dw[0] as Long,
                daySessions = (dw[1] as Long).toInt(),
                weekFocusedMs = dw[2] as Long,
                weekSessions = (dw[3] as Long).toInt(),
                monthFocusedMs = m.first,
                monthSessions = m.second,
                monthCategories = m.third,
                history = history
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())
    }
}
