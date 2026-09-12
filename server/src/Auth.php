<?php
declare(strict_types=1);

namespace WA;

use PDO;

/**
 * Аутентификация:
 *  • API-ключи устройств (X-Api-Key) — основной способ для приложения;
 *  • JWT (HS256, чистый PHP) — для управления аккаунтом/устройствами;
 *  • сессии — только админ-панель.
 *
 * Пароли: password_hash (bcrypt). Ключи хранятся только как SHA-256.
 */
final class Auth {

    public static function generateApiKey(): string {
        return 'wa_' . bin2hex(random_bytes(24));
    }

    public static function hashApiKey(string $key): string {
        return hash('sha256', $key);
    }

    /** Обезличенный идентификатор устройства (для observations). */
    public static function deviceHash(string $deviceUuid): string {
        return hash_hmac('sha256', $deviceUuid, (string) (wa_config()['app']['pepper'] ?? 'pepper'));
    }

    public static function ipHash(): ?string {
        $ip = $_SERVER['REMOTE_ADDR'] ?? '';
        return $ip === '' ? null : hash_hmac('sha256', $ip, (string) (wa_config()['app']['pepper'] ?? 'pepper'));
    }

    // ------------------------------------------------------------------ JWT

    public static function issueJwt(string $userUuid, string $role, ?int $ttl = null): array {
        $ttl ??= (int) (wa_config()['app']['jwt_ttl'] ?? 3600);
        $now = time();
        $header = self::b64url(json_encode(['alg' => 'HS256', 'typ' => 'JWT']));
        $payload = self::b64url(json_encode([
            'sub' => $userUuid, 'role' => $role, 'iat' => $now, 'exp' => $now + $ttl,
        ]));
        $sig = self::b64url(hash_hmac('sha256', "$header.$payload", self::jwtSecret(), true));
        return ['token' => "$header.$payload.$sig", 'expires_at' => gmdate('c', $now + $ttl)];
    }

    /** @return array{sub:string,role:string,exp:int}|null */
    public static function verifyJwt(string $token): ?array {
        $parts = explode('.', $token);
        if (count($parts) !== 3) {
            return null;
        }
        [$h, $p, $s] = $parts;
        $expected = self::b64url(hash_hmac('sha256', "$h.$p", self::jwtSecret(), true));
        if (!hash_equals($expected, $s)) {
            return null;
        }
        /** @var mixed $header */
        $header = json_decode(self::b64urlDecode($h) ?: '', true);
        /** @var mixed $payload */
        $payload = json_decode(self::b64urlDecode($p) ?: '', true);
        if (!is_array($header) || !is_array($payload)) {
            return null;
        }
        if (($header['alg'] ?? '') !== 'HS256') {
            return null;
        }
        $exp = (int) ($payload['exp'] ?? 0);
        if ($exp < time()) {
            return null;
        }
        return [
            'sub' => (string) ($payload['sub'] ?? ''),
            'role' => (string) ($payload['role'] ?? 'user'),
            'exp' => $exp,
        ];
    }

    private static function jwtSecret(): string {
        return (string) (wa_config()['app']['pepper'] ?? '');
    }

    private static function b64url(string $data): string {
        return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
    }

    private static function b64urlDecode(string $data): string|false {
        return base64_decode(strtr($data, '-_', '+/'));
    }

    // ------------------------------------------------------------- Device API

    /**
     * Требует валидный X-Api-Key. Возвращает [user, device].
     * Обновляет last_seen_at не чаще раза в минуту.
     */
    public static function requireApiKey(): array {
        $key = $_SERVER['HTTP_X_API_KEY'] ?? '';
        if (!is_string($key) || $key === '') {
            wa_error('unauthorized', 'Требуется заголовок X-Api-Key', 401);
        }
        if (!RateLimiter::check(self::hashApiKey($key), (int) (wa_config()['app']['rate_limit'] ?? 120))) {
            wa_error('rate_limited', 'Превышен лимит запросов, попробуйте позже', 429,
                ['retry_after_seconds' => 60]);
        }
        $pdo = Db::pdo();
        $st = $pdo->prepare(
            'SELECT d.*, u.uuid AS user_uuid, u.email, u.role, u.deleted_at AS user_deleted
             FROM devices d JOIN users u ON u.id = d.user_id
             WHERE d.api_key_hash = ? AND d.deleted_at IS NULL AND u.deleted_at IS NULL LIMIT 1'
        );
        $st->execute([self::hashApiKey($key)]);
        $row = $st->fetch();
        if (!$row) {
            wa_error('unauthorized', 'Недействительный API-ключ', 401);
        }
        // last_seen — не чаще раза в минуту (снижаем нагрузку записи)
        if (empty($row['last_seen_at']) || strtotime((string) $row['last_seen_at']) < time() - 60) {
            $up = $pdo->prepare('UPDATE devices SET last_seen_at = ?, updated_at = ? WHERE id = ?');
            $up->execute([wa_db_now(), wa_db_now(), (int) $row['id']]);
        }
        return [$row, $row];
    }

    /** Требует Bearer JWT. Возвращает payload. */
    public static function requireJwt(): array {
        $auth = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
        if (!preg_match('/^Bearer\s+(.+)$/i', (string) $auth, $m)) {
            wa_error('unauthorized', 'Требуется заголовок Authorization: Bearer <jwt>', 401);
        }
        $payload = self::verifyJwt(trim($m[1]));
        if ($payload === null) {
            wa_error('unauthorized', 'Недействительный или просроченный токен', 401);
        }
        return $payload;
    }

    /** Создание устройства для текущего пользователя (по JWT). */
    public static function createDevice(int $userId, string $name, string $platform, string $appVersion): array {
        $pdo = Db::pdo();
        $apiKey = self::generateApiKey();
        $now = wa_db_now();
        $uuid = wa_uuid();
        $st = $pdo->prepare(
            'INSERT INTO devices (uuid, user_id, name, platform, app_version, api_key_hash, created_at, updated_at)
             VALUES (?,?,?,?,?,?,?,?)'
        );
        $st->execute([$uuid, $userId, mb_substr($name, 0, 120), mb_substr($platform, 0, 30),
            mb_substr($appVersion, 0, 20), self::hashApiKey($apiKey), $now, $now]);
        return ['device_uuid' => $uuid, 'api_key' => $apiKey];
    }
}
