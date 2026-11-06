package dev.chaik.remember.feature.settings.feedback

import dev.chaik.remember.feature.stats.onTapAnywhere
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import dev.chaik.remember.R
import dev.chaik.remember.core.feedback.Feedback
import dev.chaik.remember.core.feedback.FeedbackSender
import dev.chaik.remember.core.feedback.FeedbackType
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.settings.SettingsCard
import dev.chaik.remember.feature.settings.SettingsSwitchRow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object FeedbackRoute : NavKey

/** Чем закончилась отправка. */
private enum class SendResult { SENT, FAILED, NOT_READY }

private val UriListSaver = listSaver<List<Uri>, String>(save = { list -> list.map(Uri::toString) }, restore = { it.map(Uri::parse) })

/**
 * Обратная связь: идея или ошибка, текст, как ответить, до 10 фото. Уходит на сервер разработчика
 * одним запросом — только по кнопке «Отправить» и только то, что здесь написано.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sender = remember { FeedbackSender(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var type by rememberSaveable { mutableStateOf(FeedbackType.IDEA) }
    var message by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var withDevice by rememberSaveable { mutableStateOf(true) }
    var photos by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf(emptyList<Uri>()) }
    var sending by remember { mutableStateOf(false) }
    // Клавиатуру свернули — поле теряет фокус (только при сворачивании, не при открытии)
    val keyboardVisible = WindowInsets.isImeVisible
    var keyboardWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardVisible) {
        if (keyboardWasVisible && !keyboardVisible) focusManager.clearFocus()
        keyboardWasVisible = keyboardVisible
    }
    // Сколько уже отправлено сегодня: не больше FeedbackSender.DAILY_LIMIT в день
    var sentToday by remember { mutableIntStateOf(sender.sentToday()) }
    val limitReached = sentToday >= FeedbackSender.DAILY_LIMIT
    var result by remember { mutableStateOf<SendResult?>(null) }

    val left = FeedbackSender.MAX_PHOTOS - photos.size
    val pickMany = rememberLauncherForActivityResult(PickMultipleVisualMedia(FeedbackSender.MAX_PHOTOS)) { picked ->
        photos = (photos + picked).distinct().take(FeedbackSender.MAX_PHOTOS)
    }
    val pickOne = rememberLauncherForActivityResult(PickVisualMedia()) { picked ->
        picked?.let { photos = (photos + it).distinct().take(FeedbackSender.MAX_PHOTOS) }
    }
    fun addPhotos() {
        val request = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
        // Выбор нескольких требует места хотя бы под два — на последнее место выбираем одно
        if (left >= 2) pickMany.launch(request) else if (left == 1) pickOne.launch(request)
    }

    fun send() {
        focusManager.clearFocus()
        if (!sender.configured) {
            result = SendResult.NOT_READY
            return
        }
        sending = true
        scope.launch {
            val ok = runCatching { sender.send(Feedback(type, message, contact, withDevice, photos)) }.isSuccess
            sending = false
            sentToday = sender.sentToday()
            result = if (ok) SendResult.SENT else SendResult.FAILED
            if (ok) {
                message = ""
                photos = emptyList()
            }
        }
    }

    ScreenScaffold(title = stringResource(R.string.settings_feedback), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                // Тап по свободному месту убирает курсор и клавиатуру
                .onTapAnywhere { focusManager.clearFocus() }
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(R.string.feedback_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedbackType.entries.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = { type = option },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        label = { Text(stringResource(if (option == FeedbackType.IDEA) R.string.feedback_idea else R.string.feedback_bug)) },
                    )
                }
            }
            OutlinedTextField(
                value = message,
                onValueChange = { message = it.take(FeedbackSender.MAX_MESSAGE) },
                label = { Text(stringResource(R.string.feedback_message)) },
                placeholder = {
                    Text(stringResource(if (type == FeedbackType.IDEA) R.string.feedback_idea_hint else R.string.feedback_bug_hint))
                },
                supportingText = { Text("${message.length} / ${FeedbackSender.MAX_MESSAGE}") },
                minLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(R.string.feedback_photos, photos.size, FeedbackSender.MAX_PHOTOS),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.forEach { uri ->
                    Box(Modifier.size(PhotoSize)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { photos = photos - uri },
                        ) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.feedback_remove_photo), tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                if (left > 0) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(PhotoSize)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClick = ::addPhotos),
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.feedback_add_photo), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it.take(200) },
                label = { Text(stringResource(R.string.feedback_contact)) },
                placeholder = { Text(stringResource(R.string.feedback_contact_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.feedback_device),
                    checked = withDevice,
                    onChange = { withDevice = it },
                    hint = stringResource(R.string.feedback_device_hint),
                )
            }
            Button(
                onClick = ::send,
                enabled = !sending && message.isNotBlank() && !limitReached,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.feedback_send))
                }
            }
            if (limitReached) {
                Text(
                    stringResource(R.string.feedback_limit, FeedbackSender.DAILY_LIMIT),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                stringResource(R.string.feedback_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    result?.let { shown ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = {
                Text(
                    stringResource(
                        when (shown) {
                            SendResult.SENT -> R.string.feedback_sent_title
                            SendResult.FAILED -> R.string.feedback_failed_title
                            SendResult.NOT_READY -> R.string.feedback_not_ready_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        when (shown) {
                            SendResult.SENT -> R.string.feedback_sent_text
                            SendResult.FAILED -> R.string.feedback_failed_text
                            SendResult.NOT_READY -> R.string.feedback_not_ready_text
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { result = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/** Размер миниатюры фото. */
private val PhotoSize = 76.dp
