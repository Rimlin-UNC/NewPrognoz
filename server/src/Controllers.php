<?php
declare(strict_types=1);

namespace WA;

use WA\Services\ForecastService;
use WA\Services\ProfileRules;
use WA\Services\SyncService;

/**
 * Обработчики REST API v1.
 */
final class Controllers {

    public static function health(): void {
        $pdo = Db::pdo();
        $dbOk = false;
        try {
            $dbOk = (bool) $pdo->query('SELECT 1')->fetchColumn();
        } catch (\Throwable $e) {
            $dbOk = false;
        }
        wa_json([
            'status' => $dbOk ? 'ok' : 'degraded',
            'service' => 'weather-pro-2.0',
            'version' => '2.0.0',
            'db' => $dbOk ? 'ok' : 'error',
            'driver' => Db::driver(),
            'time' => gmdate('c'),
        ], $dbOk ? 200 : 503);
    }

    public static function register(): void {
        $b = wa_body();
        $email = strtolower(trim((string) ($b['email'] ?? '')));
        $password = (string) ($b['password'] ?? '');
        if (!Validator::email($email)) {
            wa_error('validation', 'Некорректный email', 422);
        }
        if (strlen($password) < 8) {
            wa_error('validation', 'Пароль должен быть не короче 8 символов', 422);
        }
        $pdo = Db::pdo();
        $st = $pdo->prepare('SELECT id FROM users WHERE email = ?');
        $st->execute([$email]);
        if ($st->fetch()) {
            wa_error('conflict', 'Пользователь с таким email уже зарегистрирован', 409);
        }
        $now = wa_db_now();
        $userUuid = wa_uuid();
        $pdo->prepare('INSERT INTO users (uuid, email, password_hash, role, created_at, updated_at) VALUES (?,?,?,?,?,?)')
            ->execute([$userUuid, $email, password_hash($password, PASSWORD_BCRYPT), 'user', $now, $now]);
        $userId = (int) $pdo->lastInsertId();

        $device = Auth::createDevice(
            $userId,
            (string) ($b['device_name'] ?? 'Android'),
            (string) ($b['platform'] ?? 'android'),
            (string) ($b['app_version'] ?? '')
        );
        Logger::audit($userUuid, 'register', 'user', $userUuid);
        wa_json([
            'user_uuid' => $userUuid,
            'device_uuid' => $device['device_uuid'],
            'api_key' => $device['api_key'],
            'notice' => 'api_key показывается один раз — сохраните его',
        ], 201);
    }

    public static function login(): void {
        $b = wa_body();
        $email = strtolower(trim((string) ($b['email'] ?? '')));
        $password = (string) ($b['password'] ?? '');
        $pdo = Db::pdo();
        $st = $pdo->prepare('SELECT * FROM users WHERE email = ? AND deleted_at IS NULL LIMIT 1');
        $st->execute([$email]);
        $user = $st->fetch();
        if (!$user || !password_verify($password, (string) $user['password_hash'])) {
            Logger::audit('anonymous', 'login_failed', 'user', $email);
            wa_error('unauthorized', 'Неверный email или пароль', 401);
        }
        $jwt = Auth::issueJwt((string) $user['uuid'], (string) $user['role']);
        Logger::audit((string) $user['uuid'], 'login', 'user', (string) $user['uuid']);
        wa_json(['token' => $jwt['token'], 'expires_at' => $jwt['expires_at'], 'role' => $user['role']]);
    }

    public static function devices(): void {
        $jwt = Auth::requireJwt();
        $pdo = Db::pdo();
        $st = $pdo->prepare('SELECT id FROM users WHERE uuid = ? AND deleted_at IS NULL');
        $st->execute([$jwt['sub']]);
        $userId = $st->fetchColumn();
        if ($userId === false) {
            wa_error('unauthorized', 'Пользователь не найден', 401);
        }
        $b = wa_body();
        $device = Auth::createDevice(
            (int) $userId,
            (string) ($b['name'] ?? 'Device'),
            (string) ($b['platform'] ?? 'android'),
            (string) ($b['app_version'] ?? '')
        );
        Logger::audit($jwt['sub'], 'device_created', 'device', $device['device_uuid']);
        wa_json($device, 201);
    }

    public static function me(): void {
        [$user, $device] = Auth::requireApiKey();
        wa_json([
            'user_uuid' => $user['user_uuid'],
            'email' => $user['email'],
            'role' => $user['role'],
            'device_uuid' => $device['uuid'],
            'device_name' => $device['name'],
            'platform' => $device['platform'],
            'app_version' => $device['app_version'],
            'last_seen_at' => $device['last_seen_at'],
        ]);
    }

    public static function push(): void {
        [, $device] = Auth::requireApiKey();
        $result = SyncService::push($device, wa_body());
        wa_json($result, 200);
    }

    public static function pull(): void {
        [, $device] = Auth::requireApiKey();
        $since = isset($_GET['since']) ? (string) $_GET['since'] : null;
        wa_json(SyncService::pull($device, $since));
    }

    public static function observation(): void {
        [, $device] = Auth::requireApiKey();
        $body = wa_body();
        // Одиночное наблюдение → оборачиваем в батч из одного
        $result = SyncService::push($device, ['observations' => [$body]]);
        $first = $result['results'][0] ?? null;
        $status = $first['status'] ?? 'rejected';
        if ($status === 'rejected') {
            wa_json($result, 422);
        }
        wa_json($result, 201);
    }

    public static function forecast(): void {
        Auth::requireApiKey();
        $lat = filter_var($_GET['lat'] ?? null, FILTER_VALIDATE_FLOAT);
        $lon = filter_var($_GET['lon'] ?? null, FILTER_VALIDATE_FLOAT);
        if ($lat === false || $lat === null || $lon === false || $lon === null ||
            $lat < -90 || $lat > 90 || $lon < -180 || $lon > 180) {
            wa_error('validation', 'Параметры lat и lon обязательны (числа)', 422);
        }
        $hours = (int) ($_GET['hours'] ?? 48);
        $profile = (string) ($_GET['profile'] ?? 'universal');
        if (!in_array($profile, ProfileRules::PROFILES, true)) {
            $profile = 'universal';
        }
        try {
            wa_json(ForecastService::getForecast((float) $lat, (float) $lon, $hours, $profile));
        } catch (ApiException $e) {
            wa_error($e->errorCode, $e->getMessage(), $e->status);
        }
    }

    public static function aggregates(): void {
        Auth::requireApiKey();
        $pdo = Db::pdo();
        $kind = (string) ($_GET['kind'] ?? 'cell_hour');
        if (!in_array($kind, ['cell_hour', 'bias'], true)) {
            wa_error('validation', 'kind должен быть cell_hour или bias', 422);
        }
        $limit = min(500, max(1, (int) ($_GET['limit'] ?? 100)));
        $st = $pdo->prepare(
            'SELECT kind, cell_lat, cell_lon, hour_bucket, source_code, payload_json, sample_size, computed_at
             FROM aggregates WHERE kind = ? ORDER BY computed_at DESC LIMIT ' . $limit
        );
        $st->execute([$kind]);
        $rows = $st->fetchAll();
        wa_json([
            'kind' => $kind,
            'count' => count($rows),
            'items' => array_map(static function (array $r): array {
                return [
                    'cell' => ['lat' => (float) $r['cell_lat'], 'lon' => (float) $r['cell_lon']],
                    'hour_bucket' => $r['hour_bucket'],
                    'source_code' => $r['source_code'],
                    'payload' => json_decode((string) $r['payload_json'], true),
                    'sample_size' => (int) $r['sample_size'],
                    'computed_at' => $r['computed_at'],
                ];
            }, $rows),
        ]);
    }
}
