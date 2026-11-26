package dev.chaik.remember.core.wordgame

import kotlinx.serialization.Serializable

/** Что показывает клетка с буквой после попытки. */
enum class LetterMark {
    /** Буква на своём месте. */
    HIT,

    /** Буква есть в слове, но в другом месте. */
    PRESENT,

    /** Буквы в слове нет (или все такие буквы уже отмечены). */
    MISS,
}

/**
 * «Пять букв»: угадать английское слово из [WORD_LENGTH] букв за [MAX_ATTEMPTS] попыток.
 * Каждая попытка — настоящее слово из словаря; после неё буквы подсвечиваются (см. [mark]).
 */
@Serializable
data class WordGameState(
    val answer: String,
    val guesses: List<String> = emptyList(),
    /** Набранное в текущей строке — чтобы после повторного звонка продолжить с того же места. */
    val input: String = "",
    /** У старых партий — null, то есть словарь из APK. */
    val dictionaryHash: String? = null,
) {
    val won: Boolean get() = guesses.lastOrNull() == answer
    val lost: Boolean get() = !won && guesses.size >= MAX_ATTEMPTS
    val finished: Boolean get() = won || lost

    /** Лучшее, что известно о каждой букве, — для подсветки клавиатуры. */
    fun keyMarks(): Map<Char, LetterMark> {
        val marks = mutableMapOf<Char, LetterMark>()
        for (guess in guesses) {
            mark(guess, answer).forEachIndexed { i, mark ->
                val letter = guess[i]
                val known = marks[letter]
                if (known == null || mark.ordinal < known.ordinal) marks[letter] = mark
            }
        }
        return marks
    }

    companion object {
        const val WORD_LENGTH = 5
        const val MAX_ATTEMPTS = 6

        /**
         * Подсветка попытки [guess] для слова [answer]. Сначала — буквы на своих местах, потом из
         * оставшихся букв слова — «есть в другом месте»: повторов отмечается не больше, чем их в слове.
         */
        fun mark(guess: String, answer: String): List<LetterMark> {
            val marks = MutableList(guess.length) { LetterMark.MISS }
            val left = mutableMapOf<Char, Int>()
            guess.forEachIndexed { i, letter ->
                if (answer.getOrNull(i) == letter) marks[i] = LetterMark.HIT else answer.getOrNull(i)?.let { left[it] = (left[it] ?: 0) + 1 }
            }
            guess.forEachIndexed { i, letter ->
                if (marks[i] == LetterMark.HIT) return@forEachIndexed
                val count = left[letter] ?: 0
                if (count > 0) {
                    marks[i] = LetterMark.PRESENT
                    left[letter] = count - 1
                }
            }
            return marks
        }
    }
}

/** Почему попытку не приняли. */
enum class GuessError { TOO_SHORT, NOT_A_WORD }

/** Ход игры: набор букв и попытки. Не меняет состояние, а возвращает новое. */
class WordGame(private val dictionary: WordDictionary) {

    fun type(state: WordGameState, letter: Char): WordGameState =
        if (state.finished || state.input.length >= WordGameState.WORD_LENGTH || letter !in 'a'..'z') state
        else state.copy(input = state.input + letter)

    fun erase(state: WordGameState): WordGameState =
        if (state.finished || state.input.isEmpty()) state else state.copy(input = state.input.dropLast(1))

    /** Принять набранное слово; [GuessError] — если оно не годится (тогда состояние не меняется). */
    fun submit(state: WordGameState): Pair<WordGameState, GuessError?> = when {
        state.finished -> state to null
        state.input.length < WordGameState.WORD_LENGTH -> state to GuessError.TOO_SHORT
        state.input != state.answer && !dictionary.isWord(state.input) -> state to GuessError.NOT_A_WORD
        else -> state.copy(guesses = state.guesses + state.input, input = "") to null
    }
}
