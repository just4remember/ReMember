package dev.chaik.remember.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.chaik.remember.core.database.entity.MoneyCategoryEntity
import dev.chaik.remember.core.database.entity.MoneyTransactionEntity
import dev.chaik.remember.core.model.MoneyType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface MoneyDao {
    // Категории

    @Query("SELECT * FROM money_category WHERE type = :type AND archived = 0 ORDER BY sort_order, name")
    fun observeCategories(type: MoneyType): Flow<List<MoneyCategoryEntity>>

    /** Все, включая убранные: у прошлых операций остаётся название категории. */
    @Query("SELECT * FROM money_category")
    fun observeAllCategories(): Flow<List<MoneyCategoryEntity>>

    @Query("SELECT IFNULL(MAX(sort_order) + 1, 0) FROM money_category WHERE type = :type AND archived = 0")
    suspend fun nextCategoryOrder(type: MoneyType): Int

    @Query("UPDATE money_category SET sort_order = :order WHERE id = :id")
    suspend fun setCategoryOrder(id: Long, order: Int)

    @Query("UPDATE money_category SET archived = 1 WHERE id = :id")
    suspend fun archiveCategory(id: Long)

    /** Прошлые комментарии категории (без категории — NULL): сначала частые, среди них свежие. */
    @Query(
        """
        SELECT comment FROM money_transaction
        WHERE type = :type AND category_id IS :categoryId AND comment != ''
        GROUP BY comment
        ORDER BY COUNT(*) DESC, MAX(created_at) DESC
        """,
    )
    fun observeComments(type: MoneyType, categoryId: Long?): Flow<List<String>>

    @Insert
    suspend fun insertCategory(category: MoneyCategoryEntity): Long

    @Query("SELECT * FROM money_category WHERE type = :type AND name = :name LIMIT 1")
    suspend fun findCategory(type: MoneyType, name: String): MoneyCategoryEntity?

    @Update
    suspend fun updateCategory(category: MoneyCategoryEntity)

    /** Все категории, и убранные тоже. */
    @Query("SELECT * FROM money_category")
    suspend fun allCategories(): List<MoneyCategoryEntity>

    // Операции

    @Query("SELECT * FROM money_transaction WHERE date = :date ORDER BY created_at DESC")
    fun observeForDate(date: LocalDate): Flow<List<MoneyTransactionEntity>>

    @Query("SELECT * FROM money_transaction WHERE date BETWEEN :from AND :to ORDER BY date DESC, created_at DESC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<MoneyTransactionEntity>>

    @Insert
    suspend fun insert(transaction: MoneyTransactionEntity): Long

    @Update
    suspend fun update(transaction: MoneyTransactionEntity)

    @Query("UPDATE money_transaction SET currency = :currency")
    suspend fun setCurrency(currency: String)

    @Query("DELETE FROM money_transaction WHERE id = :id")
    suspend fun delete(id: Long)

    /** Суммы расходов и доходов за период в одной валюте. */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_minor END), 0) AS expenseMinor,
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_minor END), 0) AS incomeMinor
        FROM money_transaction
        WHERE date BETWEEN :from AND :to AND currency = :currency
        """,
    )
    fun observeTotals(from: LocalDate, to: LocalDate, currency: String): Flow<MoneyTotalsRow>
}

data class MoneyTotalsRow(
    val expenseMinor: Long,
    val incomeMinor: Long,
)
