<?php
/**
 * Cron (каждые 30–60 минут): предзагрузка прогнозов для «горячих» точек —
 * ячеек, по которым недавно были наблюдения пользователей или запросы.
 * Запуск: php cron/fetch_forecasts.php  (или htdocs/cron.php?job=fetch)
 */

declare(strict_types=1);

require __DIR__ . '/../src/bootstrap.php';

$pdo = WA\Db::pdo();

// Горячие ячейки: наблюдения за последние 24 ч (устойчивый спрос)
$rows = $pdo->query(
    "SELECT ROUND(lat / 0.05) * 0.05 AS clat, ROUND(lon / 0.05) * 0.05 AS clon, COUNT(*) AS n
     FROM observations WHERE observed_at >= '" . gmdate('Y-m-d H:i:s', time() - 86400) . "'
     GROUP BY clat, clon ORDER BY n DESC LIMIT 50"
)->fetchAll();

$hot = array_map(static fn($r) => [(float) $r['clat'], (float) $r['clon']], $rows);

// Плюс точки из последних model_runs (запрошенные через API)
foreach ($pdo->query(
    "SELECT region FROM model_runs WHERE started_at >= '" . gmdate('Y-m-d H:i:s', time() - 86400) . "'
     GROUP BY region ORDER BY MAX(started_at) DESC LIMIT 50"
)->fetchAll() as $r) {
    $parts = explode(',', (string) $r['region']);
    if (count($parts) === 2 && is_numeric($parts[0]) && is_numeric($parts[1])) {
        $hot[] = [(float) $parts[0], (float) $parts[1]];
    }
}

$results = [];
foreach ($hot as [$lat, $lon]) {
    // Прогноз берётся через сервис — с кэшем; freshness проверяется внутри
    try {
        $f = WA\Services\ForecastService::getForecast($lat, $lon, 48, 'universal');
        $results[] = ['lat' => $lat, 'lon' => $lon, 'points' => count($f['points']), 'fetched_now' => $f['fetched_now']];
    } catch (Throwable $e) {
        $results[] = ['lat' => $lat, 'lon' => $lon, 'error' => $e->getMessage()];
    }
}

echo json_encode(['job' => 'fetch_forecasts', 'cells' => count($results), 'results' => $results],
    JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE), PHP_EOL;
