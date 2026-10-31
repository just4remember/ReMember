package dev.chaik.remember.feature.today.money

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chaik.remember.R
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.currencySymbol
import dev.chaik.remember.core.data.FAVORITE_EXPENSE_CATEGORIES
import dev.chaik.remember.core.model.MoneyCategory
import dev.chaik.remember.core.model.MoneyTransaction
import dev.chaik.remember.core.model.MoneyType
import dev.chaik.remember.core.model.commentHints
import dev.chaik.remember.core.ui.format.MoneyFormat

private val IncomeColor = Color(0xFF4CAF50)

/** Режим «Траты»: итоги дня, форма и список операций. */
@Composable
fun MoneyContent(controller: MoneyController, modifier: Modifier = Modifier) {
    val transactions by controller.transactions.collectAsStateWithLifecycle()
    val expenseCategories by controller.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by controller.incomeCategories.collectAsStateWithLifecycle()
    val allCategories by controller.allCategories.collectAsStateWithLifecycle()
    val categories = remember(allCategories) { allCategories.associateBy { it.id } }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DayTotals(transactions)
        MoneyForm(
            controller = controller,
            categories = if (controller.type == MoneyType.EXPENSE) expenseCategories else incomeCategories,
            allCategories = categories,
        )

        AnimatedVisibility(controller.lastDeleted != null) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.money_deleted),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = controller::undoDelete) {
                        Text(stringResource(R.string.money_undo), color = MaterialTheme.colorScheme.inversePrimary)
                    }
                }
            }
        }

        if (transactions.isEmpty()) {
            Text(
                stringResource(R.string.money_empty),
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                transactions.forEach { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        category = transaction.categoryId?.let(categories::get),
                        isEditing = controller.editing?.id == transaction.id,
                        onClick = { controller.edit(transaction) },
                        onDelete = { controller.delete(transaction) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayTotals(transactions: List<MoneyTransaction>) {
    val expense = transactions.filter { it.type == MoneyType.EXPENSE }.sumOf { it.amountMinor }
    val income = transactions.filter { it.type == MoneyType.INCOME }.sumOf { it.amountMinor }
    val currency = LocalAppSettings.current.currency
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TotalCard(stringResource(R.string.money_day_expense), MoneyFormat.format(expense, currency), MaterialTheme.colorScheme.error, Modifier.weight(1f))
        TotalCard(stringResource(R.string.money_day_income), MoneyFormat.format(income, currency), IncomeColor, Modifier.weight(1f))
    }
}

@Composable
private fun TotalCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MoneyForm(controller: MoneyController, categories: List<MoneyCategory>, allCategories: Map<Long, MoneyCategory>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (controller.editing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.money_editing),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = controller::cancelEdit) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel))
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(MoneyType.EXPENSE to R.string.money_expense, MoneyType.INCOME to R.string.money_income)
                    .forEachIndexed { i, (type, label) ->
                        SegmentedButton(
                            selected = controller.type == type,
                            onClick = { controller.changeType(type) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            label = { Text(stringResource(label)) },
                        )
                    }
            }

            OutlinedTextField(
                value = controller.amount,
                onValueChange = controller::onAmountChange,
                label = { Text(stringResource(R.string.money_amount, currencySymbol(LocalAppSettings.current.currency))) },
                isError = controller.amountError,
                supportingText = if (controller.amountError) {
                    { Text(stringResource(R.string.money_amount_error)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                textStyle = MaterialTheme.typography.headlineSmall,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            CategoryDropdown(
                categories = categories,
                selectedId = controller.categoryId,
                // Правим старую операцию, а её категорию уже убрали — название всё равно видно
                selected = controller.categoryId?.let(allCategories::get),
                favoriteCount = if (controller.type == MoneyType.EXPENSE) FAVORITE_EXPENSE_CATEGORIES else 0,
                onSelect = { controller.categoryId = it },
            )

            val known by controller.knownComments.collectAsStateWithLifecycle()
            OutlinedTextField(
                value = controller.comment,
                onValueChange = { controller.comment = it },
                label = { Text(stringResource(R.string.money_comment)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { controller.submit() }),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            val hints = remember(known, controller.comment.text) { commentHints(known, controller.comment.text) }
            if (hints.isNotEmpty()) {
                // Одной строкой с прокруткой: форма не прыгает, пока набирается текст
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    hints.forEach { hint ->
                        SuggestionChip(onClick = { controller.pickComment(hint) }, label = { Text(hint, maxLines = 1) })
                    }
                }
            }

            Button(onClick = controller::submit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (controller.editing != null) R.string.money_save else R.string.money_add))
            }
        }
    }
}

/** Выпадающий список категорий; частые — сверху, отделены чертой. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<MoneyCategory>,
    selectedId: Long?,
    selected: MoneyCategory?,
    favoriteCount: Int,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.label ?: stringResource(R.string.money_no_category),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.money_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.money_no_category)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            HorizontalDivider()
            categories.forEachIndexed { index, category ->
                if (index == favoriteCount && favoriteCount > 0) HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            category.label,
                            fontWeight = if (category.id == selectedId) FontWeight.Bold else null,
                        )
                    },
                    onClick = {
                        onSelect(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private val MoneyCategory.label: String get() = "${icon.orEmpty()} $name".trim()

@Composable
private fun TransactionRow(
    transaction: MoneyTransaction,
    category: MoneyCategory?,
    isEditing: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val isIncome = transaction.type == MoneyType.INCOME
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEditing) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(category?.icon ?: "•") }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = category?.name ?: stringResource(R.string.money_no_category),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (transaction.comment.isNotEmpty()) {
                    Text(
                        text = transaction.comment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = MoneyFormat.format(transaction.amountMinor, transaction.currency, withSign = true, sign = if (isIncome) '+' else '−'),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) IncomeColor else MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.money_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
