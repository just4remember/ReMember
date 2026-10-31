package dev.chaik.remember.core.model

import java.time.Instant
import java.time.LocalDate

enum class MoneyType { EXPENSE, INCOME }

data class MoneyCategory(
    val id: Long = 0,
    val name: String,
    val type: MoneyType,
    val icon: String? = null,
    /** Цвет ARGB, null — цвет по умолчанию. */
    val color: Long? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

/** Трата или доход. */
data class MoneyTransaction(
    val id: Long = 0,
    val date: LocalDate,
    val type: MoneyType,
    /** Сумма в копейках: 150,50 ₽ = 15050. */
    val amountMinor: Long,
    val currency: String = DEFAULT_CURRENCY,
    val categoryId: Long? = null,
    val comment: String = "",
    val createdAt: Instant? = null,
)

/** Итог за период в одной валюте. */
data class MoneyTotals(
    val expenseMinor: Long,
    val incomeMinor: Long,
) {
    val balanceMinor: Long get() = incomeMinor - expenseMinor
}

const val DEFAULT_CURRENCY = "RUB"

/**
 * Подсказки к комментарию из прошлых комментариев категории ([known] — частые первыми):
 * подходящие по набранному тексту, без повторов и без уже набранного целиком.
 */
fun commentHints(known: List<String>, typed: String, limit: Int = COMMENT_HINTS): List<String> {
    val query = typed.trim().lowercase()
    return known
        .distinctBy { it.lowercase() }
        .filter { val text = it.lowercase(); text != query && text.contains(query) }
        .take(limit)
}

private const val COMMENT_HINTS = 12
