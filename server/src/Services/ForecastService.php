<?php
declare(strict_types=1);

namespace WA\Services;

use WA\Auth;
use WA\Db;
use WA\Logger;
use WA\Schema;

/**
 * Серверный прогноз:
 *  1. кэш в таблице forecasts (TTL из конфига, по умолчанию 20 минут);
 *  2. загрузка Open-Meteo;
 *  3. bias-коррекция по агрегатам верификации (aggregates kind='bias');
 *  4. confidence-скор (затухание 0.92^(h/24), штраф за известное смещение);
 *  5. профессиональные флаги по профилю (кровельщик/лётчик/рыбак/альпинист).
 */
final class ForecastService {

    private const GRID = 0.05; // шаг кэша ~5 км

    public static function getForecast(float $lat, float $lon, int $hours, string $profile): array {
        $hours = max(1, min(168, $hours));
        $cellLat = round($lat / self::GRID) * self::GRID;
        $cellLon = round($lon / self::GRID) * self::GRID;
        $pdo = Db::pdo();
        $ttl = (int) (wa_config()['open_meteo']['cache_ttl'] ?? 1200);

        $sourceId = self::sourceId('open-meteo');
        $points = self::fromCache($pdo, $sourceId, $cellLat, $cellLon, $hours, $ttl);

        $fetched = false;
        if ($points === null) {
            $raw = OpenMeteoClient::fetchForecast($cellLat, $cellLon, $hours);
            if ($raw === null) {
                // Попытка устаревшего кэша вместо отказа
                $points = self::fromCache($pdo, $sourceId, $cellLat, $cellLon, $hours, 86400);
                if ($points === null) {
                    self::markSource('open-meteo', 'error');
                    throw new \WA\ApiException(503, 'upstream_unavailable',
                        'Источник прогноза временно недоступен');
                }
                self::markSource('open-meteo', 'stale');
            } else {
                $points = self::store($pdo, $sourceId, $cellLat, $cellLon, $raw, $hours);
                $fetched = true;
                self::markSource('open-meteo', 'ok');
            }
        }

        $bias = self::bias($pdo);
        $now = time();
        $out = [];
        foreach ($points as $p) {
            $targetTs = strtotime((string) $p['target_time']);
            $hoursAhead = max(0.0, ($targetTs - $now) / 3600.0);
            $temp = $p['temp_c'] !== null ? (float) $p['temp_c'] - $bias['temp_c'] : null;
            $wind = $p['wind_ms'] !== null ? max(0.0, (float) $p['wind_ms'] - $bias['wind_ms']) : null;
            $confidence = 100.0 * pow(0.92, $hoursAhead / 24.0) * (1.0 - min(0.35, abs($bias['temp_c']) / 6.0));
            $out[] = [
                'time' => gmdate('c', (int) $targetTs),
                'temp_c' => $temp === null ? null : round($temp, 1),
                'wind_ms' => $wind === null ? null : round($wind, 1),
                'wind_gust_ms' => $p['wind_gust_ms'] !== null ? round((float) $p['wind_gust_ms'] - $bias['wind_ms'], 1) : null,
                'precip_mm' => $p['precip_mm'] !== null ? round((float) $p['precip_mm'], 2) : null,
                'precip_prob' => $p['precip_prob'] !== null ? round((float) $p['precip_prob'], 0) : null,
                'pressure_hpa' => $p['pressure_hpa'] !== null ? round((float) $p['pressure_hpa'], 0) : null,
                'humidity_pct' => $p['humidity_pct'] !== null ? round((float) $p['humidity_pct'], 0) : null,
                'visibility_m' => $p['visibility_m'] !== null ? (int) $p['visibility_m'] : null,
                'cloud_pct' => $p['cloud_pct'] !== null ? round((float) $p['cloud_pct'], 0) : null,
                'weather_code' => $p['weather_code'] !== null ? (int) $p['weather_code'] : null,
                'confidence' => (int) round(max(5.0, min(100.0, $confidence))),
                'flags' => ProfileRules::flags($profile, $temp, $wind,
                    $p['wind_gust_ms'] !== null ? (float) $p['wind_gust_ms'] : null,
                    $p['precip_mm'] !== null ? (float) $p['precip_mm'] : null,
                    $p['visibility_m'] !== null ? (float) $p['visibility_m'] : null,
                    $p['weather_code'] !== null ? (int) $p['weather_code'] : null,
                    $temp !== null && $temp <= 0.0),
            ];
        }

        return [
            'issued_at' => gmdate('c'),
            'fetched_now' => $fetched,
            'lat' => $lat, 'lon' => $lon,
            'cell' => ['lat' => $cellLat, 'lon' => $cellLon],
            'bias_applied' => $bias['sample_size'] > 0,
            'bias' => $bias,
            'profile' => $profile,
            'points' => $out,
            'attribution' => 'Данные: Open-Meteo (CC BY 4.0), bias-коррекция Weather Pro 2.0',
        ];
    }

    // --------------------------------------------------------------- internal

    private static function sourceId(string $code): int {
        $pdo = Db::pdo();
        $st = $pdo->prepare('SELECT id FROM forecast_sources WHERE code = ?');
        $st->execute([$code]);
        $id = $st->fetchColumn();
        if ($id === false) {
            $pdo->prepare('INSERT INTO forecast_sources (code, name, type, base_weight, active) VALUES (?,?,?,?,1)')
                ->execute([$code, $code, 'api', 1.0]);
            $id = $pdo->lastInsertId();
        }
        return (int) $id;
    }

    private static function markSource(string $code, string $status): void {
        try {
            $pdo = Db::pdo();
            $pdo->prepare('UPDATE forecast_sources SET last_status = ?, last_run_at = ? WHERE code = ?')
                ->execute([$status, wa_db_now(), $code]);
            $pdo->prepare('UPDATE api_sources SET last_status = ?, last_run_at = ? WHERE code = ?')
                ->execute([$status, wa_db_now(), $code]);
        } catch (\Throwable $e) {
            Logger::app('warn', 'markSource: ' . $e->getMessage());
        }
    }

    /** Свежие точки из кэша или null. */
    private static function fromCache(\PDO $pdo, int $sourceId, float $lat, float $lon, int $hours, int $ttl): ?array {
        $st = $pdo->prepare(
            'SELECT * FROM forecasts WHERE source_id = ? AND lat = ? AND lon = ?
             AND target_time >= ? AND issued_at >= ? ORDER BY target_time ASC LIMIT 200'
        );
        $st->execute([$sourceId, $lat, $lon, gmdate('Y-m-d H:i:s', time() - 1800),
            gmdate('Y-m-d H:i:s', time() - $ttl)]);
        $rows = $st->fetchAll();
        if (count($rows) < 3) {
            return null;
        }
        return $rows;
    }

    /** Сохраняет точки прогноза в кэш (замена ячейки) и возвращает их. */
    private static function store(\PDO $pdo, int $sourceId, float $lat, float $lon, array $raw, int $hours): array {
        $hourly = $raw['hourly'];
        $times = (array) ($hourly['time'] ?? []);
        $issuedAt = wa_db_now();
        $pdo->beginTransaction();
        try {
            $pdo->prepare('DELETE FROM forecasts WHERE source_id = ? AND lat = ? AND lon = ?')
                ->execute([$sourceId, $lat, $lon]);
            $ins = $pdo->prepare(
                'INSERT INTO forecasts (source_id, lat, lon, issued_at, target_time, temp_c, wind_ms,
                 wind_gust_ms, precip_mm, precip_prob, pressure_hpa, humidity_pct, visibility_m,
                 cloud_pct, weather_code, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)'
            );
            $rows = [];
            $minTs = time() - 3600;
            $maxTs = time() + $hours * 3600 + 7200;
            foreach ($times as $i => $t) {
                $ts = strtotime((string) $t);
                if ($ts === false || $ts < $minTs || $ts > $maxTs) {
                    continue;
                }
                $row = [
                    'source_id' => $sourceId, 'lat' => $lat, 'lon' => $lon, 'issued_at' => $issuedAt,
                    'target_time' => gmdate('Y-m-d H:i:s', $ts),
                    'temp_c' => $hourly['temperature_2m'][$i] ?? null,
                    'wind_ms' => $hourly['wind_speed_10m'][$i] ?? null,
                    'wind_gust_ms' => $hourly['wind_gusts_10m'][$i] ?? null,
                    'precip_mm' => $hourly['precipitation'][$i] ?? null,
                    'precip_prob' => $hourly['precipitation_probability'][$i] ?? null,
                    'pressure_hpa' => $hourly['pressure_msl'][$i] ?? null,
                    'humidity_pct' => $hourly['relative_humidity_2m'][$i] ?? null,
                    'visibility_m' => $hourly['visibility'][$i] ?? null,
                    'cloud_pct' => $hourly['cloud_cover'][$i] ?? null,
                    'weather_code' => $hourly['weather_code'][$i] ?? null,
                    'created_at' => $issuedAt,
                ];
                $ins->execute(array_values($row));
                $rows[] = $row;
            }
            // model_runs — журнал загрузок
            $pdo->prepare('INSERT INTO model_runs (source_id, run_time, region, status, points, started_at, finished_at)
                           VALUES (?,?,?,?,?,?,?)')
                ->execute([$sourceId, $issuedAt, sprintf('%.2f,%.2f', $lat, $lon), 'ok', count($rows), $issuedAt, wa_db_now()]);
            $pdo->commit();
            return $rows;
        } catch (\Throwable $e) {
            $pdo->rollBack();
            Logger::app('error', 'forecast store: ' . $e->getMessage());
            return [];
        }
    }

    /** Глобальное смещение источника (из верификации) или нули. */
    private static function bias(\PDO $pdo): array {
        $st = $pdo->prepare(
            "SELECT payload_json FROM aggregates
             WHERE kind = 'bias' AND source_code = 'open-meteo' AND cell_lat = 0 AND cell_lon = 0
             ORDER BY computed_at DESC LIMIT 1"
        );
        $st->execute();
        $json = $st->fetchColumn();
        if (is_string($json)) {
            /** @var mixed $data */
            $data = json_decode($json, true);
            if (is_array($data)) {
                return [
                    'temp_c' => (float) ($data['temp_c'] ?? 0),
                    'wind_ms' => (float) ($data['wind_ms'] ?? 0),
                    'mae_temp' => (float) ($data['mae_temp'] ?? 0),
                    'sample_size' => (int) ($data['n'] ?? 0),
                ];
            }
        }
        return ['temp_c' => 0.0, 'wind_ms' => 0.0, 'mae_temp' => 0.0, 'sample_size' => 0];
    }
}
