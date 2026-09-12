<?php
declare(strict_types=1);

namespace WA;

/**
 * Валидация наблюдений пользователей (защита от мусора в агрегатах).
 */
final class Validator {

    /** Диапазоны физических величин. */
    private const RANGES = [
        'temp_c' => [-60.0, 60.0],
        'wind_ms' => [0.0, 80.0],
        'wind_gust_ms' => [0.0, 120.0],
        'precip_mm' => [0.0, 300.0],
        'pressure_hpa' => [850.0, 1100.0],
        'humidity_pct' => [0.0, 100.0],
        'visibility_m' => [0.0, 60000.0],
        'weather_code' => [0.0, 99.0],
    ];

    /**
     * @return array{ok:bool, errors:string[], clean:?array}
     */
    public static function observation(array $row): array {
        $errors = [];

        $uuid = (string) ($row['uuid'] ?? '');
        if (!preg_match('/^[0-9a-fA-F-]{36}$/', $uuid)) {
            $errors[] = 'uuid обязателен (36 символов)';
        }

        $lat = filter_var($row['lat'] ?? null, FILTER_VALIDATE_FLOAT);
        $lon = filter_var($row['lon'] ?? null, FILTER_VALIDATE_FLOAT);
        if ($lat === false || $lat === null || $lat < -90 || $lat > 90) {
            $errors[] = 'lat вне диапазона [-90..90]';
        }
        if ($lon === false || $lon === null || $lon < -180 || $lon > 180) {
            $errors[] = 'lon вне диапазона [-180..180]';
        }

        $observedAt = self::parseTime((string) ($row['observed_at'] ?? ''));
        if ($observedAt === null) {
            $errors[] = 'observed_at обязателен (ISO 8601)';
        } elseif (strtotime($observedAt) > time() + 600) {
            $errors[] = 'observed_at в будущем более чем на 10 минут';
        } elseif (strtotime($observedAt) < time() - 48 * 3600) {
            $errors[] = 'observed_at старше 48 часов';
        }

        $clean = [
            'uuid' => strtolower($uuid),
            'lat' => $lat === false ? null : $lat,
            'lon' => $lon === false ? null : $lon,
            'observed_at' => $observedAt,
        ];

        $measured = 0;
        foreach (self::RANGES as $field => [$min, $max]) {
            $value = $row[$field] ?? null;
            if ($value === null || $value === '') {
                $clean[$field] = null;
                continue;
            }
            if (!is_numeric($value)) {
                $errors[] = "$field должен быть числом";
                $clean[$field] = null;
                continue;
            }
            $f = (float) $value;
            if ($f < $min || $f > $max) {
                $errors[] = "$field вне физического диапазона [$min..$max]";
                $clean[$field] = null;
                continue;
            }
            $clean[$field] = $f;
            $measured++;
        }

        // Порыв не может быть заметно меньше средней скорости
        if ($clean['wind_ms'] !== null && $clean['wind_gust_ms'] !== null &&
            $clean['wind_gust_ms'] < $clean['wind_ms'] - 0.5) {
            $errors[] = 'wind_gust_ms меньше wind_ms';
        }

        if ($measured === 0) {
            $errors[] = 'нужно хотя бы одно измерение (temp/wind/precip/pressure/...)';
        }

        if ($errors !== []) {
            return ['ok' => false, 'errors' => $errors, 'clean' => null];
        }
        return ['ok' => true, 'errors' => [], 'clean' => $clean];
    }

    /** ISO 8601 → 'Y-m-d H:i:s' (UTC) или null. */
    public static function parseTime(string $value): ?string {
        $ts = strtotime($value);
        if ($ts === false) {
            return null;
        }
        return gmdate('Y-m-d H:i:s', $ts);
    }

    public static function email(string $value): bool {
        return (bool) filter_var($value, FILTER_VALIDATE_EMAIL);
    }
}
