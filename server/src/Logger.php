<?php
declare(strict_types=1);

namespace WA;

/**
 * Журналирование: audit_log (значимые действия) + error_log (диагностика).
 */
final class Logger {

    public static function audit(string $actor, string $action, ?string $entity = null, ?string $entityId = null): void {
        try {
            $pdo = Db::pdo();
            $st = $pdo->prepare(
                'INSERT INTO audit_log (actor, action, entity, entity_id, ip_hash, created_at) VALUES (?,?,?,?,?,?)'
            );
            $st->execute([$actor, $action, $entity, $entityId, Auth::ipHash(), wa_db_now()]);
        } catch (\Throwable $e) {
            error_log('[audit] ' . $e->getMessage());
        }
    }

    public static function app(string $level, string $message, array $context = []): void {
        $line = sprintf('[%s] %s: %s %s', wa_db_now(), strtoupper($level), $message,
            $context ? json_encode($context, JSON_UNESCAPED_UNICODE) : '');
        error_log($line);
    }

    public static function syncLog(string $deviceUuid, string $direction, int $opsIn, int $opsOk, int $httpStatus): void {
        try {
            $pdo = Db::pdo();
            $st = $pdo->prepare(
                'INSERT INTO sync_log (device_uuid, direction, ops_in, ops_ok, http_status, ip_hash, created_at) VALUES (?,?,?,?,?,?,?)'
            );
            $st->execute([$deviceUuid, $direction, $opsIn, $opsOk, $httpStatus, Auth::ipHash(), wa_db_now()]);
        } catch (\Throwable $e) {
            error_log('[synclog] ' . $e->getMessage());
        }
    }
}
