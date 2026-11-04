package dev.chaik.remember.core.model

import java.text.DecimalFormat
import java.time.LocalDate
import java.time.LocalTime

/**
 * Тренировка в зале. Номер не хранится — он считается по порядку дат ([number]),
 * поэтому импорт старых тренировок сам расставляет номера.
 */
data class Workout(
    val id: Long = 0,
    /** Сквозной номер по хронологии, начиная с 1. */
    val number: Int = 0,
    val date: LocalDate,
    val start: LocalTime? = null,
    /** null — ещё идёт или время конца не записано. */
    val end: LocalTime? = null,
    /** Вес тела до и после, кг; оба необязательные. */
    val weightBefore: Double? = null,
    val weightAfter: Double? = null,
    val note: String = "",
    /** Идёт прямо сейчас (начата кнопкой и ещё не закончена). */
    val active: Boolean = false,
    /** Перенесена из файла. */
    val imported: Boolean = false,
    val exercises: List<WorkoutExercise> = emptyList(),
)

/** Упражнение внутри тренировки — с подходами по порядку. */
data class WorkoutExercise(
    val id: Long = 0,
    val exercise: Exercise,
    val sets: List<WorkoutSet> = emptyList(),
)

/** Упражнение из справочника: название и как записываются его подходы. */
data class Exercise(
    val id: Long = 0,
    val name: String,
    val kind: ExerciseKind = ExerciseKind.WEIGHT_REPS,
    /** Заметка: настройки тренажёра, техника. Видна в карточке упражнения в тренировке. */
    val note: String = "",
) {
    /** Подходы этого вида записываются со временем — к ним есть секундомер. */
    val timed: Boolean get() = kind in TimedKinds
}

private val TimedKinds = setOf(ExerciseKind.TIME, ExerciseKind.SPEED_TIME, ExerciseKind.DISTANCE_TIME, ExerciseKind.WEIGHT_TIME)

/**
 * Как записываются подходы: «40×12», «30», «1:30», «9,4 км/ч × 10:00» (бег на дорожке),
 * «5 км × 28:00» (бег, вело), «20 × 1:00» (вес на время: удержание, вис) или «1,5 км» (плавание).
 * Код хранится в базе — менять коды нельзя.
 */
enum class ExerciseKind(val code: String) {
    WEIGHT_REPS("WR"),
    REPS("R"),
    TIME("T"),
    SPEED_TIME("ST"),
    DISTANCE_TIME("DT"),
    WEIGHT_TIME("WT"),
    DISTANCE("D"),
    ;

    companion object {
        fun fromCode(code: String): ExerciseKind = entries.firstOrNull { it.code == code } ?: WEIGHT_REPS
    }
}

/** Подход. Заполнены те поля, что нужны виду упражнения; пустое — «не записано». */
data class WorkoutSet(
    val id: Long = 0,
    /** Вес снаряда, кг, можно с дробью (17,5). */
    val weight: Double? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    /** Скорость, км/ч — для бега. */
    val speed: Double? = null,
    /** Дистанция, км, можно с дробью (0,4). */
    val distance: Double? = null,
) {
    /**
     * Как подход выглядит в записи: «40 × 12», «30», «1:30», «9,4 км/ч × 10:00», «5 км × 28:00».
     * [speedUnit] и [distanceUnit] — подписи на языке приложения (strings.xml, unit_kmh и unit_km).
     */
    fun format(kind: ExerciseKind, speedUnit: String = "km/h", distanceUnit: String = "km"): String = when (kind) {
        ExerciseKind.WEIGHT_REPS -> "${weight?.let(::formatKg) ?: "—"} × ${reps ?: "—"}"
        ExerciseKind.REPS -> reps?.toString() ?: "—"
        ExerciseKind.TIME -> seconds?.let(::formatDuration) ?: "—"
        ExerciseKind.SPEED_TIME -> listOfNotNull(speed?.let { "${formatKg(it)} $speedUnit" }, seconds?.let(::formatDuration))
            .joinToString(" × ")
            .ifEmpty { "—" }
        ExerciseKind.DISTANCE_TIME -> listOfNotNull(distance?.let { "${formatKg(it)} $distanceUnit" }, seconds?.let(::formatDuration))
            .joinToString(" × ")
            .ifEmpty { "—" }
        ExerciseKind.WEIGHT_TIME -> "${weight?.let(::formatKg) ?: "—"} × ${seconds?.let(::formatDuration) ?: "—"}"
        ExerciseKind.DISTANCE -> distance?.let { "${formatKg(it)} $distanceUnit" } ?: "—"
    }
}

/** Шаблон тренировки: заготовленный список упражнений, без подходов. */
data class WorkoutTemplate(
    val id: Long = 0,
    val name: String,
    val exercises: List<Exercise> = emptyList(),
)

/**
 * Какие упражнения добавить в тренировку: шаблоны стыкуются по порядку выбора.
 * Упражнение, которое уже есть в тренировке или встретилось в прошлом шаблоне, второй раз не добавляется.
 */
fun stackTemplates(templates: List<List<Long>>, present: Collection<Long>): List<Long> =
    templates.flatten().distinct().filterNot { it in present }

/** Упражнения, которые при добавлении шаблонов оказались повторами: уже были в тренировке или встретились дважды. */
fun repeatedInTemplates(templates: List<List<Long>>, present: Collection<Long>): Set<Long> {
    val all = templates.flatten()
    return all.filter { id -> id in present || all.count { it == id } > 1 }.toSet()
}

/** Тренировки, где есть упражнение с таким текстом в названии; пустой запрос — все. */
fun List<WorkoutSummary>.withExercise(query: String): List<WorkoutSummary> {
    val text = query.trim().lowercase()
    if (text.isEmpty()) return this
    return filter { summary -> summary.exerciseNames.any { it.lowercase().contains(text) } }
}

/** Краткая строка для списка тренировок. */
data class WorkoutSummary(
    val id: Long,
    val number: Int,
    val date: LocalDate,
    val start: LocalTime?,
    val end: LocalTime?,
    val weightBefore: Double?,
    val weightAfter: Double?,
    val active: Boolean,
    val imported: Boolean,
    val exerciseNames: List<String>,
)

/** «40», «17,5», «65,75». */
fun formatKg(value: Double): String = DecimalFormat("0.##").format(value)

/** «1:30», «12:05», для часа и больше — «1:02:03». */
fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** «1:30» или «90» → 90 секунд; «2:05:00» тоже понимается. null — не разобрать. */
fun parseDuration(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.isEmpty() || parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
    val numbers = parts.map { it.toInt() }
    return numbers.fold(0) { acc, n -> acc * 60 + n }.takeIf { it > 0 }
}

/** «17,5» и «17.5» → 17.5. */
fun parseKg(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
