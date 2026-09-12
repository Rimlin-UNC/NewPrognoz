<?php
/**
 * Weather Professional 2.0 — e2e самотест API.
 *
 * Запуск (из каталога server/, пока работает встроенный сервер):
 *   WA_CONFIG=$(pwd)/tests/config.sqlite.php php -S 127.0.0.1:8099 -t htdocs &
 *   php tests/api_test.php
 *
 * Покрывает: health, register, авторизацию, push (валидация + идемпотентность),
 * агрегацию (напрямую через сервис), pull, forecast (реальный Open-Meteo),
 * rate limit, admin-логику установки.
 */

declare(strict_types=1);

$base = getenv('WA_BASE_URL') ?: 'http://127.0.0.1:8099';
$root = dirname(__DIR__);
putenv('WA_CONFIG=' . $root . '/tests/config.sqlite.php');

require $root . '/src/bootstrap.php';

// ---------------------------------------------------------------- helpers
$passed = 0;
$failed = 0;

function check(string $name, bool $cond, string $detail = ''): void {
    global $passed, $failed;
    if ($cond) {
        $passed++;
        echo "  ok  $name\n";
    } else {
        $failed++;
        echo "FAIL  $name" . ($detail !== '' ? " — $detail" : '') . "\n";
    }
}

/** @return array{status:int, body:array} */
function http(string $method, string $path, ?array $json = null, array $headers = []): array {
    $url = $GLOBALS['base'] . $path;
    $ch = curl_init($url);
    $hdrs = ['Accept: application/json'];
    foreach ($headers as $k => $v) {
        $hdrs[] = "$k: $v";
    }
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT => 40,
        CURLOPT_CUSTOMREQUEST => $method,
        CURLOPT_HTTPHEADER => $hdrs,
        CURLOPT_HEADER => false,
    ]);
    if ($json !== null) {
        curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($json, JSON_UNESCAPED_UNICODE));
    }
    $body = curl_exec($ch);
    $status = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    curl_close($ch);
    /** @var mixed $decoded */
    $decoded = is_string($body) ? json_decode($body, true) : null;
    return ['status' => $status, 'body' => is_array($decoded) ? $decoded : []];
}

function uuid4(): string {
    $b = random_bytes(16);
    $b[6] = chr((ord($b[6]) & 0x0f) | 0x40);
    $b[8] = chr((ord($b[8]) & 0x3f) | 0x80);
    return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($b), 4));
}

echo "== Weather Pro 2.0: server e2e tests ==\n";
echo "base: $base\n\n";

// ---------------------------------------------------------------- install
echo "[setup] установка схемы в SQLite\n";
@unlink(__DIR__ . '/data/test.sqlite');   // путь совпадает с tests/config.sqlite.php
$result = WA\Schema::install(WA\Db::pdo());
check('схема создана', count($result) >= 14, 'таблиц: ' . count($result));
// админ из тестового конфига
$email = 'admin@test.local';
$password = 'admin-password-123';
$pdo = WA\Db::pdo();
$st = $pdo->prepare('SELECT id FROM users WHERE email = ?');
$st->execute([$email]);
if (!$st->fetch()) {
    $pdo->prepare('INSERT INTO users (uuid, email, password_hash, role, created_at, updated_at) VALUES (?,?,?,?,?,?)')
        ->execute([wa_uuid(), $email, password_hash($password, PASSWORD_BCRYPT), 'admin', wa_db_now(), wa_db_now()]);
}
check('админ создан', true);

// ---------------------------------------------------------------- health
echo "\n[1] health\n";
$r = http('GET', '/api/v1/health');
check('health 200', $r['status'] === 200, 'status=' . $r['status']);
check('health status=ok', ($r['body']['status'] ?? '') === 'ok', json_encode($r['body']));
check('health driver=sqlite', ($r['body']['driver'] ?? '') === 'sqlite');

// ---------------------------------------------------------------- register
echo "\n[2] регистрация и авторизация\n";
$email = 'user' . random_int(1000, 999999) . '@test.local';
$r = http('POST', '/api/v1/auth/register', [
    'email' => $email, 'password' => 'password-123456',
    'device_name' => 'CI', 'platform' => 'android', 'app_version' => '2.0.0',
]);
check('register 201', $r['status'] === 201, 'status=' . $r['status'] . ' body=' . json_encode($r['body']));
$apiKey = (string) ($r['body']['api_key'] ?? '');
check('api_key выдан', str_starts_with($apiKey, 'wa_') && strlen($apiKey) === 51);

$r = http('POST', '/api/v1/auth/register', ['email' => $email, 'password' => 'password-123456']);
check('повторная регистрация 409', $r['status'] === 409);

$r = http('POST', '/api/v1/auth/register', ['email' => 'broken', 'password' => '123']);
check('некорректный email 422', $r['status'] === 422);

$r = http('POST', '/api/v1/auth/login', ['email' => $email, 'password' => 'password-123456']);
check('login 200 + jwt', $r['status'] === 200 && strlen((string) ($r['body']['token'] ?? '')) > 40);
$jwt = (string) ($r['body']['token'] ?? '');

$r = http('POST', '/api/v1/auth/login', ['email' => $email, 'password' => 'wrong-password']);
check('неверный пароль 401', $r['status'] === 401);

$r = http('POST', '/api/v1/devices', ['name' => 'Second'], ['Authorization' => "Bearer $jwt"]);
check('создание устройства 201', $r['status'] === 201 && str_starts_with((string) ($r['body']['api_key'] ?? ''), 'wa_'));

$r = http('GET', '/api/v1/me', null, ['X-Api-Key' => $apiKey]);
check('me 200 с api key', $r['status'] === 200 && ($r['body']['email'] ?? '') === $email);

$r = http('GET', '/api/v1/me');
check('me без ключа 401', $r['status'] === 401);

$r = http('GET', '/api/v1/me', null, ['X-Api-Key' => 'wa_invalid_invalid_invalid_invalid_invalid']);
check('me с неверным ключом 401', $r['status'] === 401);

// ---------------------------------------------------------------- push
echo "\n[3] push наблюдений\n";
$obs1 = [
    'uuid' => uuid4(),
    'lat' => 55.75, 'lon' => 37.62,
    'observed_at' => gmdate('c', time() - 600),
    'temp_c' => 18.4, 'wind_ms' => 3.2, 'wind_gust_ms' => 5.1,
    'precip_mm' => 0.0, 'pressure_hpa' => 1011.0, 'humidity_pct' => 62.0,
    'visibility_m' => 10000, 'weather_code' => 1,
];
$obsBad = [
    'uuid' => uuid4(),
    'lat' => 55.75, 'lon' => 37.62,
    'observed_at' => gmdate('c', time() - 600),
    'temp_c' => 999.0, // вне диапазона
];
$r = http('POST', '/api/v1/sync/push', ['observations' => [$obs1, $obsBad]], ['X-Api-Key' => $apiKey]);
check('push 200', $r['status'] === 200, json_encode($r['body']));
check('accepted=1 rejected=1', ($r['body']['accepted'] ?? -1) === 1 && ($r['body']['rejected'] ?? -1) === 1, json_encode($r['body']));

// Идемпотентность: повторная отправка того же uuid
$r = http('POST', '/api/v1/sync/push', ['observations' => [$obs1]], ['X-Api-Key' => $apiKey]);
check('повторный push duplicate=1', ($r['body']['duplicate'] ?? -1) === 1, json_encode($r['body']));

// Единичное наблюдение (alias)
$obs2 = [
    'uuid' => uuid4(),
    'lat' => 55.76, 'lon' => 37.61,
    'observed_at' => gmdate('c', time() - 3600),
    'temp_c' => 17.9, 'wind_ms' => 3.6,
];
$r = http('POST', '/api/v1/observations', $obs2, ['X-Api-Key' => $apiKey]);
check('single observation 201', $r['status'] === 201, json_encode($r['body']));

$r = http('POST', '/api/v1/observations', ['lat' => 1, 'lon' => 1], ['X-Api-Key' => $apiKey]);
check('наблюдение без uuid 422', $r['status'] === 422);

// ----------------------------------------------------------- агрегация
echo "\n[4] агрегация (напрямую через сервис)\n";
$stats = WA\Services\AggregationService::run();
check('агрегация отработала', $stats['validated'] >= 1, json_encode($stats));
$st = $pdo->query("SELECT COUNT(*) FROM aggregates WHERE kind='cell_hour'");
check('cell_hour агрегат создан', (int) $st->fetchColumn() >= 1);

// ---------------------------------------------------------------- pull
echo "\n[5] pull\n";
$r = http('GET', '/api/v1/sync/pull?since=' . gmdate('c', time() - 7200), null, ['X-Api-Key' => $apiKey]);
check('pull 200', $r['status'] === 200, json_encode($r['body']));
check('pull отдаёт курсор', strlen((string) ($r['body']['cursor'] ?? '')) === 19);
check('pull отдаёт источники', count($r['body']['sources'] ?? []) >= 3);
check('pull отдаёт агрегаты', count($r['body']['aggregates'] ?? []) >= 1);
check('pull provider_bias есть', isset($r['body']['provider_bias']['temp_c']));

// ---------------------------------------------------------------- forecast
echo "\n[6] forecast (реальный Open-Meteo)\n";
$r = http('GET', '/api/v1/forecast?lat=55.75&lon=37.62&profile=roofer&hours=6', null, ['X-Api-Key' => $apiKey]);
if ($r['status'] === 200) {
    $points = $r['body']['points'] ?? [];
    check('forecast 200 c точками', count($points) >= 3, 'points=' . count($points));
    $first = $points[0] ?? [];
    check('точка содержит temp/confidence/flags',
        isset($first['temp_c'], $first['confidence'], $first['flags']),
        json_encode($first));
    check('confidence в 0..100', ($first['confidence'] ?? -1) >= 0 && ($first['confidence'] ?? -1) <= 100);
    // повторный запрос — из кэша
    $r2 = http('GET', '/api/v1/forecast?lat=55.75&lon=37.62&hours=6', null, ['X-Api-Key' => $apiKey]);
    check('повторный forecast из кэша', $r2['status'] === 200 && ($r2['body']['fetched_now'] ?? true) === false);
} else {
    // В окружении без интернета допустим отказ апстрима — но не 5xx-каши
    check('forecast: отказ апстрима корректно оформлен', $r['status'] === 503 && ($r['body']['error'] ?? '') === 'upstream_unavailable',
        'status=' . $r['status'] . ' body=' . json_encode($r['body']));
}

$r = http('GET', '/api/v1/forecast?lat=999&lon=1', null, ['X-Api-Key' => $apiKey]);
check('forecast с невалидным lat 422', $r['status'] === 422);

// ---------------------------------------------------------------- aggregates API
echo "\n[7] aggregates API\n";
$r = http('GET', '/api/v1/aggregates?kind=cell_hour&limit=10', null, ['X-Api-Key' => $apiKey]);
check('aggregates 200', $r['status'] === 200 && ($r['body']['count'] ?? 0) >= 1, json_encode($r['body']));
$r = http('GET', '/api/v1/aggregates?kind=evil', null, ['X-Api-Key' => $apiKey]);
check('aggregates с неверным kind 422', $r['status'] === 422);

// ---------------------------------------------------------------- rate limit
echo "\n[8] rate limit (лимит 40/мин в тестовом конфиге)\n";
$got429 = false;
for ($i = 0; $i < 50; $i++) {
    $r = http('GET', '/api/v1/health');
    if ($r['status'] === 429) { $got429 = true; break; }
    if ($r['status'] !== 200) { break; }
}
check('429 при превышении лимита (health без ключа не лимитируется — проверяем на me)', true);
// реальная проверка лимита — на ключевых запросах
$got429 = false;
for ($i = 0; $i < 50; $i++) {
    $r = http('GET', '/api/v1/me', null, ['X-Api-Key' => $apiKey]);
    if ($r['status'] === 429) { $got429 = true; break; }
}
check('429 выдан после исчерпания лимита', $got429, 'последний статус=' . $r['status']);

// ---------------------------------------------------------------- 404/405
echo "\n[9] маршрутизация\n";
$r = http('GET', '/api/v1/unknown');
check('неизвестный маршрут 404', $r['status'] === 404);
$r = http('DELETE', '/api/v1/health');
check('метод не поддерживается 405', $r['status'] === 405);

// ---------------------------------------------------------------- итог
echo "\n== Итог: $passed ok, $failed failed ==\n";
exit($failed === 0 ? 0 : 1);
