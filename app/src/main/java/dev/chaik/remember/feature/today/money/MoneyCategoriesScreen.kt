package dev.chaik.remember.feature.today.money

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.data.FAVORITE_EXPENSE_CATEGORIES
import dev.chaik.remember.core.model.MoneyCategory
import dev.chaik.remember.core.model.MoneyType
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.primaryCornerModifier
import dev.chaik.remember.feature.notes.ConfirmDialog
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object MoneyCategoriesRoute : NavKey

/** Категории трат и доходов: свои, порядок, значок и название. */
@Composable
fun MoneyCategoriesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val repository = container.moneyRepository
    var type by rememberSaveable { mutableStateOf(MoneyType.EXPENSE) }
    val categories by remember(type) { repository.observeCategories(type) }.collectAsStateWithLifecycle(emptyList())
    // Открытая форма: id = 0 — новая категория
    var editing by remember { mutableStateOf<MoneyCategory?>(null) }
    val colors = MaterialTheme.colorScheme

    fun move(index: Int, by: Int) {
        val ids = categories.map { it.id }.toMutableList()
        ids.add(index + by, ids.removeAt(index))
        container.appScope.launch { repository.reorderCategories(ids) }
    }

    ScreenScaffold(title = stringResource(R.string.money_categories), modifier = modifier, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "type") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(MoneyType.EXPENSE to R.string.money_expense, MoneyType.INCOME to R.string.money_income)
                            .forEachIndexed { i, (option, label) ->
                                SegmentedButton(
                                    selected = type == option,
                                    onClick = { type = option },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                    label = { Text(stringResource(label)) },
                                )
                            }
                    }
                }
                if (type == MoneyType.EXPENSE) {
                    item(key = "hint") {
                        Text(
                            text = stringResource(R.string.money_categories_hint, FAVORITE_EXPENSE_CATEGORIES),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                itemsIndexed(categories, key = { _, it -> it.id }) { index, category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceContainerLow)
                            .clickable { editing = category }
                            .padding(start = 16.dp),
                    ) {
                        Text(category.icon.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        )
                        IconButton(enabled = index > 0, onClick = { move(index, -1) }) {
                            Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.gym_template_up))
                        }
                        IconButton(enabled = index < categories.lastIndex, onClick = { move(index, 1) }) {
                            Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.gym_template_down))
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { editing = MoneyCategory(name = "", type = type) },
                modifier = primaryCornerModifier(bottom = padding.calculateBottomPadding() + 8.dp),
            ) {
                Icon(Icons.Outlined.Add, stringResource(R.string.money_category_new))
            }
        }
    }

    editing?.let { category ->
        CategoryDialog(
            category = category,
            taken = categories.filter { it.id != category.id }.map { it.name.lowercase() }.toSet(),
            onSave = { saved ->
                container.appScope.launch { repository.saveCategory(saved) }
                editing = null
            },
            onDelete = {
                container.appScope.launch { repository.archiveCategory(category.id) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Значок (эмодзи) и название. [taken] — имена, которые уже заняты, в нижнем регистре. */
@Composable
private fun CategoryDialog(
    category: MoneyCategory,
    taken: Set<String>,
    onSave: (MoneyCategory) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var icon by remember { mutableStateOf(category.icon.orEmpty()) }
    var name by remember { mutableStateOf(category.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    val clean = name.trim()
    val duplicate = clean.lowercase() in taken

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (category.id == 0L) R.string.money_category_new else R.string.money_category_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = icon,
                    onValueChange = { if (it.length <= MAX_ICON_CHARS) icon = it },
                    label = { Text(stringResource(R.string.money_category_icon)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.money_category_name)) },
                    isError = duplicate,
                    supportingText = if (duplicate) {
                        { Text(stringResource(R.string.money_category_taken)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = clean.isNotEmpty() && !duplicate,
                onClick = { onSave(category.copy(name = clean, icon = icon.trim().ifEmpty { null })) },
            ) { Text(stringResource(R.string.gym_save)) }
        },
        dismissButton = {
            Row {
                if (category.id != 0L) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.notes_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.money_category_delete),
            text = stringResource(R.string.money_category_delete_text),
            confirmLabel = stringResource(R.string.notes_delete),
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Эмодзи бывает из нескольких символов (флаги, кожа). */
private const val MAX_ICON_CHARS = 16
