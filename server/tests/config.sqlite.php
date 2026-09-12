<?php
/**
 * Конфигурация для самотеста (SQLite, низкий rate limit для проверки 429).
 * Используется через переменную окружения WA_CONFIG.
 */

return [
    'db' => [
        'driver' => 'sqlite',
        'sqlite_path' => __DIR__ . '/data/test.sqlite',
    ],
    'app' => [
        'url' => 'http://127.0.0.1:8099',
        'timezone' => 'UTC',
        'pepper' => 'test_pepper_0123456789_0123456789_0123456789_0123456789',
        'jwt_ttl' => 600,
        'rate_limit' => 40,          // низкий порог для теста 429
        'force_https' => false,
        'cors_origins' => [],
        'admin_email' => 'admin@test.local',
        'admin_password' => 'admin-password-123',
    ],
    'open_meteo' => [
        'base_url' => 'https://api.open-meteo.com/v1/forecast',
        'timeout' => 20,
        'cache_ttl' => 1200,
    ],
    'cron' => [
        'secret' => 'test_cron_secret',
    ],
];
