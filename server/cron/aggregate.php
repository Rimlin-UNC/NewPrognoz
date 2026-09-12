<?php
/**
 * Cron (ежечасно): валидация наблюдений → cell_hour-агрегаты → bias-коррекция.
 * Запуск: php cron/aggregate.php  (или через htdocs/cron.php?job=aggregate)
 */

declare(strict_types=1);

require __DIR__ . '/../src/bootstrap.php';

$stats = WA\Services\AggregationService::run();
echo json_encode(['job' => 'aggregate', 'finished_at' => gmdate('c'), 'stats' => $stats],
    JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE), PHP_EOL;
