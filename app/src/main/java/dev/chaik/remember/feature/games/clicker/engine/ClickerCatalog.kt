package dev.chaik.remember.feature.games.clicker.engine

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.pow

/**
 * Что даёт карточка за каждый уровень. Уровень n (с нуля) даёт
 * [CardDef.baseValue] · valueGrowthⁿ · (1 + valueStep · n): каждый следующий уровень приносит
 * немного больше предыдущего. Эффекты всех купленных уровней складываются.
 */
@Serializable
enum class CardEffect {
    /** Пассивный доход, коинов в час. */
    @SerialName("income") INCOME,

    /** Коинов за один тап (постоянная прибавка). */
    @SerialName("tap") TAP,

    /**
     * Сила тапа: тап приносит ещё столько секунд пассивного дохода. Растёт вместе с игрой —
     * поэтому активная игра остаётся выгоднее, чем просто ждать.
     */
    @SerialName("tap_seconds") TAP_SECONDS,

    /** Объём шкалы энергии. */
    @SerialName("energy_max") ENERGY_MAX,

    /** Восстановление энергии, единиц в секунду. */
    @SerialName("energy_regen") ENERGY_REGEN,

    /** Доля дохода, которая идёт вне игры (прибавка к [Rules.offlineRate]). */
    @SerialName("offline_rate") OFFLINE_RATE,

    /** Сколько часов вне игры копится доход (прибавка к [Rules.offlineCapHours]). */
    @SerialName("offline_cap") OFFLINE_CAP,

    /** Доля прибавки к доходу всех карточек категории [CardDef.target]. */
    @SerialName("boost") BOOST,
}

/** Условие открытия: у карточки [card] не меньше [level] уровней. */
@Serializable
data class Requirement(val card: String, val level: Int)

/**
 * Карточка. Цена уровня n (сколько стоит купить уровень n+1): baseCost · costGrowthⁿ.
 * Карточки задаются данными — позже новые наборы будут приходить с сервера, а прогресс
 * игрока хранится по [id], поэтому id никогда не меняются.
 */
@Serializable
data class CardDef(
    val id: String,
    val category: String,
    val name: String,
    val effect: CardEffect,
    val baseCost: Double,
    val costGrowth: Double,
    val baseValue: Double,
    /** Во сколько раз каждый следующий уровень даёт больше предыдущего. */
    val valueGrowth: Double = 1.0,
    /** Линейная прибавка: на сколько (доля от baseValue) каждый следующий уровень даёт больше. */
    val valueStep: Double = 0.0,
    /** Больше этого уровня купить нельзя; null — без предела. */
    val maxLevel: Int? = null,
    /** Все условия должны быть выполнены; часть — из других категорий. */
    val requires: List<Requirement> = emptyList(),
    /** Для [CardEffect.BOOST] — какую категорию усиливает. */
    val target: String? = null,
    /**
     * Временная карточка (категория «Хаос»): продаётся с [availableFrom] до [availableUntil],
     * секунды Unix. Купленные уровни после срока остаются и приносят доход.
     */
    val availableFrom: Long? = null,
    val availableUntil: Long? = null,
    /** Картинка вместо иконки категории: файл в assets/clicker/images/; null — иконка. */
    val image: String? = null,
    /**
     * Картинка поверх [image] (например, кольца Сатурна): может выходить за рамку иконки,
     * но не за край карточки. [overlayScale] — во сколько раз она больше рамки иконки.
     */
    val overlay: String? = null,
    val overlayScale: Double = 1.6,
    /** Сдвиг [overlay] от центра в долях рамки иконки: x — вправо, y — вниз (0.1 — на 10% рамки). */
    val overlayOffsetX: Double = 0.0,
    val overlayOffsetY: Double = 0.0,
) {
    /** Продаётся ли сейчас: у обычной карточки срока нет. */
    fun onSale(nowSeconds: Long?): Boolean {
        if (nowSeconds == null) return availableFrom == null && availableUntil == null
        return (availableFrom == null || nowSeconds >= availableFrom) && (availableUntil == null || nowSeconds < availableUntil)
    }

    /** Цена следующего уровня, если сейчас уровень [level]. */
    fun cost(level: Int): Double = baseCost * costGrowth.pow(level)

    /** Что даёт уровень номер [level] (с нуля). */
    fun valueOf(level: Int): Double = baseValue * valueGrowth.pow(level) * (1 + valueStep * level)

    /** Сколько дают все [levels] купленных уровней вместе. */
    fun total(levels: Int): Double = when {
        levels <= 0 -> 0.0
        valueGrowth == 1.0 -> baseValue * (levels + valueStep * levels * (levels - 1) / 2.0)
        // Уровней у карточки немного (до 30) — проще сложить
        else -> (0 until levels).sumOf { valueOf(it) }
    }

    fun isMaxed(level: Int): Boolean = maxLevel != null && level >= maxLevel
}

/**
 * Категория: открывается, когда за прохождение заработано [unlockAt] коинов.
 * [temporary] — категория временных карточек («Хаос»): её карточки приходят с сервера
 * на время и в прохождении не обязательны.
 */
@Serializable
data class CategoryDef(
    val id: String,
    val name: String,
    val unlockAt: Double = 0.0,
    val temporary: Boolean = false,
    /** Не показывается среди «Улучшений» (прокачка тапа — своей кнопкой). */
    val hidden: Boolean = false,
)

/** Базовые правила — то, что есть у игрока без карточек. */
@Serializable
data class Rules(
    val tapValue: Double = 1.0,
    /** Пассивный доход без карточек, коинов в час — чтобы начать можно было и не тапая. */
    val baseIncomePerHour: Double = 0.0,
    /** Секунд пассивного дохода за тап у нового игрока. */
    val tapIncomeSeconds: Double = 0.2,
    val energyMax: Double = 500.0,
    val energyRegen: Double = 1.0,
    /** Сколько энергии стоит один тап. */
    val tapCost: Double = 1.0,
    val offlineRate: Double = 0.4,
    val offlineCapHours: Double = 3.0,
    /** Цель прохождения: столько коинов заработать за прохождение. */
    val goal: Double,
    /** Сколько звёздной пыли за пройденную игру (перерождение). */
    val stardustPerRun: Int = 100,
    /** Сколько раз игру можно пройти; после последнего прохождения она замирает. */
    val maxRuns: Int = 3,
    /**
     * Уровни игрока (вид планеты): уровень n+1 — когда за прохождение заработано levels[n].
     * Первый порог — 0: первый уровень есть сразу.
     */
    val levels: List<Double> = listOf(0.0),
)

/** Что даёт постоянное улучшение за звёздную пыль; переживает перерождение. */
@Serializable
enum class PerkEffect {
    /** Доля прибавки ко всему пассивному доходу и тапу. */
    @SerialName("income") INCOME,

    /** Доля скидки на все карточки. */
    @SerialName("discount") DISCOUNT,

    /** Секунд пассивного дохода за тап. */
    @SerialName("tap_seconds") TAP_SECONDS,

    @SerialName("offline_rate") OFFLINE_RATE,
    @SerialName("offline_cap") OFFLINE_CAP,
    @SerialName("energy_max") ENERGY_MAX,
    @SerialName("energy_regen") ENERGY_REGEN,

    /** Коинов в начале каждого прохождения. */
    @SerialName("start_coins") START_COINS,
}

/** Улучшение за звёздную пыль: уровень n стоит [cost]·(n+1), каждый даёт [value]. */
@Serializable
data class PerkDef(
    val id: String,
    val name: String,
    val effect: PerkEffect,
    val value: Double,
    val cost: Int,
    val maxLevel: Int = 1,
) {
    fun costOf(level: Int): Int = cost * (level + 1)
}

/** Набор карточек вместе с правилами. [version] растёт с каждым обновлением набора. */
@Serializable
data class ClickerCatalog(
    val version: Int,
    val rules: Rules,
    val categories: List<CategoryDef>,
    val cards: List<CardDef>,
    val perks: List<PerkDef> = emptyList(),
) {
    val cardsById: Map<String, CardDef> by lazy { cards.associateBy { it.id } }

    companion object {
        // Набор правится руками: в нём можно писать комментарии и лишнюю запятую в конце
        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json {
            ignoreUnknownKeys = true
            allowComments = true
            allowTrailingComma = true
        }

        fun parse(text: String): ClickerCatalog = json.decodeFromString(serializer(), text)
    }
}
