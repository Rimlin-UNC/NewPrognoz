package com.meteoanalyst.app.data.providers

import com.meteoanalyst.app.data.remote.OpenMeteoClient

/**
 * Фабрика шести источников (ТЗ п.1):
 *
 *  1. Open-Meteo      — реальные данные (без ключа);
 *  2. WeatherAPI      — заглушка: шум ±1.5 °C, ±2 м/с;
 *  3. Visual Crossing — заглушка: другая погрешность (+смещение, +добавка осадков);
 *  4. Tomorrow.io     — заглушка: небольшой шум по температуре, крупный по ветру;
 *  5. AccuWeather     — заглушка: бинарный шум по осадкам;
 *  6. Custom          — усреднение первых двух + микрошум.
 *
 * Все заглушки детерминированы — сверка воспроизводима. Для перехода
 * на реальные API замените реализации в этом файле.
 */
object ProviderFactory {

    fun create(client: OpenMeteoClient): List<WeatherProvider> {
        val openMeteo = OpenMeteoProvider(client)

        val weatherApi = SimulatedProvider(
            base = openMeteo,
            id = "weatherapi",
            name = "WeatherAPI",
            color = 0xFFFFB74D,
            tempError = 1.5f,
            windError = 2f
        )

        val visualCrossing = SimulatedProvider(
            base = openMeteo,
            id = "visualcrossing",
            name = "Visual Crossing",
            color = 0xFFBA68C8,
            tempError = 2.5f,
            tempBias = -0.4f,
            windError = 3f,
            windBias = 0.3f,
            rainNoise = SimulatedProvider.RainNoise.ADD,
            rainFlipProbability = 0.15f,
            rainAddAmount = 0.2f
        )

        val tomorrowIo = SimulatedProvider(
            base = openMeteo,
            id = "tomorrowio",
            name = "Tomorrow.io",
            color = 0xFF81C784,
            tempError = 1.0f,
            tempBias = 0.3f,
            windError = 3.5f,
            rainNoise = SimulatedProvider.RainNoise.FLIP,
            rainFlipProbability = 0.12f,
            rainAddAmount = 0.4f
        )

        val accuWeather = SimulatedProvider(
            base = openMeteo,
            id = "accuweather",
            name = "AccuWeather",
            color = 0xFFE57373,
            tempError = 0.8f,
            windError = 1.2f,
            rainNoise = SimulatedProvider.RainNoise.FLIP,   // бинарный шум по осадкам (ТЗ)
            rainFlipProbability = 0.25f,
            rainAddAmount = 0.4f
        )

        val custom = CustomProvider(openMeteo, weatherApi)

        return listOf(openMeteo, weatherApi, visualCrossing, tomorrowIo, accuWeather, custom)
    }
}
