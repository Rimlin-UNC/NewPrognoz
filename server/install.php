<?php
/**
 * Установщик Weather Professional 2.0.
 *
 * CLI:   php install.php
 * HTTP:  /../install.php  — только при свежей БД; после установки удалите файл.
 *
 * Действия: создаёт таблицы (идемпотентно), сеет справочники,
 * создаёт администратора из config.php (admin_email / admin_password).
 */

declare(strict_types=1);

require __DIR__ . '/src/bootstrap.php';

$isCli = PHP_SAPI === 'cli';

/** @var array{created:array, admin:array} $result */
$result = ['created' => [], 'admin' => []];

try {
    $pdo = WA\Db::pdo();
    $result['created'] = WA\Schema::install($pdo);

    // Администратор
    $cfg = wa_config()['app'] ?? [];
    $email = strtolower(trim((string) ($cfg['admin_email'] ?? '')));
    $password = (string) ($cfg['admin_password'] ?? '');
    if ($email !== '' && $password !== '' && !str_starts_with($password, 'ЗАМЕНИТЕ')) {
        $st = $pdo->prepare('SELECT id FROM users WHERE email = ?');
        $st->execute([$email]);
        if (!$st->fetch()) {
            $now = wa_db_now();
            $pdo->prepare('INSERT INTO users (uuid, email, password_hash, role, created_at, updated_at) VALUES (?,?,?,?,?,?)')
                ->execute([wa_uuid(), $email, password_hash($password, PASSWORD_BCRYPT), 'admin', $now, $now]);
            WA\Logger::audit('installer', 'admin_created', 'user', $email);
            $result['admin'] = ['created' => true, 'email' => $email];
        } else {
            $result['admin'] = ['created' => false, 'reason' => 'already_exists', 'email' => $email];
        }
    } else {
        $result['admin'] = ['created' => false, 'reason' => 'admin_email/admin_password не заданы или не заменены в config.php'];
    }

    $result['ok'] = true;
    $result['schema_version'] = WA\Schema::VERSION;
    $result['driver'] = WA\Db::driver();
} catch (Throwable $e) {
    $result = ['ok' => false, 'error' => $e->getMessage()];
}

$payload = json_encode($result, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
if ($isCli) {
    echo $payload, PHP_EOL;
    exit($result['ok'] ? 0 : 1);
}
header('Content-Type: application/json; charset=utf-8');
http_response_code($result['ok'] ? 200 : 500);
echo $payload;
