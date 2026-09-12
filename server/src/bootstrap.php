<?php
/**
 * Weather Professional 2.0 — серверная часть (shared-хостинг, PHP 8+).
 *
 * Каталог:
 *   htdocs/   — document root (единственная веб-доступная папка)
 *   src/      — классы приложения (WA\*)
 *   cron/     — задачи по расписанию (CLI или через htdocs/cron.php)
 *   tests/    — самотест API (php tests/api_test.php)
 *   install.php        — установщик (схема БД + сиды + админ)
 *   schema.mysql.sql   — дамп схемы для phpMyAdmin
 *
 * Требования: PHP 8.1+, PDO (mysql или sqlite), расширения json, curl (желательно).
 * Внешние зависимости (Composer) НЕ требуются.
 */

declare(strict_types=1);

// Защита от повторного подключения (require из cron-скриптов и админки)
if (defined('WA_BOOTSTRAPPED')) {
    return;
}
define('WA_BOOTSTRAPPED', true);

error_reporting(E_ALL);
ini_set('display_errors', '0'); // ошибки наружу не отдаём

// ---------------------------------------------------------------------------
// Автозагрузка классов WA\* из src/
// ---------------------------------------------------------------------------
spl_autoload_register(static function (string $class): void {
    if (str_starts_with($class, 'WA\\')) {
        $path = __DIR__ . '/' . str_replace('\\', '/', substr($class, 3)) . '.php';
        if (is_file($path)) {
            require $path;
        }
    }
});

// ---------------------------------------------------------------------------
// Помощники
// ---------------------------------------------------------------------------

/**
 * Загрузка конфигурации. Путь можно переопределить переменной окружения
 * WA_CONFIG (используется тестами). Кэшируется на время запроса.
 */
function wa_config(): array {
    /** @var array|null $cached */
    static $cached = null;
    if ($cached !== null) {
        return $cached;
    }
    $file = getenv('WA_CONFIG') ?: dirname(__DIR__) . '/config.php';
    if (!is_file($file)) {
        http_response_code(500);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode(['error' => 'not_installed', 'message' => 'Создайте config.php из config.example.php и запустите install.php'], JSON_UNESCAPED_UNICODE);
        exit;
    }
    /** @var mixed $cfg */
    $cfg = require $file;
    if (!is_array($cfg)) {
        http_response_code(500);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode(['error' => 'bad_config'], JSON_UNESCAPED_UNICODE);
        exit;
    }
    $cached = $cfg;
    // Все времена в БД и API — UTC. Локальная таймзона PHP принудительно UTC,
    // чтобы strtotime() корректно разбирал наивные строки из БД.
    date_default_timezone_set('UTC');
    return $cached;
}

/** Универсальный JSON-ответ с завершением запроса. */
function wa_json(array $data, int $status = 200): never {
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('X-Content-Type-Options: nosniff');
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

/** Ошибка API в едином формате. */
function wa_error(string $code, string $message, int $status = 400, array $extra = []): never {
    wa_json(['error' => $code, 'message' => $message] + $extra, $status);
}

/** Тело запроса (JSON) как массив. */
function wa_body(): array {
    static $body = null;
    if ($body === null) {
        $raw = file_get_contents('php://input') ?: '';
        /** @var mixed $decoded */
        $decoded = json_decode($raw, true);
        $body = is_array($decoded) ? $decoded : [];
    }
    return $body;
}

/** Путь запроса без query-строки. */
function wa_path(): string {
    $uri = $_SERVER['REQUEST_URI'] ?? '/';
    $path = parse_url($uri, PHP_URL_PATH);
    return is_string($path) ? rtrim($path, '/') ?: '/' : '/';
}

/** Текущее время в БД-формате (UTC). */
function wa_db_now(): string {
    return gmdate('Y-m-d H:i:s');
}

/** UUID v4. */
function wa_uuid(): string {
    $b = random_bytes(16);
    $b[6] = chr((ord($b[6]) & 0x0f) | 0x40);
    $b[8] = chr((ord($b[8]) & 0x3f) | 0x80);
    return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($b), 4));
}
