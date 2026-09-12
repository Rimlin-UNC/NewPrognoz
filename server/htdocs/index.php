<?php
/**
 * Weather Professional 2.0 — точка входа API.
 * Все запросы /api/v1/* маршрутизируются сюда через .htaccess.
 */

declare(strict_types=1);

require __DIR__ . '/../src/bootstrap.php';

// CORS (по умолчанию закрыт; домены задаются в конфиге)
$origins = (array) (wa_config()['app']['cors_origins'] ?? []);
if ($origins !== []) {
    $origin = $_SERVER['HTTP_ORIGIN'] ?? '';
    if (in_array($origin, $origins, true)) {
        header('Access-Control-Allow-Origin: ' . $origin);
        header('Vary: Origin');
        header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
        header('Access-Control-Allow-Headers: Content-Type, X-Api-Key, Authorization');
        header('Access-Control-Max-Age: 600');
    }
}
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

// Требуем HTTPS, если включено (за reverse-proxy смотрим X-Forwarded-Proto)
if (!empty(wa_config()['app']['force_https'])) {
    $proto = $_SERVER['HTTP_X_FORWARDED_PROTO'] ?? ($_SERVER['REQUEST_SCHEME'] ?? 'http');
    if (strtolower((string) $proto) !== 'https') {
        wa_error('https_required', 'Доступен только HTTPS', 400);
    }
}

$router = new WA\Router();

$router->add('GET',  '/api/v1/health', [WA\Controllers::class, 'health']);
$router->add('POST', '/api/v1/auth/register', [WA\Controllers::class, 'register']);
$router->add('POST', '/api/v1/auth/login', [WA\Controllers::class, 'login']);
$router->add('POST', '/api/v1/devices', [WA\Controllers::class, 'devices']);
$router->add('GET',  '/api/v1/me', [WA\Controllers::class, 'me']);
$router->add('POST', '/api/v1/sync/push', [WA\Controllers::class, 'push']);
$router->add('GET',  '/api/v1/sync/pull', [WA\Controllers::class, 'pull']);
$router->add('POST', '/api/v1/observations', [WA\Controllers::class, 'observation']);
$router->add('GET',  '/api/v1/forecast', [WA\Controllers::class, 'forecast']);
$router->add('GET',  '/api/v1/aggregates', [WA\Controllers::class, 'aggregates']);

try {
    $router->dispatch($_SERVER['REQUEST_METHOD'] ?? 'GET', wa_path());
} catch (\Throwable $e) {
    WA\Logger::app('error', 'unhandled: ' . $e->getMessage(), [
        'file' => basename($e->getFile()) . ':' . $e->getLine(),
    ]);
    wa_error('internal', 'Внутренняя ошибка сервера', 500);
}
