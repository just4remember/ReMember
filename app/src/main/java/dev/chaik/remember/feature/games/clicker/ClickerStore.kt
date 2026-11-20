package dev.chaik.remember.feature.games.clicker

import android.content.Context
import dev.chaik.remember.core.database.dao.GameDao
import dev.chaik.remember.core.database.entity.ClickerSaveEntity
import dev.chaik.remember.feature.games.clicker.engine.ClickerCatalog
import dev.chaik.remember.feature.games.clicker.engine.ClickerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Набор карточек (из assets; позже — ещё и с сервера) и сохранение игры в базе.
 * База зашифрована и целиком попадает в резервную копию — вместе с ней и прогресс.
 */
class ClickerStore(private val context: Context, private val dao: GameDao) {
    // NaN в энергии — «полная шкала у нового игрока»
    private val json = Json {
        allowSpecialFloatingPointValues = true
        ignoreUnknownKeys = true
    }

    /** Сохранённое состояние и когда сохранено (мс Unix). */
    class Save(val state: ClickerState, val savedAt: Long)

    suspend fun catalog(): ClickerCatalog = withContext(Dispatchers.IO) {
        ClickerCatalog.parse(context.assets.open(CATALOG).bufferedReader().use { it.readText() })
    }

    /** null — игры ещё не было (или сохранение не читается). */
    suspend fun load(): Save? = dao.clicker()?.let { row ->
        runCatching { Save(json.decodeFromString(ClickerState.serializer(), row.state), row.savedAt) }.getOrNull()
    }

    suspend fun save(state: ClickerState, at: Long) {
        dao.saveClicker(ClickerSaveEntity(state = json.encodeToString(ClickerState.serializer(), state), savedAt = at))
    }

    suspend fun reset() = dao.deleteClicker()

    private companion object {
        const val CATALOG = "clicker/cards.json"
    }
}
