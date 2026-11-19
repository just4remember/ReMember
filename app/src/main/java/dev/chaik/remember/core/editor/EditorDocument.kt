package dev.chaik.remember.core.editor

/** Положение фото по горизонтали, когда оно уже экрана. */
enum class PhotoAlign(val code: String) {
    Start("left"),
    Center("center"),
    End("right"),
    ;

    companion object {
        fun fromCode(code: String?): PhotoAlign = entries.firstOrNull { it.code == code } ?: Center
    }
}

/** Как показан блок из нескольких фото: сеткой, столбиком одно под другим или лентой со свайпом. */
enum class PhotoLayout(val code: String) {
    Grid("grid"),
    Column("column"),
    Row("row"),
    ;

    companion object {
        fun fromCode(code: String?): PhotoLayout = entries.firstOrNull { it.code == code } ?: Grid
    }
}

/**
 * Документ редактора — чередование текста и фото:
 * `текст, фото, текст, фото, …, текст`. Текстовый блок может быть пустым,
 * поэтому между двумя фото всегда есть место, куда поставить курсор.
 *
 * Фото-блок — одно фото или сетка из нескольких (если выбрали несколько сразу).
 *
 * В базе документ хранится как Markdown. Фото-блок — отдельная строка из картинок:
 * `![](photo:<id>?w=75&a=left) ![](photo:<id2>)`. Параметры — у первой картинки:
 * `w` — ширина в процентах, `a` — положение (left / center / right),
 * `e=1` — блок из [GALLERY_MIN] и больше фото показан сеткой, а не свёрнут в карточку «Галерея»,
 * `l` — раскладка нескольких фото ([PhotoLayout]: grid / column / row).
 */
sealed interface DocumentBlock {
    data class Text(val text: String) : DocumentBlock

    data class Photos(
        val photoIds: List<String>,
        val widthPercent: Int = FULL_WIDTH,
        val align: PhotoAlign = PhotoAlign.Center,
        /** Развёрнут в сетку; иначе блок из [GALLERY_MIN]+ фото — карточка «Галерея». */
        val expanded: Boolean = false,
        val layout: PhotoLayout = PhotoLayout.Grid,
    ) : DocumentBlock {
        init {
            require(photoIds.isNotEmpty()) { "Фото-блок без фото" }
        }
    }

    companion object {
        const val FULL_WIDTH = 100
        const val MIN_WIDTH = 30

        /** С какого числа фото и видео блок сворачивается в карточку «Галерея». */
        const val GALLERY_MIN = 4
    }
}

object EditorDocument {
    private const val UUID = """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"""
    private const val IMAGE = """!\[]\(photo:$UUID(?:\?[^)\s]*)?\)"""
    private val photoLine = Regex("""(?m)^$IMAGE(?: $IMAGE)*$""")
    private val image = Regex("""!\[]\(photo:($UUID)(?:\?([^)\s]*))?\)""")

    /** Markdown → блоки. Результат всегда начинается и заканчивается текстом. */
    fun parse(markdown: String): List<DocumentBlock> {
        val blocks = mutableListOf<DocumentBlock>()
        var cursor = 0
        for (match in photoLine.findAll(markdown)) {
            blocks += DocumentBlock.Text(markdown.substring(cursor, match.range.first).dropTrailingNewline())
            blocks += parsePhotos(match.value)
            cursor = match.range.last + 1
            // Отрезаем перевод строки после фото — он принадлежит разметке, а не тексту
            if (markdown.startsWith("\n", cursor)) cursor++
        }
        blocks += DocumentBlock.Text(markdown.substring(cursor))
        return blocks
    }

    /** Блоки → Markdown. Обратная к [parse]: parse(serialize(x)) == x. */
    fun serialize(blocks: List<DocumentBlock>): String = buildString {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is DocumentBlock.Text -> append(block.text)
                is DocumentBlock.Photos -> {
                    // Фото всегда на своей строке
                    if (index > 0) append('\n')
                    block.photoIds.forEachIndexed { i, id ->
                        if (i > 0) append(' ')
                        append("![](photo:").append(id)
                        if (i == 0) append(params(block))
                        append(')')
                    }
                    if (index < blocks.lastIndex) append('\n')
                }
            }
        }
    }

    /**
     * Приводит список к виду «текст, фото, текст, …, текст»:
     * склеивает соседние тексты и добавляет пустые тексты вокруг фото.
     */
    fun normalize(blocks: List<DocumentBlock>): List<DocumentBlock> {
        val result = mutableListOf<DocumentBlock>()
        for (block in blocks) {
            val last = result.lastOrNull()
            when {
                block is DocumentBlock.Text && last is DocumentBlock.Text ->
                    result[result.lastIndex] = DocumentBlock.Text(joinParagraphs(last.text, block.text))
                block is DocumentBlock.Photos && last !is DocumentBlock.Text ->
                    result += listOf(DocumentBlock.Text(""), block)
                else -> result += block
            }
        }
        if (result.lastOrNull() !is DocumentBlock.Text) result += DocumentBlock.Text("")
        return result
    }

    /**
     * Убрать фото [photoIds] из текста. Из сетки уходят только они; опустевший фото-блок
     * пропадает целиком, а текст до и после него склеивается.
     */
    fun removePhotos(markdown: String, photoIds: Set<String>): String {
        val blocks = parse(markdown)
        if (blocks.none { it is DocumentBlock.Photos && it.photoIds.any(photoIds::contains) }) return markdown
        val kept = blocks.mapNotNull { block ->
            if (block !is DocumentBlock.Photos) return@mapNotNull block
            val left = block.photoIds.filterNot(photoIds::contains)
            if (left.isEmpty()) null else block.copy(photoIds = left)
        }
        return serialize(normalize(kept))
    }

    /** Склеить два куска текста, сохранив границу абзаца. */
    fun joinParagraphs(first: String, second: String): String = when {
        first.isEmpty() -> second
        second.isEmpty() -> first
        else -> first + "\n" + second
    }

    private fun parsePhotos(line: String): DocumentBlock.Photos {
        val images = image.findAll(line).toList()
        val params = images.first().groupValues[2]
            .split('&')
            .mapNotNull { it.split('=', limit = 2).takeIf { kv -> kv.size == 2 } }
            .associate { (k, v) -> k to v }
        return DocumentBlock.Photos(
            photoIds = images.map { it.groupValues[1] },
            widthPercent = params["w"]?.toIntOrNull()
                ?.coerceIn(DocumentBlock.MIN_WIDTH, DocumentBlock.FULL_WIDTH)
                ?: DocumentBlock.FULL_WIDTH,
            align = PhotoAlign.fromCode(params["a"]),
            expanded = params["e"] == "1",
            layout = PhotoLayout.fromCode(params["l"]),
        )
    }

    private fun params(block: DocumentBlock.Photos): String {
        val list = buildList {
            if (block.widthPercent != DocumentBlock.FULL_WIDTH) add("w=${block.widthPercent}")
            if (block.align != PhotoAlign.Center) add("a=${block.align.code}")
            if (block.expanded) add("e=1")
            if (block.layout != PhotoLayout.Grid) add("l=${block.layout.code}")
        }
        return if (list.isEmpty()) "" else list.joinToString("&", prefix = "?")
    }

    /** Перевод строки перед фото тоже принадлежит разметке. */
    private fun String.dropTrailingNewline(): String = removeSuffix("\n")
}
