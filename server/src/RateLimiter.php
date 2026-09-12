<?php
declare(strict_types=1);

namespace WA;

use PDO;

/**
 * Rate limiting на таблице rate_limits (1 строка на ключ×минуту).
 * Очистку старых строк делает cron/cleanup.php.
 */
final class RateLimiter {

    public static function check(string $keyHash, int $limitPerMinute): bool {
        if ($limitPerMinute <= 0) {
            return true; // лимит отключён
        }
        $pdo = Db::pdo();
        $minute = intdiv(time(), 60);
        if (Db::driver() === 'mysql') {
            $pdo->prepare(
                'INSERT INTO rate_limits (minute_ts, key_hash, count) VALUES (?,?,1)
                 ON DUPLICATE KEY UPDATE count = count + 1'
            )->execute([$minute, $keyHash]);
            $count = (int) $pdo->query(
                'SELECT count FROM rate_limits WHERE minute_ts = ' . $minute .
                ' AND key_hash = ' . $pdo->quote($keyHash)
            )->fetchColumn();
        } else {
            $pdo->prepare(
                'INSERT INTO rate_limits (minute_ts, key_hash, count) VALUES (?,?,1)
                 ON CONFLICT(minute_ts, key_hash) DO UPDATE SET count = count + 1'
            )->execute([$minute, $keyHash]);
            $st = $pdo->prepare('SELECT count FROM rate_limits WHERE minute_ts = ? AND key_hash = ?');
            $st->execute([$minute, $keyHash]);
            $count = (int) $st->fetchColumn();
        }
        return $count <= $limitPerMinute;
    }
}
