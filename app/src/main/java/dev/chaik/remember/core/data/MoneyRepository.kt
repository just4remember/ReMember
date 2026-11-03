package dev.chaik.remember.core.data

import androidx.room.withTransaction
import dev.chaik.remember.core.database.ReMemberDatabase
import dev.chaik.remember.core.model.DEFAULT_CURRENCY
import dev.chaik.remember.core.model.MoneyCategory
import dev.chaik.remember.core.model.MoneyTotals
import dev.chaik.remember.core.model.MoneyTransaction
import dev.chaik.remember.core.model.MoneyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** Траты, доходы и их категории. */
class MoneyRepository(private val db: ReMemberDatabase, private val currency: () -> String = { DEFAULT_CURRENCY }) {
    private val dao = db.moneyDao()

    /**
     * Привести стандартные категории к текущему набору ([CATEGORY_DEFAULTS_VERSION]):
     * добавить недостающие и выставить их порядок. Свои категории пользователя не трогаются.
     *
     * @param appliedVersion версия набора, уже применённая на этом телефоне (0 — ни одной)
     * @return версия, которую нужно запомнить
     */
    suspend fun ensureDefaultCategories(appliedVersion: Int): Int {
        if (appliedVersion >= CATEGORY_DEFAULTS_VERSION) return appliedVersion
        db.withTransaction {
            if (appliedVersion < 2) {
                // Версия 2: «Еда» стала «Продуктами» — старые траты переезжают вместе с категорией
                val food = dao.findCategory(MoneyType.EXPENSE, "Еда")
                if (food != null && dao.findCategory(MoneyType.EXPENSE, "Продукты") == null) {
                    dao.updateCategory(food.copy(name = "Продукты", icon = "🛒"))
                }
            }
            if (appliedVersion < 3) {
                // Версия 3: отдельного значка больше нет — эмодзи переезжает в начало названия
                dao.allCategories().filter { !it.icon.isNullOrBlank() }.forEach { category ->
                    dao.updateCategory(category.copy(name = "${category.icon!!.trim()} ${category.name}", icon = null))
                }
            }
            DefaultCategories.forEachIndexed { index, category ->
                val existing = dao.findCategory(category.type, category.name)
                if (existing == null) {
                    dao.insertCategory(category.copy(sortOrder = index).toEntity())
                } else {
                    dao.updateCategory(existing.copy(sortOrder = index))
                }
            }
        }
        return CATEGORY_DEFAULTS_VERSION
    }

    fun observeCategories(type: MoneyType): Flow<List<MoneyCategory>> =
        dao.observeCategories(type).map { list -> list.map { it.toModel() } }

    fun observeAllCategories(): Flow<List<MoneyCategory>> =
        dao.observeAllCategories().map { list -> list.map { it.toModel() } }

    fun observeComments(type: MoneyType, categoryId: Long?): Flow<List<String>> = dao.observeComments(type, categoryId)

    /**
     * Создаёт категорию (id = 0) в конце списка или обновляет существующую. Возвращает id.
     * Новая с именем убранной возвращает убранную — вместе с её прошлыми операциями.
     */
    suspend fun saveCategory(category: MoneyCategory): Long = db.withTransaction {
        if (category.id != 0L) {
            dao.updateCategory(category.toEntity())
            return@withTransaction category.id
        }
        val order = dao.nextCategoryOrder(category.type)
        val archived = dao.findCategory(category.type, category.name)?.takeIf { it.archived }
        if (archived != null) {
            dao.updateCategory(archived.copy(icon = category.icon, sortOrder = order, archived = false))
            archived.id
        } else {
            dao.insertCategory(category.copy(sortOrder = order).toEntity())
        }
    }

    /** Порядок категорий — как в [ids]. */
    suspend fun reorderCategories(ids: List<Long>) = db.withTransaction {
        ids.forEachIndexed { index, id -> dao.setCategoryOrder(id, index) }
    }

    /** Убрать категорию из выбора. Прошлые операции и их название остаются. */
    suspend fun archiveCategory(id: Long) = dao.archiveCategory(id)

    fun observeForDate(date: LocalDate): Flow<List<MoneyTransaction>> =
        dao.observeForDate(date).map { list -> list.map { it.toModel() } }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<MoneyTransaction>> =
        dao.observeRange(from, to).map { list -> list.map { it.toModel() } }

    fun observeTotals(from: LocalDate, to: LocalDate, currency: String = this.currency()): Flow<MoneyTotals> =
        dao.observeTotals(from, to, currency).map { MoneyTotals(it.expenseMinor, it.incomeMinor) }

    /** Создаёт операцию (id = 0, в валюте из Профиля) или обновляет существующую. Возвращает id. */
    suspend fun save(transaction: MoneyTransaction): Long =
        if (transaction.id == 0L) {
            dao.insert(transaction.copy(currency = currency()).toEntity(createdAt = Instant.now()))
        } else {
            dao.update(transaction.toEntity(createdAt = transaction.createdAt ?: Instant.now()))
            transaction.id
        }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Траты ведутся в одной валюте: сменили её в Профиле — меняется знак у всех записей, суммы те же. */
    suspend fun changeCurrency(code: String) = dao.setCurrency(code)
}

/** Версия стандартного набора категорий. Поднять, если [DefaultCategories] поменялись. */
const val CATEGORY_DEFAULTS_VERSION = 3

/** Сколько первых категорий расходов — «частые»: в списке выбора они отделены чертой. */
const val FAVORITE_EXPENSE_CATEGORIES = 4

/** Стандартные категории в нужном порядке: сначала частые, «Другое» — последней. */
private val DefaultCategories = listOf(
    // Частые
    MoneyCategory(name = "🛒 Продукты", type = MoneyType.EXPENSE),
    MoneyCategory(name = "🎮 Игры", type = MoneyType.EXPENSE),
    MoneyCategory(name = "👕 Одежда", type = MoneyType.EXPENSE),
    MoneyCategory(name = "🔁 Подписки", type = MoneyType.EXPENSE),
    // Остальные
    MoneyCategory(name = "☕ Кафе и рестораны", type = MoneyType.EXPENSE),
    MoneyCategory(name = "🚌 Транспорт", type = MoneyType.EXPENSE),
    MoneyCategory(name = "🚕 Такси", type = MoneyType.EXPENSE),
    MoneyCategory(name = "🎬 Кино", type = MoneyType.EXPENSE),
    MoneyCategory(name = "🏠 Жильё и коммуналка", type = MoneyType.EXPENSE),
    MoneyCategory(name = "📱 Связь и интернет", type = MoneyType.EXPENSE),
    MoneyCategory(name = "💊 Здоровье и аптека", type = MoneyType.EXPENSE),
    MoneyCategory(name = "🏋️ Спорт и зал", type = MoneyType.EXPENSE),
    MoneyCategory(name = "🎁 Подарки", type = MoneyType.EXPENSE),
    MoneyCategory(name = "💄 Красота и уход", type = MoneyType.EXPENSE),
    MoneyCategory(name = "📚 Образование и книги", type = MoneyType.EXPENSE),
    MoneyCategory(name = "🐾 Питомцы", type = MoneyType.EXPENSE),
    MoneyCategory(name = "🧹 Дом и быт", type = MoneyType.EXPENSE),
    MoneyCategory(name = "✈️ Путешествия", type = MoneyType.EXPENSE),
    MoneyCategory(name = "📦 Другое", type = MoneyType.EXPENSE),
    // Доходы
    MoneyCategory(name = "💼 Зарплата", type = MoneyType.INCOME),
    MoneyCategory(name = "🎓 Стипендия", type = MoneyType.INCOME),
    MoneyCategory(name = "💸 Перевод", type = MoneyType.INCOME),
    MoneyCategory(name = "🪙 Кешбэк", type = MoneyType.INCOME),
    MoneyCategory(name = "📦 Другое", type = MoneyType.INCOME),
)
