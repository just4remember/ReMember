package dev.chaik.remember.feature.settings.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.settings.AppLanguage
import dev.chaik.remember.core.settings.Currencies
import dev.chaik.remember.core.settings.currencySymbol
import dev.chaik.remember.core.settings.Gender
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.settings.SettingsCard
import dev.chaik.remember.core.settings.DayStartOptions
import dev.chaik.remember.feature.settings.SettingsDropdown
import dev.chaik.remember.feature.settings.SettingsGroupTitle
import dev.chaik.remember.feature.settings.SettingsRadioRow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

@Serializable
data object ProfileRoute : NavKey

/** Языки, которые уже переведены; остальные в списке видны, но недоступны. */
private val TranslatedLanguages = setOf("", "ru", "en")

/** Профиль: никнейм, пол, язык, валюта, первый день недели. Всё сохраняется сразу. */
@OptIn(ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val store = container.settings
    val settings = LocalAppSettings.current

    // Никнейм пишем после паузы в наборе, а не на каждую букву
    var nickname by remember { mutableStateOf(settings.nickname) }
    LaunchedEffect(Unit) {
        snapshotFlow { nickname }.debounce(400).collect { name -> store.update { it.copy(nickname = name.trim()) } }
    }
    var language by remember { mutableStateOf(AppLanguage.current(context)) }

    ScreenScaffold(title = stringResource(R.string.settings_profile), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { if (it.length <= 40) nickname = it },
                label = { Text(stringResource(R.string.profile_nickname)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            SettingsGroupTitle(stringResource(R.string.profile_gender))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(
                    Gender.UNSET to R.string.profile_gender_unset,
                    Gender.MALE to R.string.profile_gender_male,
                    Gender.FEMALE to R.string.profile_gender_female,
                )
                options.forEachIndexed { i, (gender, label) ->
                    SegmentedButton(
                        selected = settings.gender == gender,
                        onClick = { store.update { it.copy(gender = gender) } },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        icon = {},
                        label = { Text(stringResource(label), maxLines = 1) },
                    )
                }
            }

            SettingsGroupTitle(stringResource(R.string.profile_language))
            SettingsCard {
                AppLanguage.available.forEach { tag ->
                    val translated = tag in TranslatedLanguages
                    SettingsRadioRow(
                        label = stringResource(
                            when (tag) {
                                "" -> R.string.profile_language_system
                                "ru" -> R.string.profile_language_ru
                                else -> R.string.profile_language_en
                            },
                        ),
                        hint = if (translated) null else stringResource(R.string.profile_language_soon),
                        selected = language == tag,
                        enabled = translated,
                        onClick = {
                            language = tag
                            // Android сам пересоздаст экраны на новом языке
                            AppLanguage.set(context, tag)
                        },
                    )
                }
            }

            SettingsGroupTitle(stringResource(R.string.profile_currency))
            CurrencyMenu(selected = settings.currency) { code ->
                if (settings.currency != code) {
                    store.update { it.copy(currency = code) }
                    // Траты ведутся в одной валюте — у всех записей меняется знак
                    container.appScope.launch { container.moneyRepository.changeCurrency(code) }
                }
            }
            Text(
                text = stringResource(R.string.profile_currency_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

            SettingsGroupTitle(stringResource(R.string.profile_day_start))
            SettingsDropdown(
                selected = settings.dayStartMinutes,
                options = DayStartOptions.map { it to "%02d:%02d".format(it / 60, it % 60) },
                onSelect = { minutes -> store.update { it.copy(dayStartMinutes = minutes) } },
            )
            Text(
                text = stringResource(R.string.profile_day_start_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

            SettingsGroupTitle(stringResource(R.string.profile_first_day))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = settings.firstDayOfWeek == day,
                        onClick = { store.update { it.copy(firstDayOfWeek = day) } },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.titlecase() }) },
                    )
                }
            }
        }
    }
}

/** Выпадающий список валют: «₽  Российский рубль». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyMenu(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = "${currencySymbol(selected)}  ${currencyName(selected)}",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Currencies.forEach { (code, symbol) ->
                DropdownMenuItem(
                    text = { Text("$symbol  ${currencyName(code)}") },
                    onClick = {
                        expanded = false
                        onSelect(code)
                    },
                )
            }
        }
    }
}

/** «Российский рубль», «US Dollar» — на языке приложения. */
private fun currencyName(code: String): String =
    runCatching { Currency.getInstance(code).getDisplayName(Locale.getDefault()) }.getOrDefault(code)
        .replaceFirstChar { it.titlecase() }
