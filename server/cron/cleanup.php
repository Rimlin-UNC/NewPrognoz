<?php
/**
 * Cron (ежедневно): очистка устаревших данных.
 * Запуск: php cron/cleanup.php  (или htdocs/cron.php?job=cleanup)
 */

declare(strict_types=1);

require __DIR__ . '/../src/bootstrap.php';

$pdo = WA\Db::pdo();
$now = time();
$stats = [];

$purge = [
    // [таблица, поле, срок в днях, доп.условие]
    ['rate_limits', 'minute_ts', null, 'minute_ts < ' . intdiv($now - 86400, 60)],
    ['sync_queue', 'received_at', 7, null],
    ['sync_log', 'created_at', 30, null],
    ['audit_log', 'created_at', 90, null],
    ['forecasts', 'target_time', 7, null],
    ['model_runs', 'started_at', 30, null],
    ['observations', 'observed_at', 90, "quality = 'rejected'"],
    ['aggregates', 'computed_at', 365, "kind = 'cell_hour'"],
];

foreach ($purge as [$table, $column, $days, $extra]) {
    if ($days !== null) {
        $threshold = gmdate('Y-m-d H:i:s', $now - $days * 86400);
        $sql = "DELETE FROM `$table` WHERE `$column` < " . $pdo->quote($threshold);
    } else {
        $sql = "DELETE FROM `$table` WHERE $extra";
    }
    if ($extra !== null && $days !== null) {
        $sql .= " AND $extra";
    }
    $stats[$table] = $pdo->exec($sql);
}

echo json_encode(['job' => 'cleanup', 'finished_at' => gmdate('c'), 'deleted' => $stats],
    JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE), PHP_EOL;
