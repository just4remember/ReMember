package dev.chaik.remember.feature.health

import dev.chaik.remember.core.health.DEFAULT_WEIGHT_KG
import android.os.RemoteException
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chaik.remember.app.AppContainer
import dev.chaik.remember.core.health.DayActivity
import dev.chaik.remember.core.health.HealthAvailability
import dev.chaik.remember.core.health.HealthGoals
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Что показывать на странице здоровья. */
sealed interface HealthState {
    data object Loading : HealthState
    data class Unavailable(val needsUpdate: Boolean) : HealthState
    data object NoPermission : HealthState
    data object Ready : HealthState
    data class Failed(val message: String) : HealthState
}

/** Здоровье: активность выбранного дня и мини-кольца недели. */
class HealthViewModel(private val container: AppContainer) : ViewModel() {
    private val health = container.health

    var state by mutableStateOf<HealthState>(HealthState.Loading)
        private set
    var selected by mutableStateOf(LocalDate.now())
        private set

    /** Прочитанные дни; прошлые не меняются, сегодняшний перечитывается при каждом возврате. */
    val days = mutableStateMapOf<LocalDate, DayActivity>()

    val goals: StateFlow<HealthGoals> = container.healthGoals.goals

    private var weekShown: LocalDate? = null
    private var loading: Job? = null

    /** Проверить доступ и перечитать неделю выбранного дня (при открытии и возврате на экран). */
    fun refresh() {
        state = when (health.availability) {
            HealthAvailability.UNAVAILABLE -> HealthState.Unavailable(needsUpdate = false)
            HealthAvailability.NEEDS_UPDATE -> HealthState.Unavailable(needsUpdate = true)
            HealthAvailability.AVAILABLE -> state.takeIf { it == HealthState.Ready } ?: HealthState.Loading
        }
        if (state is HealthState.Unavailable) return
        days.remove(LocalDate.now())
        viewModelScope.launch {
            val granted = runCatching { health.hasPermissions() }.getOrDefault(false)
            if (!granted) {
                state = HealthState.NoPermission
                return@launch
            }
            state = HealthState.Ready
            weekShown?.let(::loadWeek) ?: loadWeek(weekStart(selected, container.settings.settings.value.firstDayOfWeek))
        }
    }

    fun select(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        selected = date
    }

    /** Неделя с первого дня [monday] — дочитать дни, которых ещё нет. */
    fun loadWeek(monday: LocalDate) {
        weekShown = monday
        if (state != HealthState.Ready) return
        val today = LocalDate.now()
        val missing = (0L..6L).map { monday.plusDays(it) }.filter { !it.isAfter(today) && it !in days }
        if (missing.isEmpty()) return
        loading?.cancel()
        loading = viewModelScope.launch {
            try {
                // Выбранный день — первым, чтобы большие кольца появились сразу
                // Вес — последний из зала; без него — средний
                val weight = container.workoutRepository.lastBodyWeight() ?: DEFAULT_WEIGHT_KG
                missing.sortedBy { if (it == selected) 0 else 1 }.forEach { days[it] = health.day(it, weight) }
            } catch (e: CancellationException) {
                // Новая загрузка заменила эту (пролистали неделю) — это не ошибка
                throw e
            } catch (e: SecurityException) {
                state = HealthState.NoPermission
            } catch (e: RemoteException) {
                fail(e)
            } catch (e: IOException) {
                fail(e)
            } catch (e: IllegalStateException) {
                fail(e)
            }
        }
    }

    fun saveGoals(goals: HealthGoals) = container.healthGoals.save(goals)

    private fun fail(e: Exception) {
        Log.w(TAG, "Health Connect не ответил", e)
        state = HealthState.Failed(e.message.orEmpty())
    }

    private companion object {
        const val TAG = "HealthData"
    }
}

/** Первый день недели [date] — по настройке Профиля. */
fun weekStart(date: LocalDate, first: DayOfWeek): LocalDate = date.minusDays(((date.dayOfWeek.value - first.value + 7) % 7).toLong())
