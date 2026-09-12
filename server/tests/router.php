<?php
/**
 * Router для встроенного PHP-сервера (тесты e2e).
 *
 *   WA_CONFIG=$(pwd)/tests/config.sqlite.php \
 *     php -S 127.0.0.1:8099 -t htdocs tests/router.php
 *
 * Эмулирует поведение .htaccess: /api/* уходит в index.php,
 * реальные файлы (admin/, cron.php) отдаются напрямую.
 */
declare(strict_types=1);

$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$docRoot = rtrim((string) ($_SERVER['DOCUMENT_ROOT'] ?? dirname(__DIR__) . '/htdocs'), '/');

// Существующие файлы (админка, cron.php) — отдаёт сам сервер
if ($path !== '/' && is_file($docRoot . $path)) {
    return false;
}

// Каталоги без слэша и корень — index.php этого каталога
if (str_ends_with($path, '/')) {
    $dirIndex = $docRoot . $path . 'index.php';
    if (is_file($dirIndex)) {
        $_SERVER['SCRIPT_NAME'] = $path . 'index.php';
        require $dirIndex;
        return true;
    }
}

// API и всё остальное — единая точка входа
$_SERVER['SCRIPT_NAME'] = '/index.php';
require $docRoot . '/index.php';
return true;
