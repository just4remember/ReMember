package dev.chaik.remember.feature.notes.editor

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.fragment.app.FragmentActivity
import dev.chaik.remember.core.security.BiometricUnlock
import dev.chaik.remember.feature.lock.LockScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.editor.EditorToolbar
import dev.chaik.remember.core.editor.EditorToolbarHeight
import dev.chaik.remember.core.editor.PhotoViewer
import dev.chaik.remember.core.editor.RichEditor
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.components.TagChips
import dev.chaik.remember.core.ui.LocalTabVisible
import dev.chaik.remember.core.ui.primaryCornerModifier
import dev.chaik.remember.feature.notes.ConfirmDialog
import dev.chaik.remember.feature.notes.FolderPickerDialog
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/** Заметка: [noteId] = 0 — новая, в папке [folderId]. */
@Serializable
data class NoteEditorRoute(
    val noteId: Long = 0,
    val folderId: Long? = null,
    /** Отличает новые заметки друг от друга: у каждой должно быть своё состояние экрана. */
    val opening: Long = 0,
) : NavKey

private const val MAX_PHOTOS_PER_PICK = 10

/** Вход в защищённую заметку: тот же PIN и отпечаток, что у приложения; окно отпечатка — сразу. */
@Composable
private fun ProtectedNoteLock(title: String, onUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val lock = container.appLock
    val activity = LocalActivity.current as? FragmentActivity
    val biometricTitle = stringResource(R.string.notes_protected)
    val usePin = stringResource(R.string.lock_biometric_use_pin)
    val showBiometric: (() -> Unit)? = activity
        ?.takeIf { lock.biometricEnabled && BiometricUnlock.isAvailable(it) }
        ?.let { host ->
            {
                lock.allowExternal()
                BiometricUnlock.prompt(host, biometricTitle, usePin, onSuccess = onUnlocked)
            }
        }
    // Окно отпечатка не показываем поверх другой вкладки, в фоне и пока закрыто само приложение
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tabVisible = LocalTabVisible.current
    LaunchedEffect(tabVisible) {
        if (!tabVisible) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        lock.locked.first { !it }
        showBiometric?.invoke()
    }
    LockScreen(lock = lock, onUnlocked = onUnlocked, onBiometric = showBiometric, modifier = modifier, title = title)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    route: NoteEditorRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val viewModel = viewModel { NoteEditorViewModel(container, route.noteId, route.folderId) }
    val editor = viewModel.editor
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    // Защита. Без PIN в приложении проверять нечем: заметка открыта, а кнопка просит сначала задать PIN
    val lock = container.appLock
    val focusManager = LocalFocusManager.current
    var unlocked by remember { mutableStateOf(false) }
    var disablingProtection by remember { mutableStateOf(false) }
    var needPin by remember { mutableStateOf(false) }
    var openingPicker by remember { mutableStateOf(false) }
    val askToOpen = viewModel.isLoaded && viewModel.isProtected && lock.enabled && !unlocked
    val showLock = askToOpen || disablingProtection
    fun relock() {
        if (!viewModel.isProtected) return
        focusManager.clearFocus()
        unlocked = false
        disablingProtection = false
    }
    // Смена вкладки закроет защищённую заметку (см. protectedScreens), обычная останется открытой
    val protectedNow = viewModel.isProtected && lock.enabled
    LaunchedEffect(protectedNow) {
        if (protectedNow) container.protectedScreens.add(route) else container.protectedScreens.remove(route)
    }
    val tabVisible = LocalTabVisible.current
    LaunchedEffect(tabVisible) {
        if (!tabVisible) {
            viewModel.flush()
            relock()
        }
    }
    BackHandler(enabled = disablingProtection) { disablingProtection = false }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.flush()
        // Окно выбора фото из самой заметки — не уход из неё
        if (!openingPicker) relock()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { openingPicker = false }

    val pickPhotos = rememberLauncherForActivityResult(PickMultipleVisualMedia(MAX_PHOTOS_PER_PICK)) {
        viewModel.addPhotos(it)
    }
    val launchPicker = {
        // Выбор фото — системное окно: блокировка по возвращении не нужна
        container.appLock.allowExternal()
        openingPicker = true
        pickPhotos.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }
    val failedImport = stringResource(R.string.editor_photo_import_failed)
    LaunchedEffect(viewModel.failedImports) {
        if (viewModel.failedImports > 0) {
            Toast.makeText(context, failedImport, Toast.LENGTH_SHORT).show()
            viewModel.consumeImportError()
        }
    }

    var openedPhotos by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var pickingFolder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.isImeVisible
    val keyboard = LocalSoftwareKeyboardController.current

    // В шапке — папка заметки, чтобы было видно, где она лежит
    val folderName = folders.firstOrNull { it.id == viewModel.folderId }?.name
        ?: stringResource(R.string.notes_no_folder)

    ScreenScaffold(
        title = folderName,
        modifier = modifier,
        onBack = if (disablingProtection) ({ disablingProtection = false }) else onBack,
        actions = {
            if (showLock) return@ScreenScaffold
            IconButton(
                onClick = {
                    when {
                        viewModel.isProtected && lock.enabled -> {
                            focusManager.clearFocus()
                            disablingProtection = true
                        }
                        viewModel.isProtected -> viewModel.changeProtected(false)
                        lock.enabled -> {
                            unlocked = true
                            viewModel.changeProtected(true)
                        }
                        else -> needPin = true
                    }
                },
            ) {
                Icon(
                    imageVector = if (viewModel.isProtected) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                    contentDescription = stringResource(if (viewModel.isProtected) R.string.notes_unprotect else R.string.notes_protect),
                    tint = if (viewModel.isProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::togglePinned) {
                Icon(
                    imageVector = if (viewModel.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = stringResource(if (viewModel.pinned) R.string.notes_unpin else R.string.notes_pin),
                    tint = if (viewModel.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { pickingFolder = true }) {
                Icon(Icons.AutoMirrored.Outlined.DriveFileMove, stringResource(R.string.notes_move_to_folder))
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.notes_delete), tint = MaterialTheme.colorScheme.error)
            }
        },
    ) { padding ->
        if (showLock) {
            ProtectedNoteLock(
                title = stringResource(if (disablingProtection) R.string.notes_unprotect else R.string.notes_protected),
                onUnlocked = {
                    if (disablingProtection) {
                        disablingProtection = false
                        viewModel.changeProtected(false)
                    }
                    unlocked = true
                },
                // Шапка и островок уже учитывают системные панели: второй раз их не вычитаем
                modifier = Modifier
                    .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                    .consumeWindowInsets(WindowInsets.safeDrawing),
            )
            return@ScreenScaffold
        }
        val bottomSpace = if (keyboardVisible) EditorToolbarHeight + 24.dp else padding.calculateBottomPadding() + 96.dp

        Box(Modifier.fillMaxSize()) {
            if (viewModel.isLoaded) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                        // Отступ под шапку — снаружи прокрутки: строка под шапкой считается невидимой,
                        // и курсор, оказавшийся там, редактор подкрутит обратно
                        .padding(top = padding.calculateTopPadding())
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    TitleField(
                        value = viewModel.title,
                        onChange = { viewModel.title = it },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TagChips(
                        tags = viewModel.tags,
                        allTags = allTags,
                        onRemove = viewModel::removeTag,
                        onToggle = viewModel::toggleTag,
                        onCreate = viewModel::addTag,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 16.dp))
                    RichEditor(
                        state = editor,
                        photoFile = container.photoStorage::file,
                        onOpenPhotos = { ids, index -> openedPhotos = ids to index },
                        placeholder = stringResource(R.string.notes_text_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Пустое место под текстом: тап ставит курсор в конец заметки
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(bottomSpace)
                            .clickable(interactionSource = null, indication = null) { editor.focusEnd() },
                    )
                }
            }

            AnimatedVisibility(
                visible = !keyboardVisible,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = primaryCornerModifier(bottom = padding.calculateBottomPadding()),
            ) {
                FloatingActionButton(onClick = { if (!viewModel.isImporting) launchPicker() }) {
                    if (viewModel.isImporting) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.editor_add_photo))
                    }
                }
            }

            AnimatedVisibility(
                visible = keyboardVisible && editor.hasFocus,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding(),
            ) {
                EditorToolbar(
                    state = editor,
                    onAddPhoto = launchPicker,
                    onHideKeyboard = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    },
                )
            }
        }
    }

    if (pickingFolder) {
        FolderPickerDialog(
            folders = folders,
            current = viewModel.folderId,
            onPick = {
                viewModel.moveToFolder(it)
                pickingFolder = false
            },
            onDismiss = { pickingFolder = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.notes_delete_title),
            text = stringResource(R.string.notes_delete_text),
            confirmLabel = stringResource(R.string.notes_delete),
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
    if (needPin) {
        AlertDialog(
            onDismissRequest = { needPin = false },
            title = { Text(stringResource(R.string.notes_protect)) },
            text = { Text(stringResource(R.string.notes_protect_need_pin)) },
            confirmButton = { TextButton(onClick = { needPin = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }

    openedPhotos?.let { (ids, index) ->
        PhotoViewer(
            files = ids.map(container.photoStorage::file),
            initialIndex = index,
            onDismiss = { openedPhotos = null },
        )
    }
}

/** Крупное поле заголовка без рамки. */
@Composable
private fun TitleField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.headlineSmall.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
    )
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(stringResource(R.string.notes_title_hint), style = style.copy(color = MaterialTheme.colorScheme.outline))
                }
                inner()
            }
        },
    )
}
