package dev.chaik.remember.core.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.chaik.remember.core.model.Photo

/**
 * Состояние редактора: список блоков «текст, фото, текст, …», курсор и выделенное фото.
 *
 * Живёт во ViewModel экрана, поэтому переживает поворот экрана.
 * Всё, что меняет документ, идёт через методы этого класса —
 * так чередование «текст / фото» никогда не нарушается.
 */
@Stable
class EditorState(markdown: String = "", private val clock: () -> Long = System::currentTimeMillis) {

    sealed class Block {
        abstract val key: Long
    }

    class TextBlock internal constructor(override val key: Long, value: TextFieldValue) : Block() {
        var value by mutableStateOf(value)
            internal set
        val focusRequester = FocusRequester()

        /** Слова, заменённые Т9, — их можно вернуть как было. Проверять через [AutoCorrection.isValidIn]. */
        var corrections by mutableStateOf<List<AutoCorrection>>(emptyList())
            private set

        /** Замена, о которой клавиатура сообщила, но слово ещё не попало в текст. */
        internal var pendingCorrection: AutoCorrection? = null

        /** Новое значение поля; замены сдвигаются вслед за правкой. */
        internal fun edit(new: TextFieldValue) {
            if (new.text != value.text) {
                corrections = AutoCorrections.update(corrections, pendingCorrection, value.text, new)
                pendingCorrection = null
            }
            value = new
        }

        internal fun revert(correction: AutoCorrection) {
            corrections = corrections - correction
            value = AutoCorrections.revert(value, correction)
        }
    }

    /** Одно фото или сетка из нескольких. */
    class PhotoBlock internal constructor(
        override val key: Long,
        photoIds: List<String>,
        widthPercent: Int,
        align: PhotoAlign,
        expanded: Boolean = false,
        layout: PhotoLayout = PhotoLayout.Grid,
    ) : Block() {
        var photoIds by mutableStateOf(photoIds)
            internal set

        var widthPercent by mutableIntStateOf(widthPercent)
            internal set

        var align by mutableStateOf(align)
            internal set

        /** Показан сеткой, хотя фото столько, что мог бы быть карточкой «Галерея». */
        var expanded by mutableStateOf(expanded)
            internal set

        var layout by mutableStateOf(layout)
            internal set

        /**
         * Фото и видео, которые ещё добавляются: заглушки после готовых [photoIds].
         * В документ не попадают — только готовые.
         */
        val pending = mutableStateListOf<PendingMedia>()

        /** Всего в блоке, вместе с теми, что ещё добавляются. */
        val itemCount: Int get() = photoIds.size + pending.size

        /** Одно фото или видео — только у него можно менять размер и положение. */
        val isSingle: Boolean get() = itemCount == 1

        val isGrid: Boolean get() = itemCount > 1

        /** Много фото и видео — блок свёрнут в карточку «Галерея». */
        val isGallery: Boolean get() = itemCount >= DocumentBlock.GALLERY_MIN && !expanded

        /** Блок того размера, что может быть галереей (свёрнутой или развёрнутой). */
        val canBeGallery: Boolean get() = itemCount >= DocumentBlock.GALLERY_MIN
    }

    /** Фото или видео, которое сейчас добавляется: [progress] от 0 до 1, null — ждёт очереди. */
    class PendingMedia internal constructor(val id: Long) {
        var progress by mutableStateOf<Float?>(null)
            internal set
    }

    /** Добавление нескольких фото: блок, куда они встанут, и заглушка под каждое. */
    class ImportHandle internal constructor(val blockKey: Long, val items: List<PendingMedia>)

    private var nextKey = 0L
    private var nextPendingId = 0L

    /**
     * Пропорции (ширина / высота) каждого фото по id. Храним у фото, а не у блока:
     * если из сетки удалить первое фото, оставшееся должно взять свои пропорции,
     * а не чужие — иначе оно выглядит растянутым.
     */
    private val aspects = mutableStateMapOf<String, Float>()

    /** Пропорции фото; пока размеры не загружены — 4:3, чтобы место под фото не прыгало сильно. */
    fun aspectOf(photoId: String): Float = aspects[photoId] ?: DEFAULT_ASPECT

    /** Длительность видео по id; у фото — null. */
    private val durations = mutableStateMapOf<String, Long>()

    fun durationOf(photoId: String): Long? = durations[photoId]

    val blocks = mutableStateListOf<Block>()

    /** Текстовый блок, где последним стоял курсор; null — ещё нигде (вставка пойдёт в конец). */
    var activeTextKey by mutableStateOf<Long?>(null)
        private set

    /** Фото-блок, у которого открыта панель действий. */
    var selectedPhotoKey by mutableStateOf<Long?>(null)
        private set

    /** Какое фото внутри выделенного блока выбрано (важно для сетки: его удалит «корзина»). */
    var selectedPhotoIndex by mutableIntStateOf(0)
        private set

    /**
     * Блок, где курсор на самом деле. Не то же, что [activeTextKey]: после вставки фото тот
     * уже указывает на новый блок под фото, а курсор ещё в старом — и новое поле при появлении
     * сообщает «без фокуса». Раньше это гасило [hasFocus]: пропадала панель над клавиатурой,
     * курсор не переходил под фото, а «назад» не убирал курсор.
     * Объявлен до init: [load] вызывается при создании и его сбрасывает.
     */
    private var focusedKey by mutableStateOf<Long?>(null)

    init {
        // Не load(): поля истории отмены объявлены ниже и ещё не созданы
        replaceDocument(markdown)
    }

    /** Текущий документ в виде Markdown. Чтение отслеживается Compose — подходит для snapshotFlow. */
    val markdown: String
        get() = EditorDocument.serialize(
            EditorDocument.normalize(
                blocks.mapNotNull {
                    when (it) {
                        is TextBlock -> DocumentBlock.Text(it.value.text)
                        // Блок, где пока только заглушки, в документ не пишем
                        is PhotoBlock -> it.photoIds.takeIf { ids -> ids.isNotEmpty() }?.let { ids ->
                            DocumentBlock.Photos(ids, it.widthPercent, it.align, it.expanded, it.layout)
                        }
                    }
                },
            ),
        )

    val photoIds: List<String> get() = blocks.filterIsInstance<PhotoBlock>().flatMap { it.photoIds }

    val isEmpty: Boolean get() = blocks.all { it is TextBlock && it.value.text.isBlank() }

    /** Заменить документ целиком (при загрузке записи). История отмены начинается заново. */
    fun load(markdown: String) {
        replaceDocument(markdown)
        undoStack.clear()
        redoStack.clear()
        updateHistoryFlags()
        lastEdit = null
    }

    private fun replaceDocument(markdown: String) {
        blocks.clear()
        EditorDocument.parse(markdown).forEach { block ->
            blocks += when (block) {
                is DocumentBlock.Text -> textBlock(block.text)
                is DocumentBlock.Photos ->
                    PhotoBlock(nextKey++, block.photoIds, block.widthPercent, block.align, block.expanded, block.layout)
            }
        }
        activeTextKey = null
        selectedPhotoKey = null
        focusedKey = null
    }

    /** Подставить реальные пропорции фото, когда они загрузились из базы. */
    fun applyPhotoSizes(photos: Map<String, Photo>) {
        photos.forEach { (id, photo) ->
            aspects[id] = photo.aspect
            photo.durationMs?.let { durations[id] = it }
        }
    }

    // Текст

    /** Ввод в текстовом блоке. Enter в списке продолжает список. */
    fun onTextChange(block: TextBlock, value: TextFieldValue) {
        val new = MarkdownFormatting.continueList(block.value, value)
        if (new.text != block.value.text) recordTyping(block, block.value.text, new.text)
        block.edit(new)
    }

    /** Клавиатура заменила слово (Т9) — запомнить, чтобы его можно было вернуть. */
    fun onAutoCorrect(block: TextBlock, correction: AutoCorrection) {
        block.pendingCorrection = correction
    }

    /** Вернуть слово, набранное до замены Т9. */
    fun revertCorrection(block: TextBlock, correction: AutoCorrection) {
        record(EditKind.Other, block.key)
        block.revert(correction)
    }

    /** Стоит ли сейчас курсор в каком-нибудь текстовом блоке. */
    val hasFocus: Boolean get() = focusedKey != null

    fun onTextFocusChanged(block: TextBlock, focused: Boolean) {
        if (focused) {
            activeTextKey = block.key
            selectedPhotoKey = null
            focusedKey = block.key
        } else if (focusedKey == block.key) {
            focusedKey = null
        }
    }

    /** Применить действие панели форматирования к блоку с курсором. */
    fun format(transform: (TextFieldValue) -> TextFieldValue) {
        val block = activeTextBlock() ?: return
        val new = transform(block.value)
        if (new.text != block.value.text) record(EditKind.Other, block.key)
        block.edit(new)
    }

    /** Открыто окно ссылки. Его показывает редактор: панель исчезает вместе с клавиатурой. */
    var linkDialogOpen by mutableStateOf(false)

    /** Поставить курсор в конец последнего текстового блока (тап по пустому месту под текстом). */
    fun focusEnd() {
        selectPhoto(null)
        val last = blocks.lastOrNull() as? TextBlock ?: return
        last.value = last.value.copy(selection = TextRange(last.value.text.length))
        last.focusRequester.requestFocus()
    }

    /** Блок с курсором, а если его не было — последний текстовый. */
    fun activeTextBlock(): TextBlock? =
        blocks.firstOrNull { it.key == activeTextKey } as? TextBlock
            ?: blocks.lastOrNull() as? TextBlock

    // Фото

    /**
     * Вставить фото после строки с курсором (слово посередине не разрезается):
     * текст делится на «до» и «после», фото встают между ними,
     * курсор — в начало текста после них. Несколько фото сразу — одной сеткой.
     */
    fun insertPhotos(photos: List<Photo>): TextBlock? {
        val gallery = galleryTargetKey?.let(::photo)
        galleryTargetKey = null
        if (photos.isEmpty()) return null
        if (gallery != null) {
            // Добавляли из окна альбома — в конец этого альбома, а не новым блоком
            rememberSizes(photos)
            gallery.photoIds = gallery.photoIds + photos.map { it.id }
            return null
        }
        val target = activeTextBlock() ?: return null
        forgetHistory()
        val index = blocks.indexOf(target)
        val value = target.value
        val cursor = value.selection.max.coerceIn(0, value.text.length)
        val cut = value.text.indexOf('\n', cursor).let { if (it < 0) value.text.length else it }
        val before = value.text.substring(0, cut).removeSuffix("\n")
        val after = value.text.substring(cut).removePrefix("\n")

        target.value = TextFieldValue(before, TextRange(before.length))
        val photoBlock = PhotoBlock(
            key = nextKey++,
            photoIds = photos.map { it.id },
            widthPercent = DocumentBlock.FULL_WIDTH,
            align = PhotoAlign.Center,
        )
        rememberSizes(photos)
        val caret = textBlock(after)
        blocks.addAll(index + 1, listOf(photoBlock, caret))
        caret.value = caret.value.copy(selection = TextRange(0))
        activeTextKey = caret.key
        focusRequestKey = caret.key
        return caret
    }

    private fun rememberSizes(photos: List<Photo>) {
        photos.forEach { photo ->
            aspects[photo.id] = photo.aspect
            photo.durationMs?.let { durations[photo.id] = it }
        }
    }

    /**
     * Галерея, в которую попадут следующие выбранные фото (кнопка «добавить» в её окне).
     * Сбрасывается при следующей вставке — в том числе пустой, если выбор отменили.
     */
    var galleryTargetKey by mutableStateOf<Long?>(null)
        private set

    fun addToGalleryNext(key: Long?) {
        galleryTargetKey = key
    }

    /** Развернуть галерею в сетку или свернуть обратно в карточку. */
    fun setPhotoExpanded(key: Long, expanded: Boolean) {
        val block = photo(key) ?: return
        if (block.expanded == expanded) return
        block.expanded = expanded
    }

    /**
     * Начать добавление [count] фото и видео: сразу поставить по заглушке на каждое — в галерею
     * из [galleryTargetKey] или новым блоком после строки с курсором (как [insertPhotos]).
     * Готовые встают на место заглушек по одному — [finishImport].
     */
    fun beginImport(count: Int): ImportHandle? {
        val gallery = galleryTargetKey?.let(::photo)
        galleryTargetKey = null
        if (count <= 0) return null
        val items = List(count) { PendingMedia(nextPendingId++) }
        if (gallery != null) {
            gallery.pending += items
            return ImportHandle(gallery.key, items)
        }
        val target = activeTextBlock() ?: return null
        forgetHistory()
        val index = blocks.indexOf(target)
        val value = target.value
        val cursor = value.selection.max.coerceIn(0, value.text.length)
        val cut = value.text.indexOf('\n', cursor).let { if (it < 0) value.text.length else it }
        val before = value.text.substring(0, cut).removeSuffix("\n")
        val after = value.text.substring(cut).removePrefix("\n")

        target.value = TextFieldValue(before, TextRange(before.length))
        val photoBlock = PhotoBlock(nextKey++, emptyList(), DocumentBlock.FULL_WIDTH, PhotoAlign.Center)
        photoBlock.pending += items
        val caret = textBlock(after)
        blocks.addAll(index + 1, listOf(photoBlock, caret))
        caret.value = caret.value.copy(selection = TextRange(0))
        activeTextKey = caret.key
        focusRequestKey = caret.key
        return ImportHandle(photoBlock.key, items)
    }

    fun setImportProgress(item: PendingMedia, progress: Float) {
        item.progress = progress.coerceIn(0f, 1f)
    }

    /**
     * Заглушку [item] заменить готовым [photo] (null — добавить не вышло, заглушка просто уходит).
     * Блок, где не осталось ни фото, ни заглушек, пропадает.
     */
    fun finishImport(handle: ImportHandle, item: PendingMedia, photo: Photo?) {
        val index = blocks.indexOfFirst { it.key == handle.blockKey }
        val block = blocks.getOrNull(index) as? PhotoBlock ?: return
        block.pending.remove(item)
        if (photo != null) {
            rememberSizes(listOf(photo))
            block.photoIds = block.photoIds + photo.id
        }
        if (block.itemCount == 0 && index > 0) {
            forgetHistory()
            dropPhotoBlock(index, block.key)
        }
    }

    /** Раскладка блока из нескольких фото. */
    fun setPhotoLayout(key: Long, layout: PhotoLayout) {
        val block = photo(key) ?: return
        if (block.layout == layout) return
        block.layout = layout
    }

    /** Убрать из блока несколько фото сразу (выбранные в окне галереи) — одним шагом отмены. */
    fun removePhotosAt(key: Long, indices: Set<Int>) {
        val block = photo(key) ?: return
        if (indices.isEmpty()) return
        val left = block.photoIds.filterIndexed { i, _ -> i !in indices }
        if (left.isEmpty() && block.pending.isEmpty()) return removePhoto(key)
        block.photoIds = left
        selectedPhotoIndex = selectedPhotoIndex.coerceAtMost((left.size - 1).coerceAtLeast(0))
    }

    /** Блок, в который нужно перевести фокус (выполняет RichEditor). */
    var focusRequestKey by mutableStateOf<Long?>(null)
        private set

    fun consumeFocusRequest() {
        focusRequestKey = null
    }

    /** Тап по фото: открывает панель, повторный тап по тому же фото — закрывает. */
    fun togglePhoto(key: Long, index: Int = 0) {
        if (selectedPhotoKey == key && selectedPhotoIndex == index) {
            selectedPhotoKey = null
        } else {
            selectPhoto(key, index)
        }
    }

    /**
     * Видео, которое играет прямо в тексте (мини-плеер); null — ни одно.
     * В документ и историю правок не входит.
     */
    var playingVideoId by mutableStateOf<String?>(null)
        private set

    /** Мини-плеер на паузе. */
    var videoPaused by mutableStateOf(false)
        private set

    /** Нажали ▶ на видео: запустить его (другое остановится), а у играющего — пауза/дальше. */
    fun tapPlay(photoId: String) {
        if (playingVideoId == photoId) {
            videoPaused = !videoPaused
        } else {
            playingVideoId = photoId
            videoPaused = false
        }
    }

    fun pauseVideo() {
        videoPaused = true
    }

    fun stopVideo() {
        playingVideoId = null
        videoPaused = false
    }

    fun selectPhoto(key: Long?, index: Int = 0) {
        selectedPhotoKey = key
        selectedPhotoIndex = index
    }

    fun setPhotoAlign(key: Long, align: PhotoAlign) {
        val block = photo(key) ?: return
        if (block.align == align) return
        block.align = align
    }

    fun setPhotoWidth(key: Long, percent: Int) {
        val block = photo(key) ?: return
        val width = percent.coerceIn(DocumentBlock.MIN_WIDTH, DocumentBlock.FULL_WIDTH)
        if (block.widthPercent == width) return
        block.widthPercent = width
    }

    /** Удалить одно фото из блока; если оно было последним — весь блок. */
    fun removePhotoAt(key: Long, index: Int) {
        val block = photo(key) ?: return
        if (block.photoIds.size <= 1 && block.pending.isEmpty()) return removePhoto(key)
        block.photoIds = block.photoIds.filterIndexed { i, _ -> i != index }
        selectedPhotoIndex = selectedPhotoIndex.coerceAtMost(block.photoIds.lastIndex)
    }

    /** Удалить фото-блок целиком; текст до и после него склеивается в один абзац-блок. */
    fun removePhoto(key: Long) {
        val index = blocks.indexOfFirst { it.key == key }
        if (index <= 0) return
        forgetHistory()
        dropPhotoBlock(index, key)
    }

    private fun dropPhotoBlock(index: Int, key: Long) {
        val before = blocks[index - 1] as TextBlock
        val after = blocks[index + 1] as TextBlock
        val caret = before.value.text.length
        before.value = TextFieldValue(EditorDocument.joinParagraphs(before.value.text, after.value.text), TextRange(caret))
        blocks.removeRange(index, index + 2)
        if (selectedPhotoKey == key) selectedPhotoKey = null
        if (activeTextKey == after.key) activeTextKey = before.key
        // Блок с курсором исчез — его поле уже не скажет, что потеряло фокус
        if (focusedKey == after.key) focusedKey = null
    }

    /** Убрать фото [ids] из документа — их удалили из галереи. Опустевшие блоки пропадают. */
    fun removePhotos(ids: Set<String>) {
        // Шаги истории ссылаются на эти фото — вернуть их было бы нечем
        if (photoIds.any(ids::contains)) forgetHistory()
        blocks.filterIsInstance<PhotoBlock>()
            .filter { block -> block.photoIds.any(ids::contains) }
            .forEach { block ->
                val left = block.photoIds.filterNot(ids::contains)
                // Фото удалили из галереи — в истории отмены это не шаг, вернуть его нельзя
                val index = blocks.indexOf(block)
                if (left.isEmpty()) { if (index > 0) dropPhotoBlock(index, block.key) } else block.photoIds = left
            }
    }

    fun canMovePhoto(key: Long, up: Boolean): Boolean {
        val index = blocks.indexOfFirst { it.key == key }
        if (index <= 0) return false
        val neighbour = blocks[if (up) index - 1 else index + 1] as TextBlock
        return neighbour.value.text.isNotEmpty() || blocks.getOrNull(if (up) index - 2 else index + 2) is PhotoBlock
    }

    /**
     * Сдвинуть фото на один абзац вверх или вниз.
     * Если соседний текст пуст и за ним другое фото — фото меняются местами.
     */
    fun movePhoto(key: Long, up: Boolean) {
        val index = blocks.indexOfFirst { it.key == key }
        if (index <= 0 || !canMovePhoto(key, up)) return
        forgetHistory()
        val above = blocks[index - 1] as TextBlock
        val below = blocks[index + 1] as TextBlock

        if (up) {
            val text = above.value.text
            if (text.isEmpty()) return swapPhotos(index, index - 2)
            val split = text.lastIndexOf('\n')
            val paragraph = text.substring(split + 1)
            above.value = TextFieldValue(if (split < 0) "" else text.substring(0, split))
            below.value = TextFieldValue(EditorDocument.joinParagraphs(paragraph, below.value.text))
        } else {
            val text = below.value.text
            if (text.isEmpty()) return swapPhotos(index, index + 2)
            val split = text.indexOf('\n')
            val paragraph = if (split < 0) text else text.substring(0, split)
            below.value = TextFieldValue(if (split < 0) "" else text.substring(split + 1))
            above.value = TextFieldValue(EditorDocument.joinParagraphs(above.value.text, paragraph))
        }
    }

    private fun swapPhotos(a: Int, b: Int) {
        val first = blocks[a]
        blocks[a] = blocks[b]
        blocks[b] = first
    }

    private fun photo(key: Long) = blocks.firstOrNull { it.key == key } as? PhotoBlock

    // Отмена и повтор

    /** Состояние документа для истории: текст, в каком текстовом блоке курсор и где. */
    /**
     * Шаг истории — только текст: тексты блоков по порядку и где был курсор. Фото и видео
     * отмена не трогает (не возвращает удалённые и не меняет вид) — поэтому, когда меняется
     * сам порядок блоков (фото вставили, убрали, передвинули), история начинается заново.
     */
    private class Snapshot(val keys: List<Long>, val texts: List<String>, val blockIndex: Int, val selection: TextRange)

    private enum class EditKind { Insert, Delete, Other }

    /** Последняя правка: следующая той же природы в том же блоке вскоре после неё — тот же шаг. */
    private class LastEdit(val kind: EditKind, val blockKey: Long?, val at: Long)

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var lastEdit: LastEdit? = null

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /**
     * Набор и удаление текста: один шаг — одно слово. Пробел или перевод строки закрывают шаг,
     * пауза дольше [GROUP_MILLIS] тоже. Удаление выделенного или вставка куска — отдельный шаг.
     */
    private fun recordTyping(block: TextBlock, old: String, new: String) {
        val diff = new.length - old.length
        val kind = when {
            diff == 1 -> EditKind.Insert
            diff == -1 -> EditKind.Delete
            else -> EditKind.Other
        }
        record(kind, block.key)
        val changed = if (diff > 0) changedChars(old, new) else changedChars(new, old)
        if (kind == EditKind.Other || changed.any(Char::isWhitespace)) lastEdit = null
    }

    /** Что вставлено в [shorter], чтобы получился [longer] (или удалено из longer). */
    private fun changedChars(shorter: String, longer: String): String {
        val prefix = shorter.commonPrefixWith(longer).length
        val suffix = shorter.substring(prefix).commonSuffixWith(longer.substring(prefix)).length
        return longer.substring(prefix, longer.length - suffix)
    }

    /** Перед правкой: сохранить состояние, если это начало нового шага. */
    private fun record(kind: EditKind, blockKey: Long?) {
        val now = clock()
        val last = lastEdit
        val continues = last != null && kind != EditKind.Other && last.kind == kind &&
            last.blockKey == blockKey && now - last.at < GROUP_MILLIS
        if (!continues) {
            undoStack.addLast(capture())
            if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        }
        lastEdit = if (kind == EditKind.Other) null else LastEdit(kind, blockKey, now)
        redoStack.clear()
        updateHistoryFlags()
    }

    /** Порядок блоков изменился — прежние шаги к нему уже не подходят. */
    private fun forgetHistory() {
        undoStack.clear()
        redoStack.clear()
        lastEdit = null
        updateHistoryFlags()
    }

    fun undo() = step(from = undoStack, to = redoStack)

    fun redo() = step(from = redoStack, to = undoStack)

    private fun step(from: ArrayDeque<Snapshot>, to: ArrayDeque<Snapshot>) {
        val target = from.removeLastOrNull() ?: return
        to.addLast(capture())
        restore(target)
        lastEdit = null
        updateHistoryFlags()
    }

    private fun capture(): Snapshot {
        val block = activeTextBlock()
        return Snapshot(
            keys = blocks.map { it.key },
            texts = blocks.filterIsInstance<TextBlock>().map { it.value.text },
            blockIndex = block?.let(blocks::indexOf) ?: -1,
            selection = block?.value?.selection ?: TextRange.Zero,
        )
    }

    private fun restore(snapshot: Snapshot) {
        val hadFocus = hasFocus
        // Порядок блоков мог стать другим только вместе со сбросом истории — на всякий случай
        // текст возвращаем, лишь если блоки те же
        if (snapshot.keys != blocks.map { it.key }) return
        blocks.filterIsInstance<TextBlock>().zip(snapshot.texts).forEach { (block, text) ->
            if (block.value.text != text) block.value = TextFieldValue(text)
        }
        val block = blocks.getOrNull(snapshot.blockIndex) as? TextBlock ?: blocks.lastOrNull() as? TextBlock ?: return
        val length = block.value.text.length
        block.value = block.value.copy(
            selection = TextRange(snapshot.selection.start.coerceIn(0, length), snapshot.selection.end.coerceIn(0, length)),
        )
        activeTextKey = block.key
        if (hadFocus) {
            // Старые поля исчезли вместе с фокусом — курсор переходит в восстановленный блок
            focusedKey = block.key
            focusRequestKey = block.key
        }
    }

    private fun updateHistoryFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun textBlock(text: String) = TextBlock(nextKey++, TextFieldValue(text, TextRange(text.length)))

    private companion object {
        const val DEFAULT_ASPECT = 4f / 3f

        /** Пауза в наборе, после которой начинается новый шаг отмены. */
        const val GROUP_MILLIS = 1_500L

        /** Сколько шагов назад можно отменить. */
        const val MAX_HISTORY = 200
    }
}

private val Photo.aspect: Float get() = if (height == 0) 1f else width.toFloat() / height
