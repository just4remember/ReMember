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
class EditorState(markdown: String = "") {

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
    ) : Block() {
        var photoIds by mutableStateOf(photoIds)
            internal set

        var widthPercent by mutableIntStateOf(widthPercent)
            internal set

        var align by mutableStateOf(align)
            internal set

        val isGrid: Boolean get() = photoIds.size > 1
    }

    private var nextKey = 0L

    /**
     * Пропорции (ширина / высота) каждого фото по id. Храним у фото, а не у блока:
     * если из сетки удалить первое фото, оставшееся должно взять свои пропорции,
     * а не чужие — иначе оно выглядит растянутым.
     */
    private val aspects = mutableStateMapOf<String, Float>()

    /** Пропорции фото; пока размеры не загружены — 4:3, чтобы место под фото не прыгало сильно. */
    fun aspectOf(photoId: String): Float = aspects[photoId] ?: DEFAULT_ASPECT

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
        load(markdown)
    }

    /** Текущий документ в виде Markdown. Чтение отслеживается Compose — подходит для snapshotFlow. */
    val markdown: String
        get() = EditorDocument.serialize(
            blocks.map {
                when (it) {
                    is TextBlock -> DocumentBlock.Text(it.value.text)
                    is PhotoBlock -> DocumentBlock.Photos(it.photoIds, it.widthPercent, it.align)
                }
            },
        )

    val photoIds: List<String> get() = blocks.filterIsInstance<PhotoBlock>().flatMap { it.photoIds }

    val isEmpty: Boolean get() = blocks.all { it is TextBlock && it.value.text.isBlank() }

    /** Заменить документ целиком (при загрузке записи). */
    fun load(markdown: String) {
        blocks.clear()
        EditorDocument.parse(markdown).forEach { block ->
            blocks += when (block) {
                is DocumentBlock.Text -> textBlock(block.text)
                is DocumentBlock.Photos ->
                    PhotoBlock(nextKey++, block.photoIds, block.widthPercent, block.align)
            }
        }
        activeTextKey = null
        selectedPhotoKey = null
        focusedKey = null
    }

    /** Подставить реальные пропорции фото, когда они загрузились из базы. */
    fun applyPhotoSizes(photos: Map<String, Photo>) {
        photos.forEach { (id, photo) -> aspects[id] = photo.aspect }
    }

    // Текст

    /** Ввод в текстовом блоке. Enter в списке продолжает список. */
    fun onTextChange(block: TextBlock, value: TextFieldValue) {
        block.edit(MarkdownFormatting.continueList(block.value, value))
    }

    /** Клавиатура заменила слово (Т9) — запомнить, чтобы его можно было вернуть. */
    fun onAutoCorrect(block: TextBlock, correction: AutoCorrection) {
        block.pendingCorrection = correction
    }

    /** Вернуть слово, набранное до замены Т9. */
    fun revertCorrection(block: TextBlock, correction: AutoCorrection) {
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
        block.edit(transform(block.value))
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
        if (photos.isEmpty()) return null
        val target = activeTextBlock() ?: return null
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
        photos.forEach { aspects[it.id] = it.aspect }
        val caret = textBlock(after)
        blocks.addAll(index + 1, listOf(photoBlock, caret))
        caret.value = caret.value.copy(selection = TextRange(0))
        activeTextKey = caret.key
        focusRequestKey = caret.key
        return caret
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

    fun selectPhoto(key: Long?, index: Int = 0) {
        selectedPhotoKey = key
        selectedPhotoIndex = index
    }

    fun setPhotoAlign(key: Long, align: PhotoAlign) {
        photo(key)?.align = align
    }

    fun setPhotoWidth(key: Long, percent: Int) {
        photo(key)?.widthPercent = percent.coerceIn(DocumentBlock.MIN_WIDTH, DocumentBlock.FULL_WIDTH)
    }

    /** Удалить одно фото из блока; если оно было последним — весь блок. */
    fun removePhotoAt(key: Long, index: Int) {
        val block = photo(key) ?: return
        if (block.photoIds.size <= 1) return removePhoto(key)
        block.photoIds = block.photoIds.filterIndexed { i, _ -> i != index }
        selectedPhotoIndex = selectedPhotoIndex.coerceAtMost(block.photoIds.lastIndex)
    }

    /** Удалить фото-блок целиком; текст до и после него склеивается в один абзац-блок. */
    fun removePhoto(key: Long) {
        val index = blocks.indexOfFirst { it.key == key }
        if (index <= 0) return
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
        blocks.filterIsInstance<PhotoBlock>()
            .filter { block -> block.photoIds.any(ids::contains) }
            .forEach { block ->
                val left = block.photoIds.filterNot(ids::contains)
                if (left.isEmpty()) removePhoto(block.key) else block.photoIds = left
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

    private fun textBlock(text: String) = TextBlock(nextKey++, TextFieldValue(text, TextRange(text.length)))

    private companion object {
        const val DEFAULT_ASPECT = 4f / 3f
    }
}

private val Photo.aspect: Float get() = if (height == 0) 1f else width.toFloat() / height
