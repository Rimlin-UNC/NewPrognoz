<?php
declare(strict_types=1);

namespace WA\Services;

use WA\Auth;
use WA\Db;
use WA\Logger;
use WA\Validator;

/**
 * Синхронизация offline-first:
 *  • push: батч наблюдений, идемпотентность по uuid, валидация, обезличивание;
 *  • pull: курсорная выдача агрегатов (bias, cell_hour) и метаданных источников.
 *
 * Конфликты: наблюдения — append-only факты (дедуп по uuid); пользовательские
 * настройки — last-write-wins на клиенте (сервер их не редактирует).
 */
final class SyncService {

    private const MAX_BATCH = 200;

    /** @return array{accepted:int, duplicate:int, rejected:int, results:array} */
    public static function push(array $device, array $body): array {
        $items = $body['observations'] ?? null;
        if (!is_array($items)) {
            wa_error('bad_request', 'Ожидается массив observations', 422);
        }
        if (count($items) > self::MAX_BATCH) {
            wa_error('too_many_items', 'Максимум ' . self::MAX_BATCH . ' наблюдений за запрос', 413);
        }
        $pdo = Db::pdo();
        $deviceHash = Auth::deviceHash((string) $device['uuid']);
        $now = wa_db_now();

        $results = [];
        $accepted = 0; $duplicate = 0; $rejected = 0;

        $insert = $pdo->prepare(
            'INSERT INTO observations (uuid, device_hash, lat, lon, observed_at, temp_c, wind_ms,
             wind_gust_ms, precip_mm, pressure_hpa, humidity_pct, visibility_m, weather_code,
             quality, received_at, updated_at)
             VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,\'raw\',?,?)'
        );
        $staging = $pdo->prepare(
            'INSERT INTO sync_queue (device_uuid, op_uuid, entity, payload_json, status, received_at)
             VALUES (?,?,?,?,\'processed\',?)'
        );
        $exists = $pdo->prepare('SELECT 1 FROM observations WHERE uuid = ?');

        foreach ($items as $item) {
            if (!is_array($item)) {
                $results[] = ['uuid' => null, 'status' => 'rejected', 'reason' => 'not_an_object'];
                $rejected++;
                continue;
            }
            $v = Validator::observation($item);
            $uuid = (string) ($item['uuid'] ?? '');
            if (!$v['ok']) {
                $results[] = ['uuid' => $uuid, 'status' => 'rejected', 'reason' => implode('; ', $v['errors'])];
                $rejected++;
                continue;
            }
            $exists->execute([$uuid]);
            if ($exists->fetch()) {
                $results[] = ['uuid' => $uuid, 'status' => 'duplicate'];
                $duplicate++;
                continue;
            }
            $c = $v['clean'];
            $insert->execute([
                $c['uuid'], $deviceHash, $c['lat'], $c['lon'], $c['observed_at'],
                $c['temp_c'], $c['wind_ms'], $c['wind_gust_ms'], $c['precip_mm'],
                $c['pressure_hpa'], $c['humidity_pct'],
                $c['visibility_m'] === null ? null : (int) $c['visibility_m'],
                $c['weather_code'] === null ? null : (int) $c['weather_code'],
                $now, $now,
            ]);
            $staging->execute([$device['uuid'], $uuid, 'observation', json_encode($c, JSON_UNESCAPED_UNICODE), $now]);
            $results[] = ['uuid' => $uuid, 'status' => 'accepted'];
            $accepted++;
        }

        Logger::syncLog((string) $device['uuid'], 'push', count($items), $accepted, 200);
        return ['accepted' => $accepted, 'duplicate' => $duplicate, 'rejected' => $rejected, 'results' => $results];
    }

    /** @return array{cursor:string, provider_bias:array, aggregates:array, sources:array, server_time:string} */
    public static function pull(array $device, ?string $since): array {
        $pdo = Db::pdo();
        $sinceDb = Validator::parseTime((string) $since) ?? gmdate('Y-m-d H:i:s', time() - 86400);

        // Агрегаты, обновлённые после курсора (лимит защиты)
        $st = $pdo->prepare(
            'SELECT kind, cell_lat, cell_lon, hour_bucket, source_code, payload_json, sample_size, computed_at
             FROM aggregates WHERE computed_at > ? ORDER BY computed_at DESC LIMIT 500'
        );
        $st->execute([$sinceDb]);
        $aggregates = array_map(static function (array $row): array {
            /** @var mixed $p */
            $p = json_decode((string) $row['payload_json'], true);
            return [
                'kind' => $row['kind'],
                'cell' => ['lat' => (float) $row['cell_lat'], 'lon' => (float) $row['cell_lon']],
                'hour_bucket' => $row['hour_bucket'],
                'source_code' => $row['source_code'],
                'payload' => is_array($p) ? $p : new \stdClass(),
                'sample_size' => (int) $row['sample_size'],
                'computed_at' => $row['computed_at'],
            ];
        }, $st->fetchAll());

        // Глобальный bias источника (для коррекции ансамбля на клиенте)
        $bias = ['temp_c' => 0.0, 'wind_ms' => 0.0, 'sample_size' => 0];
        $stb = $pdo->prepare(
            "SELECT payload_json FROM aggregates WHERE kind = 'bias' AND source_code = 'open-meteo'
             AND cell_lat = 0 AND cell_lon = 0 ORDER BY computed_at DESC LIMIT 1"
        );
        $stb->execute();
        $bj = $stb->fetchColumn();
        if (is_string($bj)) {
            /** @var mixed $b */
            $b = json_decode($bj, true);
            if (is_array($b)) {
                $bias = [
                    'temp_c' => round((float) ($b['temp_c'] ?? 0), 2),
                    'wind_ms' => round((float) ($b['wind_ms'] ?? 0), 2),
                    'sample_size' => (int) ($b['n'] ?? 0),
                ];
            }
        }

        $sources = $pdo->query('SELECT code, name, type, base_weight, active FROM forecast_sources ORDER BY base_weight DESC')
            ->fetchAll();

        $cursor = wa_db_now();
        Logger::syncLog((string) $device['uuid'], 'pull', 0, count($aggregates), 200);
        return [
            'cursor' => $cursor,
            'server_time' => gmdate('c'),
            'provider_bias' => $bias,
            'aggregates' => $aggregates,
            'sources' => $sources,
        ];
    }
}
