package dev.chaik.remember.feature.today.money

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.chaik.remember.core.data.MoneyRepository
import dev.chaik.remember.core.model.MoneyCategory
import dev.chaik.remember.core.model.MoneyTransaction
import dev.chaik.remember.core.model.MoneyType
import dev.chaik.remember.core.ui.format.MoneyFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Режим «Траты»: форма добавления операции и список за день.
 * Операции сохраняются сразу по кнопке, без автосохранения.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoneyController(
    private val scope: CoroutineScope,
    private val repository: MoneyRepository,
) {
    private val date = MutableStateFlow(LocalDate.now())

    val transactions: StateFlow<List<MoneyTransaction>> = date
        .flatMapLatest { repository.observeForDate(it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expenseCategories: StateFlow<List<MoneyCategory>> =
        repository.observeCategories(MoneyType.EXPENSE).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomeCategories: StateFlow<List<MoneyCategory>> =
        repository.observeCategories(MoneyType.INCOME).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Все категории, включая убранные, — подписи прошлых операций. */
    val allCategories: StateFlow<List<MoneyCategory>> =
        repository.observeAllCategories().stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Форма

    var amount by mutableStateOf("")
    var type by mutableStateOf(MoneyType.EXPENSE)
        private set
    var categoryId by mutableStateOf<Long?>(null)
    var comment by mutableStateOf(TextFieldValue(""))

    /** Прошлые комментарии выбранной категории — подсказки под полем. */
    val knownComments: StateFlow<List<String>> = snapshotFlow { type to categoryId }
        .flatMapLatest { (type, id) -> repository.observeComments(type, id) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Подставить подсказку, курсор — в конец. */
    fun pickComment(text: String) {
        comment = TextFieldValue(text, TextRange(text.length))
    }

    /** Какую операцию сейчас правим; null — добавляем новую. */
    var editing by mutableStateOf<MoneyTransaction?>(null)
        private set

    var amountError by mutableStateOf(false)
        private set

    /** Последняя удалённая операция — её можно вернуть несколько секунд. */
    var lastDeleted by mutableStateOf<MoneyTransaction?>(null)
        private set
    private var undoJob: Job? = null

    fun setDate(date: LocalDate) {
        if (this.date.value != date) cancelEdit()
        this.date.value = date
    }

    fun changeType(type: MoneyType) {
        if (this.type == type) return
        this.type = type
        categoryId = null // категории у трат и доходов разные
    }

    fun onAmountChange(text: String) {
        amount = text
        amountError = false
    }

    /** Добавить новую или сохранить исправленную операцию. */
    fun submit() {
        val amountMinor = MoneyFormat.parse(amount)
        if (amountMinor == null) {
            amountError = true
            return
        }
        val base = editing
        val transaction = MoneyTransaction(
            id = base?.id ?: 0,
            date = base?.date ?: date.value,
            type = type,
            amountMinor = amountMinor,
            categoryId = categoryId,
            comment = comment.text.trim(),
            createdAt = base?.createdAt,
        )
        scope.launch { repository.save(transaction) }
        resetForm()
    }

    fun edit(transaction: MoneyTransaction) {
        editing = transaction
        type = transaction.type
        amount = MoneyFormat.toInput(transaction.amountMinor)
        categoryId = transaction.categoryId
        pickComment(transaction.comment)
        amountError = false
    }

    fun cancelEdit() {
        resetForm()
    }

    fun delete(transaction: MoneyTransaction) {
        if (editing?.id == transaction.id) resetForm()
        scope.launch { repository.delete(transaction.id) }
        lastDeleted = transaction
        undoJob?.cancel()
        undoJob = scope.launch {
            delay(UNDO_TIMEOUT_MS)
            lastDeleted = null
        }
    }

    fun undoDelete() {
        val transaction = lastDeleted ?: return
        undoJob?.cancel()
        lastDeleted = null
        // id = 0 — вставить заново; дата и время создания сохраняются
        scope.launch { repository.save(transaction.copy(id = 0)) }
    }

    private fun resetForm() {
        editing = null
        amount = ""
        comment = TextFieldValue("")
        categoryId = null
        amountError = false
    }

    private companion object {
        const val UNDO_TIMEOUT_MS = 5_000L
    }
}
