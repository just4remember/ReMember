package dev.chaik.remember.feature.games.clicker

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chaik.remember.feature.games.clicker.engine.CardDef
import dev.chaik.remember.feature.games.clicker.engine.ClickerEngine
import dev.chaik.remember.feature.games.clicker.engine.ClickerState
import dev.chaik.remember.feature.games.clicker.engine.PerkDef
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Кликер: состояние и правила ([ClickerEngine]). Пока экран открыт — доход идёт каждые
 * 200 мс; ушли с экрана или свернули приложение — сохраняем, а при возвращении начисляем
 * доход вне игры (с коэффициентом и потолком) и показываем, сколько накопилось.
 */
class ClickerViewModel(private val store: ClickerStore) : ViewModel() {
    var engine by mutableStateOf<ClickerEngine?>(null)
        private set
    var state by mutableStateOf(ClickerState())
        private set

    /** Сколько принёс доход вне игры — для окошка при возвращении; null — не показывать. */
    var awayGain by mutableStateOf<Double?>(null)
        private set

    /** Отладка: во сколько раз быстрее идёт время в игре. */
    var speed by mutableIntStateOf(1)
        private set

    private var backgroundAt: Long? = null
    private var ticking: Job? = null
    private var visible = false

    init {
        viewModelScope.launch {
            val e = ClickerEngine(store.catalog())
            val save = store.load()
            if (save != null) state = save.state
            engine = e
            save?.let { applyAway(System.currentTimeMillis() - it.savedAt) }
            if (visible) startTicking()
        }
    }

    /** Экран снова виден: начислить время отсутствия и запустить доход. */
    fun onForeground() {
        visible = true
        backgroundAt?.let { applyAway(System.currentTimeMillis() - it) }
        backgroundAt = null
        startTicking()
    }

    /** Экран скрыт (ушли, свернули): остановить доход и сохранить. */
    fun onBackground() {
        visible = false
        ticking?.cancel()
        ticking = null
        if (engine == null) return
        backgroundAt = System.currentTimeMillis()
        save()
    }

    private fun applyAway(ms: Long) {
        val e = engine ?: return
        val before = state.earned
        state = e.returnAfter(state, ms / 1000.0)
        val gain = state.earned - before
        // Короткие отлучки окошком не отмечаем
        if (gain >= 1 && ms >= AWAY_NOTICE_MS) awayGain = gain
    }

    fun dismissAway() {
        awayGain = null
    }

    private fun startTicking() {
        val e = engine ?: return
        if (ticking?.isActive == true) return
        ticking = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            var sinceSave = 0L
            while (isActive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val dt = now - last
                last = now
                state = e.play(state, dt / 1000.0 * speed)
                sinceSave += dt
                if (sinceSave >= AUTOSAVE_MS) {
                    sinceSave = 0
                    save()
                }
            }
        }
    }

    private fun save() {
        val snapshot = state
        val at = System.currentTimeMillis()
        viewModelScope.launch { withContext(NonCancellable) { store.save(snapshot, at) } }
    }

    /** Тап: сколько коинов принёс (0 — энергии нет или игра замерла). */
    fun tap(): Double {
        val e = engine ?: return 0.0
        val before = state.coins
        state = e.tap(state)
        return state.coins - before
    }

    private val nowSeconds: Long get() = System.currentTimeMillis() / 1000

    fun canBuy(card: CardDef): Boolean = engine?.canBuy(state, card, nowSeconds) == true

    fun isAvailable(card: CardDef): Boolean = engine?.isAvailable(state, card, nowSeconds) == true

    fun isOnSale(card: CardDef): Boolean = card.onSale(nowSeconds)

    /** Купить следующий уровень; false — нельзя (не хватает, закрыта, максимум). */
    fun buy(card: CardDef): Boolean {
        val e = engine ?: return false
        if (!e.canBuy(state, card, nowSeconds)) return false
        state = e.buy(state, card, nowSeconds)
        return true
    }

    fun buyPerk(perk: PerkDef): Boolean {
        val e = engine ?: return false
        if (!e.canBuyPerk(state, perk)) return false
        state = e.buyPerk(state, perk)
        save()
        return true
    }

    /** Цель достигнута, а поздравление ещё не показано — показать окно. */
    val showGoalReached: Boolean
        get() = engine?.let { it.goalReached(state) && !it.isFrozen(state) && !state.goalSeen } == true

    /** «Позже»: окно больше не всплывает, завершить можно в мета-прогрессии. */
    fun dismissGoalReached() {
        state = state.copy(goalSeen = true)
        save()
    }

    /** Завершить прохождение: перерождение или, если оно последнее, — заморозка игры. */
    fun finishRun() {
        val e = engine ?: return
        state = e.prestige(state).let { if (e.isFrozen(it)) it.copy(goalSeen = true) else it }
        save()
    }

    // Отладка (только в debug-сборке)

    fun debugAway(hours: Double) = applyAway((hours * 3_600_000).toLong()).also { save() }

    fun debugToggleSpeed() {
        speed = if (speed == 1) DEBUG_SPEED else 1
    }

    /** Коинов на час пассивного дохода (не меньше тысячи). */
    fun debugCoins() {
        val e = engine ?: return
        val gift = maxOf(1000.0, e.stats(state).incomePerHour)
        state = state.copy(coins = state.coins + gift, earned = state.earned + gift)
    }

    /** Выдать коины: миллион, а если баланс больше — удвоить его. */
    fun debugGiveCoins() {
        val gift = maxOf(1_000_000.0, state.coins)
        state = state.copy(coins = state.coins + gift, earned = state.earned + gift)
    }

    /** Удвоить доход в час (не меньше +10 тыс.). */
    fun debugGiveIncome() {
        val e = engine ?: return
        state = state.copy(debugIncome = state.debugIncome + maxOf(10_000.0, e.stats(state).incomePerHour))
    }

    /** Удвоить коины за тап (не меньше +100). */
    fun debugGiveTap() {
        val e = engine ?: return
        state = state.copy(debugTap = state.debugTap + maxOf(100.0, e.stats(state).tapValue))
    }

    /** До цели — чтобы проверить перерождение. */
    fun debugReachGoal() {
        val e = engine ?: return
        val missing = e.catalog.rules.goal - state.earned
        if (missing > 0) state = state.copy(coins = state.coins + missing, earned = state.earned + missing)
    }

    /** Снимок игры есть — его можно вернуть. */
    var hasSnapshot by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { hasSnapshot = store.loadSnapshot() != null }
    }

    /** Запомнить игру как есть — перед проверками с читами. */
    fun debugSnapshot() {
        val snapshot = state
        viewModelScope.launch {
            withContext(NonCancellable) { store.saveSnapshot(snapshot, System.currentTimeMillis()) }
            hasSnapshot = true
        }
    }

    /**
     * Вернуть запомненную игру. Время, прошедшее со снимка, не начисляется — игра ровно
     * такая, какой была.
     */
    fun debugRestoreSnapshot() {
        viewModelScope.launch {
            val snapshot = store.loadSnapshot() ?: return@launch
            state = snapshot.state
            save()
        }
    }

    /**
     * Сбросить мета-прогресс: звёздную пыль, улучшения, счёт прохождений и отладочные прибавки.
     * Коины, карточки и уровень остаются.
     */
    fun debugResetMeta() {
        state = state.copy(runs = 0, stardust = 0, perks = emptyMap(), debugIncome = 0.0, debugTap = 0.0, goalSeen = false)
        save()
    }

    fun debugReset() {
        state = ClickerState()
        viewModelScope.launch { store.reset() }
    }

    private companion object {
        // Счётчик монет без тапов — три раза в секунду; тап обновляет сразу
        const val TICK_MS = 333L
        const val AUTOSAVE_MS = 15_000L
        const val AWAY_NOTICE_MS = 60_000L
        const val DEBUG_SPEED = 100
    }
}
