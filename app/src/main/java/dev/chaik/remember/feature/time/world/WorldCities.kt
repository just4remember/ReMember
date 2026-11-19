package dev.chaik.remember.feature.time.world

import java.time.ZoneId

/** Город для мирового времени: часовой пояс и названия на двух языках. [id] — английское название. */
data class WorldCity(
    val zone: String,
    val nameRu: String,
    val countryRu: String,
    val nameEn: String,
    val countryEn: String,
) {
    val id: String get() = nameEn
    val zoneId: ZoneId get() = ZoneId.of(zone)

    fun name(russian: Boolean) = if (russian) nameRu else nameEn
    fun country(russian: Boolean) = if (russian) countryRu else countryEn

    /** Подходит под поиск: по названию или стране на любом языке, «ё» = «е». */
    fun matches(query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        return listOf(nameRu, countryRu, nameEn, countryEn).any { normalize(it).contains(q) }
    }

    private fun normalize(s: String) = s.trim().lowercase().replace('ё', 'е').replace('-', ' ')
}

/** Встроенный список крупных городов — без интернета. Порядок: Россия, затем остальной мир по алфавиту стран. */
object WorldCities {
    val all: List<WorldCity> by lazy {
        DATA.lines().map(String::trim).filter(String::isNotEmpty).map { line ->
            val (zone, nameRu, countryRu, nameEn, countryEn) = line.split('|')
            WorldCity(zone, nameRu, countryRu, nameEn, countryEn)
        }
    }

    private val byId: Map<String, WorldCity> by lazy { all.associateBy { it.id } }

    fun find(id: String): WorldCity? = byId[id]

    private const val DATA = """
Europe/Kaliningrad|Калининград|Россия|Kaliningrad|Russia
Europe/Moscow|Москва|Россия|Moscow|Russia
Europe/Moscow|Санкт-Петербург|Россия|Saint Petersburg|Russia
Europe/Moscow|Казань|Россия|Kazan|Russia
Europe/Moscow|Нижний Новгород|Россия|Nizhny Novgorod|Russia
Europe/Moscow|Ростов-на-Дону|Россия|Rostov-on-Don|Russia
Europe/Moscow|Краснодар|Россия|Krasnodar|Russia
Europe/Moscow|Сочи|Россия|Sochi|Russia
Europe/Moscow|Воронеж|Россия|Voronezh|Russia
Europe/Moscow|Ярославль|Россия|Yaroslavl|Russia
Europe/Moscow|Мурманск|Россия|Murmansk|Russia
Europe/Moscow|Архангельск|Россия|Arkhangelsk|Russia
Europe/Moscow|Махачкала|Россия|Makhachkala|Russia
Europe/Kirov|Киров|Россия|Kirov|Russia
Europe/Volgograd|Волгоград|Россия|Volgograd|Russia
Europe/Saratov|Саратов|Россия|Saratov|Russia
Europe/Astrakhan|Астрахань|Россия|Astrakhan|Russia
Europe/Ulyanovsk|Ульяновск|Россия|Ulyanovsk|Russia
Europe/Samara|Самара|Россия|Samara|Russia
Europe/Samara|Ижевск|Россия|Izhevsk|Russia
Asia/Yekaterinburg|Екатеринбург|Россия|Yekaterinburg|Russia
Asia/Yekaterinburg|Челябинск|Россия|Chelyabinsk|Russia
Asia/Yekaterinburg|Уфа|Россия|Ufa|Russia
Asia/Yekaterinburg|Пермь|Россия|Perm|Russia
Asia/Yekaterinburg|Тюмень|Россия|Tyumen|Russia
Asia/Yekaterinburg|Оренбург|Россия|Orenburg|Russia
Asia/Yekaterinburg|Сургут|Россия|Surgut|Russia
Asia/Omsk|Омск|Россия|Omsk|Russia
Asia/Novosibirsk|Новосибирск|Россия|Novosibirsk|Russia
Asia/Barnaul|Барнаул|Россия|Barnaul|Russia
Asia/Tomsk|Томск|Россия|Tomsk|Russia
Asia/Novokuznetsk|Кемерово|Россия|Kemerovo|Russia
Asia/Novokuznetsk|Новокузнецк|Россия|Novokuznetsk|Russia
Asia/Krasnoyarsk|Красноярск|Россия|Krasnoyarsk|Russia
Asia/Krasnoyarsk|Норильск|Россия|Norilsk|Russia
Asia/Irkutsk|Иркутск|Россия|Irkutsk|Russia
Asia/Irkutsk|Улан-Удэ|Россия|Ulan-Ude|Russia
Asia/Chita|Чита|Россия|Chita|Russia
Asia/Yakutsk|Якутск|Россия|Yakutsk|Russia
Asia/Yakutsk|Благовещенск|Россия|Blagoveshchensk|Russia
Asia/Vladivostok|Владивосток|Россия|Vladivostok|Russia
Asia/Vladivostok|Хабаровск|Россия|Khabarovsk|Russia
Asia/Sakhalin|Южно-Сахалинск|Россия|Yuzhno-Sakhalinsk|Russia
Asia/Magadan|Магадан|Россия|Magadan|Russia
Asia/Kamchatka|Петропавловск-Камчатский|Россия|Petropavlovsk-Kamchatsky|Russia
Asia/Anadyr|Анадырь|Россия|Anadyr|Russia
Australia/Adelaide|Аделаида|Австралия|Adelaide|Australia
Australia/Brisbane|Брисбен|Австралия|Brisbane|Australia
Australia/Melbourne|Мельбурн|Австралия|Melbourne|Australia
Australia/Perth|Перт|Австралия|Perth|Australia
Australia/Sydney|Сидней|Австралия|Sydney|Australia
Europe/Vienna|Вена|Австрия|Vienna|Austria
Asia/Baku|Баку|Азербайджан|Baku|Azerbaijan
America/Argentina/Buenos_Aires|Буэнос-Айрес|Аргентина|Buenos Aires|Argentina
Asia/Yerevan|Ереван|Армения|Yerevan|Armenia
Asia/Kabul|Кабул|Афганистан|Kabul|Afghanistan
Asia/Dhaka|Дакка|Бангладеш|Dhaka|Bangladesh
Europe/Minsk|Минск|Беларусь|Minsk|Belarus
Europe/Brussels|Брюссель|Бельгия|Brussels|Belgium
Europe/Sofia|София|Болгария|Sofia|Bulgaria
America/Sao_Paulo|Сан-Паулу|Бразилия|Sao Paulo|Brazil
America/Sao_Paulo|Рио-де-Жанейро|Бразилия|Rio de Janeiro|Brazil
Europe/London|Лондон|Великобритания|London|United Kingdom
Europe/London|Эдинбург|Великобритания|Edinburgh|United Kingdom
Europe/Budapest|Будапешт|Венгрия|Budapest|Hungary
America/Caracas|Каракас|Венесуэла|Caracas|Venezuela
Asia/Ho_Chi_Minh|Хошимин|Вьетнам|Ho Chi Minh City|Vietnam
Asia/Bangkok|Ханой|Вьетнам|Hanoi|Vietnam
Europe/Berlin|Берлин|Германия|Berlin|Germany
Europe/Berlin|Мюнхен|Германия|Munich|Germany
Europe/Berlin|Франкфурт-на-Майне|Германия|Frankfurt|Germany
Europe/Athens|Афины|Греция|Athens|Greece
Asia/Tbilisi|Тбилиси|Грузия|Tbilisi|Georgia
Europe/Copenhagen|Копенгаген|Дания|Copenhagen|Denmark
Africa/Cairo|Каир|Египет|Cairo|Egypt
Asia/Jerusalem|Тель-Авив|Израиль|Tel Aviv|Israel
Asia/Jerusalem|Иерусалим|Израиль|Jerusalem|Israel
Asia/Kolkata|Дели|Индия|Delhi|India
Asia/Kolkata|Мумбаи|Индия|Mumbai|India
Asia/Kolkata|Бангалор|Индия|Bangalore|India
Asia/Jakarta|Джакарта|Индонезия|Jakarta|Indonesia
Asia/Makassar|Денпасар (Бали)|Индонезия|Denpasar|Indonesia
Asia/Tehran|Тегеран|Иран|Tehran|Iran
Europe/Dublin|Дублин|Ирландия|Dublin|Ireland
Atlantic/Reykjavik|Рейкьявик|Исландия|Reykjavik|Iceland
Europe/Madrid|Мадрид|Испания|Madrid|Spain
Europe/Madrid|Барселона|Испания|Barcelona|Spain
Atlantic/Canary|Лас-Пальмас|Испания|Las Palmas|Spain
Europe/Rome|Рим|Италия|Rome|Italy
Europe/Rome|Милан|Италия|Milan|Italy
Asia/Almaty|Алматы|Казахстан|Almaty|Kazakhstan
Asia/Almaty|Астана|Казахстан|Astana|Kazakhstan
Asia/Aqtau|Актау|Казахстан|Aktau|Kazakhstan
America/Toronto|Торонто|Канада|Toronto|Canada
America/Toronto|Монреаль|Канада|Montreal|Canada
America/Vancouver|Ванкувер|Канада|Vancouver|Canada
America/Edmonton|Калгари|Канада|Calgary|Canada
Asia/Qatar|Доха|Катар|Doha|Qatar
Africa/Nairobi|Найроби|Кения|Nairobi|Kenya
Asia/Nicosia|Никосия|Кипр|Nicosia|Cyprus
Asia/Famagusta|Фамагуста|Кипр|Famagusta|Cyprus
Asia/Bishkek|Бишкек|Киргизия|Bishkek|Kyrgyzstan
Asia/Shanghai|Пекин|Китай|Beijing|China
Asia/Shanghai|Шанхай|Китай|Shanghai|China
Asia/Shanghai|Гуанчжоу|Китай|Guangzhou|China
Asia/Shanghai|Харбин|Китай|Harbin|China
Asia/Hong_Kong|Гонконг|Китай|Hong Kong|China
America/Bogota|Богота|Колумбия|Bogota|Colombia
America/Havana|Гавана|Куба|Havana|Cuba
Asia/Kuwait|Эль-Кувейт|Кувейт|Kuwait City|Kuwait
Europe/Riga|Рига|Латвия|Riga|Latvia
Asia/Beirut|Бейрут|Ливан|Beirut|Lebanon
Europe/Vilnius|Вильнюс|Литва|Vilnius|Lithuania
Indian/Mauritius|Порт-Луи|Маврикий|Port Louis|Mauritius
Asia/Kuala_Lumpur|Куала-Лумпур|Малайзия|Kuala Lumpur|Malaysia
Indian/Maldives|Мале|Мальдивы|Male|Maldives
Africa/Casablanca|Касабланка|Марокко|Casablanca|Morocco
America/Mexico_City|Мехико|Мексика|Mexico City|Mexico
America/Cancun|Канкун|Мексика|Cancun|Mexico
Europe/Chisinau|Кишинёв|Молдова|Chisinau|Moldova
Asia/Ulaanbaatar|Улан-Батор|Монголия|Ulaanbaatar|Mongolia
Asia/Kathmandu|Катманду|Непал|Kathmandu|Nepal
Africa/Lagos|Лагос|Нигерия|Lagos|Nigeria
Europe/Amsterdam|Амстердам|Нидерланды|Amsterdam|Netherlands
Pacific/Auckland|Окленд|Новая Зеландия|Auckland|New Zealand
Europe/Oslo|Осло|Норвегия|Oslo|Norway
Asia/Dubai|Дубай|ОАЭ|Dubai|United Arab Emirates
Asia/Dubai|Абу-Даби|ОАЭ|Abu Dhabi|United Arab Emirates
Asia/Muscat|Маскат|Оман|Muscat|Oman
Asia/Karachi|Карачи|Пакистан|Karachi|Pakistan
America/Panama|Панама|Панама|Panama City|Panama
America/Lima|Лима|Перу|Lima|Peru
Europe/Warsaw|Варшава|Польша|Warsaw|Poland
Europe/Lisbon|Лиссабон|Португалия|Lisbon|Portugal
Asia/Seoul|Сеул|Республика Корея|Seoul|South Korea
Europe/Bucharest|Бухарест|Румыния|Bucharest|Romania
Asia/Riyadh|Эр-Рияд|Саудовская Аравия|Riyadh|Saudi Arabia
Indian/Mahe|Виктория|Сейшелы|Victoria|Seychelles
Europe/Belgrade|Белград|Сербия|Belgrade|Serbia
Asia/Singapore|Сингапур|Сингапур|Singapore|Singapore
Europe/Bratislava|Братислава|Словакия|Bratislava|Slovakia
Europe/Ljubljana|Любляна|Словения|Ljubljana|Slovenia
America/New_York|Нью-Йорк|США|New York|United States
America/New_York|Вашингтон|США|Washington|United States
America/New_York|Майами|США|Miami|United States
America/New_York|Бостон|США|Boston|United States
America/Chicago|Чикаго|США|Chicago|United States
America/Chicago|Хьюстон|США|Houston|United States
America/Denver|Денвер|США|Denver|United States
America/Phoenix|Финикс|США|Phoenix|United States
America/Los_Angeles|Лос-Анджелес|США|Los Angeles|United States
America/Los_Angeles|Сан-Франциско|США|San Francisco|United States
America/Los_Angeles|Сиэтл|США|Seattle|United States
America/Los_Angeles|Лас-Вегас|США|Las Vegas|United States
America/Anchorage|Анкоридж|США|Anchorage|United States
Pacific/Honolulu|Гонолулу|США|Honolulu|United States
Asia/Dushanbe|Душанбе|Таджикистан|Dushanbe|Tajikistan
Asia/Bangkok|Бангкок|Таиланд|Bangkok|Thailand
Asia/Bangkok|Пхукет|Таиланд|Phuket|Thailand
Asia/Taipei|Тайбэй|Тайвань|Taipei|Taiwan
Africa/Dar_es_Salaam|Занзибар|Танзания|Zanzibar|Tanzania
Africa/Tunis|Тунис|Тунис|Tunis|Tunisia
Asia/Ashgabat|Ашхабад|Туркменистан|Ashgabat|Turkmenistan
Europe/Istanbul|Стамбул|Турция|Istanbul|Turkey
Europe/Istanbul|Анталья|Турция|Antalya|Turkey
Asia/Tashkent|Ташкент|Узбекистан|Tashkent|Uzbekistan
Asia/Samarkand|Самарканд|Узбекистан|Samarkand|Uzbekistan
Europe/Kiev|Киев|Украина|Kyiv|Ukraine
Asia/Manila|Манила|Филиппины|Manila|Philippines
Europe/Helsinki|Хельсинки|Финляндия|Helsinki|Finland
Europe/Paris|Париж|Франция|Paris|France
Europe/Paris|Ницца|Франция|Nice|France
Europe/Zagreb|Загреб|Хорватия|Zagreb|Croatia
Europe/Podgorica|Подгорица|Черногория|Podgorica|Montenegro
Europe/Prague|Прага|Чехия|Prague|Czechia
America/Santiago|Сантьяго|Чили|Santiago|Chile
Europe/Zurich|Цюрих|Швейцария|Zurich|Switzerland
Europe/Zurich|Женева|Швейцария|Geneva|Switzerland
Europe/Stockholm|Стокгольм|Швеция|Stockholm|Sweden
Asia/Colombo|Коломбо|Шри-Ланка|Colombo|Sri Lanka
Europe/Tallinn|Таллин|Эстония|Tallinn|Estonia
Africa/Addis_Ababa|Аддис-Абеба|Эфиопия|Addis Ababa|Ethiopia
Africa/Johannesburg|Йоханнесбург|ЮАР|Johannesburg|South Africa
Africa/Johannesburg|Кейптаун|ЮАР|Cape Town|South Africa
Asia/Tokyo|Токио|Япония|Tokyo|Japan
Asia/Tokyo|Осака|Япония|Osaka|Japan
"""
}
