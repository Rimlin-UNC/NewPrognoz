<?php
/**
 * Weather Professional 2.0 — конфигурация сервера.
 * Скопируйте в config.php и заполните значениями вашего хостинга.
 */

return [
    'db' => [
        'driver' => 'mysql',            // 'mysql' (хостинг) | 'sqlite' (локально/тесты)
        'host' => 'localhost',
        'port' => 3306,
        'name' => 'имя_базы',
        'user' => 'пользователь_базы',
        'pass' => 'пароль_базы',
        'sqlite_path' => __DIR__ . '/data/weather.sqlite', // только для driver=sqlite
    ],
    'app' => [
        'url' => 'https://ваш-домен.ymaster.ru',
        'timezone' => 'Europe/Moscow',
        // Секретный «перец»: 64 случайных символов. Сгенерируйте ОДИН раз
        // и не меняйте после развертывания (ломает device_hash и JWT).
        'pepper' => 'ЗАМЕНИТЕ_НА_64_СЛУЧАЙНЫХ_СИМВОЛА___abcdefghijklmnopqrstuvwxyz0123456',
        'jwt_ttl' => 3600,              // срок жизни JWT, сек
        'rate_limit' => 120,            // запросов в минуту на API-ключ
        'force_https' => true,          // на проде обязательно true
        'cors_origins' => [],           // например ['https://ваш-домен.ymaster.ru']
        // Учётка администратора, создаётся install.php:
        'admin_email' => 'admin@example.com',
        'admin_password' => 'ЗАМЕНИТЕ_ПАРОЛЬ',
    ],
    'open_meteo' => [
        'base_url' => 'https://api.open-meteo.com/v1/forecast',
        'timeout' => 15,
        'cache_ttl' => 1200,            // кэш прогноза на сервере, сек (20 минут)
    ],
    'cron' => [
        // Секрет для запуска задач через /cron.php?token=...
        'secret' => 'ЗАМЕНИТЕ_СЕКРЕТ_CRON',
    ],
];
