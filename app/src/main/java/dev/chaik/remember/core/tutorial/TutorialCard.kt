package dev.chaik.remember.core.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R

/**
 * Карточка обучения по центру экрана поверх всего: затемнение под ней не пропускает нажатия.
 *
 * [step] — «2 из 6» в туре; [onBack] — шаг назад (null — кнопки нет); [onNext] — «Далее»,
 * «Готово» или «Понятно» ([nextLabel]); [onClose] — крестик, выключает обучение целиком.
 */
@Composable
fun TutorialCard(
    lesson: Lesson,
    step: String?,
    nextLabel: String,
    onBack: (() -> Unit)?,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    // «Назад» системы: в туре — шаг назад (на первом шаге ничего), у одиночной карточки — как «Понятно»
    BackHandler {
        when {
            onBack != null -> onBack()
            step == null -> onNext()
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(24.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(top = 12.dp)) {
                        step?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            stringResource(lesson.title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.align(Alignment.Top)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.tutorial_close))
                    }
                }
                Text(
                    stringResource(lesson.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, end = 12.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, end = 4.dp),
                ) {
                    if (onBack != null) TextButton(onClick = onBack) { Text(stringResource(R.string.tutorial_back)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onNext) { Text(nextLabel) }
                }
            }
        }
    }
}
