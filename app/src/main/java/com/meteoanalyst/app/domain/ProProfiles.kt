package com.meteoanalyst.app.domain

import com.meteoanalyst.app.data.model.WeatherPoint

/**
 * Профессиональные профили пользователей (ТЗ «Weather Professional 2.0»).
 * Каждому профилю — свой набор критичных параметров и порогов.
 */
enum class ProProfile(val code: String, val title: String, val emoji: String) {
    UNIVERSAL("universal", "Универсал", "🌍"),
    ROOFER("roofer", "Кровельщик", "🏠"),
    BUILDER("builder", "Строитель", "🏗"),
    PILOT("pilot", "Лётчик", "✈"),
    FISHER("fisherman", "Рыбак", "🎣"),
    ALPINIST("alpinist", "Альпинист", "🏔");

    companion object {
        fun byCode(code: String?): ProProfile =
            entries.firstOrNull { it.code == code } ?: UNIVERSAL
    }
}

/** Критичное условие для профиля. */
data class CriticalFlag(
    val key: String,
    val critical: Boolean,
    val message: String
)

/**
 * Правила оценки условий по профилю (зеркало серверных ProfileRules).
 */
object ProfileRules {

    fun evaluate(profile: ProProfile, p: WeatherPoint): List<CriticalFlag> {
        val thunder = p.weatherCode >= 95
        val snow = p.weatherCode in listOf(71, 73, 75, 77, 85, 86)
        val rainy = p.precipitation >= 0.2f ||
            p.weatherCode in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)
        val flags = mutableListOf<CriticalFlag>()

        when (profile) {
            ProProfile.ROOFER -> {
                if (p.windGusts >= 12f) flags += f("gust", true,
                    "Порывы ${p.windGusts.toInt()} м/с — работы на высоте запрещены (норма < 12)")
                else if (p.windGusts >= 9f) flags += f("gust", false,
                    "Порывы ${p.windGusts.toInt()} м/с — крепить материалы")
                if (rainy) flags += f("precip", true,
                    "Осадки — монтаж мембран/мастик запрещён")
                if (thunder) flags += f("thunder", true, "Гроза — покинуть крышу")
                if (p.temperature <= 0f && rainy) flags += f("ice", true, "Гололёд — риск падения")
            }
            ProProfile.BUILDER -> {
                if (p.windGusts >= 15f) flags += f("gust", true,
                    "Порывы ${p.windGusts.toInt()} м/с — остановка крановых работ")
                else if (p.windGusts >= 10f) flags += f("gust", false,
                    "Порывы ${p.windGusts.toInt()} м/с — осторожно с краном/лесами")
                if (rainy) flags += f("precip", false, "Осадки — бетонные работы требуют защиты")
                if (thunder) flags += f("thunder", true, "Гроза — прекратить работы на открытом воздухе")
                if (p.temperature <= -20f) flags += f("cold", true,
                    "Мороз ${p.temperature.toInt()}°C — ограничения на бетонирование")
            }
            ProProfile.PILOT -> {
                val vis = p.visibility
                if (vis != null && vis < 5000f) flags += f("visibility", true,
                    "Видимость < 5 км — VMC не гарантируется")
                else if (vis != null && vis < 8000f) flags += f("visibility", false,
                    "Видимость < 8 км — уточнить правила полётов")
                if (p.windGusts >= 15f) flags += f("gust", true,
                    "Порывы ${p.windGusts.toInt()} м/с — сдвиг ветра/турбулентность")
                if (thunder) flags += f("thunder", true, "Гроза — CB, полёты запрещены")
                if (snow) flags += f("snow", true, "Снегопад — обледенение")
                if (p.weatherCode == 45 || p.weatherCode == 48) flags += f("fog", true,
                    "Туман — видимость может быть ниже минимума")
            }
            ProProfile.FISHER -> {
                if (p.windSpeed >= 10f) flags += f("wind", true,
                    "Ветер ${p.windSpeed.toInt()} м/с — опасно для лодки")
                else if (p.windSpeed >= 7f) flags += f("wind", false,
                    "Ветер ${p.windSpeed.toInt()} м/с — волна")
                if (thunder) flags += f("thunder", true, "Гроза — выйти на берег")
                if (p.temperature <= -5f && rainy) flags += f("ice", false,
                    "Замерзающая мокрая снасть/лёд")
            }
            ProProfile.ALPINIST -> {
                if (p.windSpeed >= 15f) flags += f("wind", true,
                    "Ветер ${p.windSpeed.toInt()} м/с — шторм, спуск")
                else if (p.windSpeed >= 10f) flags += f("wind", false,
                    "Ветер ${p.windSpeed.toInt()} м/с — оценка маршрута")
                val vis = p.visibility
                if (vis != null && vis < 1000f) flags += f("visibility", true,
                    "Видимость < 1 км — навигация критична")
                if (snow) flags += f("snow", true, "Снегопад — лавиноопасно")
                if (p.temperature <= -15f) flags += f("cold", true,
                    "Мороз ${p.temperature.toInt()}°C — обморожение")
            }
            ProProfile.UNIVERSAL -> {
                if (p.windGusts >= 17f) flags += f("gust", true,
                    "Шквалы до ${p.windGusts.toInt()} м/с")
                if (thunder) flags += f("thunder", false, "Гроза")
            }
        }
        return flags
    }

    /** Оценка для всех ближайших часов (максимум критичности за окно). */
    fun evaluateWindow(profile: ProProfile, points: List<WeatherPoint>): List<CriticalFlag> {
        if (points.isEmpty()) return emptyList()
        val merged = mutableListOf<CriticalFlag>()
        for (p in points) {
            for (flag in evaluate(profile, p)) {
                val idx = merged.indexOfFirst { it.key == flag.key }
                if (idx == -1) {
                    merged += flag
                } else if (flag.critical && !merged[idx].critical) {
                    merged[idx] = flag
                }
            }
        }
        return merged
    }

    private fun f(key: String, critical: Boolean, message: String) =
        CriticalFlag(key, critical, message)
}
