<?php
declare(strict_types=1);

namespace WA\Services;

use WA\Db;
use WA\Logger;
use WA\Validator;

/**
 * Агрегация и повышение точности (cron, ежечасно):
 *
 *  1. Валидация «сырых» наблюдений (диапазоны + частота + пространственные
 *     выбросы по MAD внутри ячейки-часа).
 *  2. Пространственно-временная кластеризация: ячейка 0.1° (~11 км) × час →
 *     медианы/перцентили → aggregates kind='cell_hour'.
 *  3. Верификация: прогноз (выданный ≥1 ч назад) vs факт (cell_hour) →
 *     ошибки источника → bias-коррекция (EMA, α=0.3) → aggregates kind='bias'
 *     (глобально и по ячейкам).
 *
 * Наблюдения обезличены (device_hash), агрегаты не содержат персональных данных.
 */
final class AggregationService {

    private const CELL = 0.1; // градуса, ~11 км
    private const EMA_ALPHA = 0.3;

    /** Основной цикл. @return array статистики */
    public static function run(): array {
        $stats = [
            'validated' => 0, 'rejected' => 0, 'cells' => 0,
            'verified_pairs' => 0, 'bias_updated' => 0,
        ];
        $stats = array_merge($stats, self::validateObservations());
        $stats['cells'] = self::buildCellHours();
        $verify = self::verifyForecasts();
        $stats['verified_pairs'] = $verify['pairs'];
        $stats['bias_updated'] = $verify['bias_rows'];
        Logger::app('info', 'aggregation done', $stats);
        return $stats;
    }

    // ------------------------------------------------------------- валидация

    private static function validateObservations(): array {
        $pdo = Db::pdo();
        $since = gmdate('Y-m-d H:i:s', time() - 172800); // 48 ч
        $st = $pdo->prepare(
            "SELECT * FROM observations WHERE quality = 'raw' AND observed_at >= ? ORDER BY observed_at ASC LIMIT 5000"
        );
        $st->execute([$since]);
        $rows = $st->fetchAll();

        $validated = 0; $rejected = 0;
        $upd = $pdo->prepare("UPDATE observations SET quality = ?, reject_reason = ?, updated_at = ? WHERE id = ?");

        // Частота: не более 1 наблюдения от устройства за 10 минут
        $lastByDevice = [];
        foreach ($rows as $row) {
            $reason = self::rejectReason($row, $lastByDevice);
            $upd->execute([$reason === null ? 'ok' : 'rejected', $reason, wa_db_now(), (int) $row['id']]);
            if ($reason === null) {
                $validated++;
                $lastByDevice[$row['device_hash']] = $row['observed_at'];
            } else {
                $rejected++;
            }
        }

        // Пространственные выбросы: внутри (ячейка × час) медиана ± max(4·MAD, допуск)
        $rejected += self::flagSpatialOutliers();

        return ['validated' => $validated, 'rejected' => $rejected];
    }

    private static function rejectReason(array $row, array &$lastByDevice): ?string {
        // Повторная проверка диапазонов (страховка)
        foreach ([
            ['temp_c', -60.0, 60.0], ['wind_ms', 0.0, 80.0], ['wind_gust_ms', 0.0, 120.0],
            ['precip_mm', 0.0, 300.0], ['pressure_hpa', 850.0, 1100.0],
            ['humidity_pct', 0.0, 100.0],
        ] as [$f, $min, $max]) {
            $v = $row[$f];
            if ($v !== null && ((float) $v < $min || (float) $v > $max)) {
                return 'range';
            }
        }
        // Частота
        $last = $lastByDevice[$row['device_hash']] ?? null;
        if ($last !== null && strtotime((string) $row['observed_at']) - strtotime((string) $last) < 600) {
            return 'rate';
        }
        return null;
    }

    private static function flagSpatialOutliers(): int {
        $pdo = Db::pdo();
        $since = gmdate('Y-m-d H:i:s', time() - 172800);
        $rows = $pdo->query(
            "SELECT id, lat, lon, observed_at, temp_c, wind_ms, pressure_hpa, humidity_pct
             FROM observations WHERE quality = 'ok' AND observed_at >= '$since' LIMIT 20000"
        )->fetchAll();
        $groups = [];
        foreach ($rows as $r) {
            $key = self::cellKey((float) $r['lat'], (float) $r['lon']) . '|' . substr((string) $r['observed_at'], 0, 13);
            $groups[$key][] = $r;
        }
        $flagged = 0;
        $upd = $pdo->prepare("UPDATE observations SET quality = 'rejected', reject_reason = 'outlier', updated_at = ? WHERE id = ?");
        $tolerance = ['temp_c' => 8.0, 'wind_ms' => 10.0, 'pressure_hpa' => 12.0, 'humidity_pct' => 40.0];
        foreach ($groups as $group) {
            if (count($group) < 3) {
                continue;
            }
            foreach (['temp_c', 'wind_ms', 'pressure_hpa', 'humidity_pct'] as $field) {
                $values = array_filter(array_map(static fn($r) => $r[$field] !== null ? (float) $r[$field] : null, $group),
                    static fn($v) => $v !== null);
                if (count($values) < 3) {
                    continue;
                }
                $median = self::median(array_values($values));
                $mad = self::median(array_map(static fn($v) => abs($v - $median), array_values($values)));
                $limit = max(4.0 * $mad, $tolerance[$field]);
                foreach ($group as $r) {
                    if ($r[$field] !== null && abs((float) $r[$field] - $median) > $limit) {
                        $upd->execute([wa_db_now(), (int) $r['id']]);
                        $flagged++;
                    }
                }
            }
        }
        return $flagged;
    }

    // ------------------------------------------------------------- cell_hour

    private static function buildCellHours(): int {
        $pdo = Db::pdo();
        $since = gmdate('Y-m-d H:i:s', time() - 172800);
        $rows = $pdo->query(
            "SELECT lat, lon, observed_at, temp_c, wind_ms, wind_gust_ms, precip_mm,
                    pressure_hpa, humidity_pct, visibility_m
             FROM observations WHERE quality = 'ok' AND observed_at >= '$since' LIMIT 20000"
        )->fetchAll();

        $groups = [];
        foreach ($rows as $r) {
            $hour = substr((string) $r['observed_at'], 0, 13) . ':00:00';
            $key = self::cellKey((float) $r['lat'], (float) $r['lon']) . '|' . $hour;
            $groups[$key]['cell'] = explode('|', self::cellKey((float) $r['lat'], (float) $r['lon']));
            $groups[$key]['hour'] = $hour;
            foreach (['temp_c', 'wind_ms', 'wind_gust_ms', 'precip_mm', 'pressure_hpa', 'humidity_pct', 'visibility_m'] as $f) {
                if ($r[$f] !== null) {
                    $groups[$key]['values'][$f][] = (float) $r[$f];
                }
            }
        }

        [$upsert, $fixed] = self::upsertStmt($pdo, 'cell_hour');
        $count = 0;
        foreach ($groups as $g) {
            $payload = ['n' => 0];
            foreach ($g['values'] ?? [] as $f => $vals) {
                sort($vals);
                $payload[$f] = [
                    'median' => round(self::median($vals), 1),
                    'p25' => round(self::percentile($vals, 25), 1),
                    'p75' => round(self::percentile($vals, 75), 1),
                    'n' => count($vals),
                ];
                $payload['n'] = max($payload['n'], count($vals));
            }
            if ($payload['n'] === 0) {
                continue;
            }
            // Порядок параметров = порядок колонок: kind, cell_lat, cell_lon,
            // hour_bucket, source_code, payload_json, sample_size, computed_at
            $upsert->execute(array_merge($fixed, [
                (float) $g['cell'][0], (float) $g['cell'][1], $g['hour'], '',
                json_encode($payload, JSON_UNESCAPED_UNICODE), $payload['n'], wa_db_now(),
            ]));
            $count++;
        }
        return $count;
    }

    // ----------------------------------------------------------- верификация

    /** @return array{pairs:int, bias_rows:int} */
    private static function verifyForecasts(): array {
        $pdo = Db::pdo();
        $pairs = 0; $biasRows = 0;

        // Прогнозы за последние 24 ч, выданные минимум за час до цели
        $rows = $pdo->query(
            'SELECT f.source_id, s.code AS source_code, f.lat, f.lon, f.target_time, f.temp_c, f.wind_ms,
                    f.issued_at
             FROM forecasts f JOIN forecast_sources s ON s.id = f.source_id
             WHERE f.target_time BETWEEN ' . $pdo->quote(gmdate('Y-m-d H:i:s', time() - 86400)) . '
               AND ' . $pdo->quote(gmdate('Y-m-d H:i:s')) . '
               AND f.issued_at <= ' . $pdo->quote(gmdate('Y-m-d H:i:s', time() - 3600)) . '
             ORDER BY f.target_time ASC LIMIT 20000'
        )->fetchAll();

        // Индекс фактов по (ячейка|час)
        $facts = $pdo->query(
            "SELECT kind, cell_lat, cell_lon, hour_bucket, payload_json FROM aggregates WHERE kind = 'cell_hour'
             AND hour_bucket >= " . $pdo->quote(gmdate('Y-m-d H:i:s', time() - 90000)) . ' LIMIT 20000'
        )->fetchAll();
        $factIndex = [];
        foreach ($facts as $f) {
            $key = self::cellKey((float) $f['cell_lat'], (float) $f['cell_lon']) . '|' . substr((string) $f['hour_bucket'], 0, 13);
            /** @var mixed $p */
            $p = json_decode((string) $f['payload_json'], true);
            $factIndex[$key] = is_array($p) ? $p : [];
        }

        // Ошибки по источникам (глобально)
        $errors = [];
        foreach ($rows as $f) {
            $key = self::cellKey((float) $f['lat'], (float) $f['lon']) . '|' . substr((string) $f['target_time'], 0, 13);
            $fact = $factIndex[$key] ?? null;
            if ($fact === null) {
                continue;
            }
            $code = (string) $f['source_code'];
            if ($f['temp_c'] !== null && isset($fact['temp_c']['median'])) {
                $errors[$code]['temp'][] = (float) $f['temp_c'] - (float) $fact['temp_c']['median'];
                $pairs++;
            }
            if ($f['wind_ms'] !== null && isset($fact['wind_ms']['median'])) {
                $errors[$code]['wind'][] = (float) $f['wind_ms'] - (float) $fact['wind_ms']['median'];
            }
        }

        foreach ($errors as $code => $byVar) {
            $tempErrors = $byVar['temp'] ?? [];
            $windErrors = $byVar['wind'] ?? [];
            if ($tempErrors === [] && $windErrors === []) {
                continue;
            }
            $meanTemp = $tempErrors ? array_sum($tempErrors) / count($tempErrors) : 0.0;
            $meanWind = $windErrors ? array_sum($windErrors) / count($windErrors) : 0.0;
            $maeTemp = $tempErrors ? array_sum(array_map('abs', $tempErrors)) / count($tempErrors) : 0.0;

            // EMA с предыдущим значением
            $old = self::currentBias($pdo, $code);
            $newTemp = $old['temp_c'] * (1 - self::EMA_ALPHA) + $meanTemp * self::EMA_ALPHA;
            $newWind = $old['wind_ms'] * (1 - self::EMA_ALPHA) + $meanWind * self::EMA_ALPHA;
            $n = $old['n'] + count($tempErrors);

            [$upsert, $fixed] = self::upsertStmt($pdo, 'bias');
            $upsert->execute(array_merge($fixed, [
                0.0, 0.0, gmdate('Y-m-d H:i:s', 0), $code,
                json_encode([
                    'temp_c' => round($newTemp, 2), 'wind_ms' => round($newWind, 2),
                    'mae_temp' => round($maeTemp, 2), 'n' => $n,
                ], JSON_UNESCAPED_UNICODE), $n, wa_db_now(),
            ]));
            $biasRows++;
        }
        return ['pairs' => $pairs, 'bias_rows' => $biasRows];
    }

    private static function currentBias(\PDO $pdo, string $code): array {
        $st = $pdo->prepare(
            "SELECT payload_json FROM aggregates WHERE kind='bias' AND source_code=? AND cell_lat=0 AND cell_lon=0
             ORDER BY computed_at DESC LIMIT 1"
        );
        $st->execute([$code]);
        $json = $st->fetchColumn();
        if (is_string($json)) {
            /** @var mixed $d */
            $d = json_decode($json, true);
            if (is_array($d)) {
                return ['temp_c' => (float) ($d['temp_c'] ?? 0), 'wind_ms' => (float) ($d['wind_ms'] ?? 0), 'n' => (int) ($d['n'] ?? 0)];
            }
        }
        return ['temp_c' => 0.0, 'wind_ms' => 0.0, 'n' => 0];
    }

    // --------------------------------------------------------------- helpers

    private static function cellKey(float $lat, float $lon): string {
        return sprintf('%.1f|%.1f', round($lat / self::CELL) * self::CELL, round($lon / self::CELL) * self::CELL);
    }

    /** @return array{0:\PDOStatement, 1:array} statement + фиксированные параметры (kind) */
    private static function upsertStmt(\PDO $pdo, string $kind): array {
        $sql = Db::driver() === 'mysql'
            ? "INSERT INTO aggregates (kind, cell_lat, cell_lon, hour_bucket, source_code, payload_json, sample_size, computed_at)
               VALUES (?,?,?,?,?,?,?,?)
               ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json), sample_size = VALUES(sample_size), computed_at = VALUES(computed_at)"
            : "INSERT INTO aggregates (kind, cell_lat, cell_lon, hour_bucket, source_code, payload_json, sample_size, computed_at)
               VALUES (?,?,?,?,?,?,?,?)
               ON CONFLICT(kind, cell_lat, cell_lon, hour_bucket, source_code) DO UPDATE SET
                 payload_json = excluded.payload_json, sample_size = excluded.sample_size, computed_at = excluded.computed_at";
        return [$pdo->prepare($sql), [$kind]];
    }

    public static function median(array $sortedValues): float {
        $n = count($sortedValues);
        if ($n === 0) {
            return 0.0;
        }
        sort($sortedValues);
        $mid = intdiv($n, 2);
        return $n % 2 ? (float) $sortedValues[$mid] : ((float) $sortedValues[$mid - 1] + (float) $sortedValues[$mid]) / 2.0;
    }

    public static function percentile(array $sortedValues, float $p): float {
        $n = count($sortedValues);
        if ($n === 0) {
            return 0.0;
        }
        sort($sortedValues);
        $idx = min($n - 1, max(0, (int) floor(($p / 100.0) * ($n - 1))));
        return (float) $sortedValues[$idx];
    }
}
