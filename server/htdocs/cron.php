<?php
/**
 * HTTP-обёртка cron-задач для shared-хостинга без CLI-крона:
 *   GET /cron.php?job=aggregate&token=CRON_SECRET
 *   GET /cron.php?job=fetch&token=CRON_SECRET
 *   GET /cron.php?job=cleanup&token=CRON_SECRET
 * Если панель хостинга умеет запускать CLI-крон — используйте cron/*.php.
 */

declare(strict_types=1);

require __DIR__ . '/../src/bootstrap.php';

header('Content-Type: application/json; charset=utf-8');

$token = (string) ($_GET['token'] ?? '');
$secret = (string) (wa_config()['cron']['secret'] ?? '');
if ($secret === '' || !hash_equals($secret, $token)) {
    http_response_code(403);
    echo json_encode(['error' => 'forbidden']);
    exit;
}

$job = (string) ($_GET['job'] ?? '');
$map = [
    'aggregate' => __DIR__ . '/../cron/aggregate.php',
    'fetch' => __DIR__ . '/../cron/fetch_forecasts.php',
    'cleanup' => __DIR__ . '/../cron/cleanup.php',
];
if (!isset($map[$job])) {
    http_response_code(404);
    echo json_encode(['error' => 'unknown_job', 'jobs' => array_keys($map)]);
    exit;
}

// Долгие задачи: снимаем ограничение времени (если позволяет хостинг)
@set_time_limit(120);

require $map[$job];
