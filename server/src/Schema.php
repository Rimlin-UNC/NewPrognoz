<?php
declare(strict_types=1);

namespace WA;

use PDO;

/**
 * Каноническая схема БД (единственный источник правды).
 * Драйверы: mysql (хостинг ymaster.ru) и sqlite (тесты/локальный режим).
 * schema.mysql.sql генерируется из этого класса: php schema_dump.php.
 *
 * Все времена — UTC в формате 'Y-m-d H:i:s' (строки сортируются
 * хронологически в обоих драйверах). UUID — CHAR(36).
 */
final class Schema {
    public const VERSION = 1;

    /** @return array<string, array{cols:array<string,string>, uniques?:array<string,array<int,string>>, indexes?:array<string,array<int,string>>, pk_composite?:array<int,string>}> */
    private static function defs(): array {
        // {dt} — DATETIME (mysql) / TEXT (sqlite); {pk} — автоинкрементный PK
        return [

        'users' => [
            'cols' => [
                'id' => '{pk}',
                'uuid' => 'CHAR(36) NOT NULL',
                'email' => 'VARCHAR(190) NOT NULL',
                'password_hash' => 'VARCHAR(255) NOT NULL',
                'role' => "VARCHAR(20) NOT NULL DEFAULT 'user'",
                'created_at' => '{dt} NOT NULL',
                'updated_at' => '{dt} NOT NULL',
                'deleted_at' => '{dt} NULL',
            ],
            'uniques' => ['uq_users_uuid' => ['uuid'], 'uq_users_email' => ['email']],
        ],

        'devices' => [
            'cols' => [
                'id' => '{pk}',
                'uuid' => 'CHAR(36) NOT NULL',
                'user_id' => 'INT NOT NULL',
                'name' => "VARCHAR(120) NOT NULL DEFAULT ''",
                'platform' => "VARCHAR(30) NOT NULL DEFAULT 'android'",
                'app_version' => "VARCHAR(20) NOT NULL DEFAULT ''",
                'api_key_hash' => 'CHAR(64) NOT NULL',
                'last_seen_at' => '{dt} NULL',
                'created_at' => '{dt} NOT NULL',
                'updated_at' => '{dt} NOT NULL',
                'deleted_at' => '{dt} NULL',
            ],
            'uniques' => ['uq_devices_uuid' => ['uuid'], 'uq_devices_key' => ['api_key_hash']],
            'indexes' => ['ix_devices_user' => ['user_id']],
        ],

        'user_profiles' => [
            'cols' => [
                'id' => '{pk}',
                'uuid' => 'CHAR(36) NOT NULL',
                'user_id' => 'INT NOT NULL',
                'profile_code' => 'VARCHAR(30) NOT NULL',
                'thresholds_json' => 'TEXT NULL',
                'updated_at' => '{dt} NOT NULL',
            ],
            'uniques' => ['uq_profiles_uuid' => ['uuid']],
            'indexes' => ['ix_profiles_user' => ['user_id']],
        ],

        'locations' => [
            'cols' => [
                'id' => '{pk}',
                'uuid' => 'CHAR(36) NOT NULL',
                'user_id' => 'INT NOT NULL',
                'name' => "VARCHAR(190) NOT NULL DEFAULT ''",
                'lat' => 'DECIMAL(8,5) NOT NULL',
                'lon' => 'DECIMAL(8,5) NOT NULL',
                'radius_m' => 'INT NOT NULL DEFAULT 1000',
                'is_favorite' => 'TINYINT NOT NULL DEFAULT 0',
                'created_at' => '{dt} NOT NULL',
                'updated_at' => '{dt} NOT NULL',
                'deleted_at' => '{dt} NULL',
            ],
            'uniques' => ['uq_locations_uuid' => ['uuid']],
            'indexes' => ['ix_locations_user' => ['user_id']],
        ],

        // Обезличенные наблюдения: связи с пользователем НЕТ, только
        // device_hash = HMAC(pepper, device_uuid) — необратимо.
        'observations' => [
            'cols' => [
                'id' => '{pk}',
                'uuid' => 'CHAR(36) NOT NULL',
                'device_hash' => 'CHAR(64) NOT NULL',
                'lat' => 'DECIMAL(8,5) NOT NULL',
                'lon' => 'DECIMAL(8,5) NOT NULL',
                'observed_at' => '{dt} NOT NULL',
                'temp_c' => 'DECIMAL(5,1) NULL',
                'wind_ms' => 'DECIMAL(5,1) NULL',
                'wind_gust_ms' => 'DECIMAL(5,1) NULL',
                'precip_mm' => 'DECIMAL(6,1) NULL',
                'pressure_hpa' => 'DECIMAL(7,1) NULL',
                'humidity_pct' => 'DECIMAL(5,1) NULL',
                'visibility_m' => 'INT NULL',
                'weather_code' => 'INT NULL',
                'quality' => "VARCHAR(10) NOT NULL DEFAULT 'raw'",
                'reject_reason' => 'VARCHAR(40) NULL',
                'received_at' => '{dt} NOT NULL',
                'updated_at' => '{dt} NOT NULL',
                'deleted_at' => '{dt} NULL',
            ],
            'uniques' => ['uq_obs_uuid' => ['uuid']],
            'indexes' => [
                'ix_obs_geo_time' => ['lat', 'lon', 'observed_at'],
                'ix_obs_quality' => ['quality'],
                'ix_obs_device' => ['device_hash'],
            ],
        ],

        'forecast_sources' => [
            'cols' => [
                'id' => '{pk}',
                'code' => 'VARCHAR(40) NOT NULL',
                'name' => 'VARCHAR(120) NOT NULL',
                'type' => "VARCHAR(20) NOT NULL DEFAULT 'api'",
                'base_weight' => 'DECIMAL(4,2) NOT NULL DEFAULT 1.00',
                'active' => 'TINYINT NOT NULL DEFAULT 1',
                'last_run_at' => '{dt} NULL',
                'last_status' => 'VARCHAR(20) NULL',
            ],
            'uniques' => ['uq_sources_code' => ['code']],
        ],

        'forecasts' => [
            'cols' => [
                'id' => '{pk}',
                'source_id' => 'INT NOT NULL',
                'lat' => 'DECIMAL(8,5) NOT NULL',
                'lon' => 'DECIMAL(8,5) NOT NULL',
                'issued_at' => '{dt} NOT NULL',
                'target_time' => '{dt} NOT NULL',
                'temp_c' => 'DECIMAL(5,1) NULL',
                'wind_ms' => 'DECIMAL(5,1) NULL',
                'wind_gust_ms' => 'DECIMAL(5,1) NULL',
                'precip_mm' => 'DECIMAL(6,1) NULL',
                'precip_prob' => 'DECIMAL(5,1) NULL',
                'pressure_hpa' => 'DECIMAL(7,1) NULL',
                'humidity_pct' => 'DECIMAL(5,1) NULL',
                'visibility_m' => 'INT NULL',
                'cloud_pct' => 'DECIMAL(5,1) NULL',
                'weather_code' => 'INT NULL',
                'created_at' => '{dt} NOT NULL',
            ],
            'indexes' => [
                'ix_fc_lookup' => ['source_id', 'lat', 'lon', 'target_time'],
                'ix_fc_issued' => ['issued_at'],
                'ix_fc_target' => ['target_time'],
            ],
        ],

        'sync_queue' => [
            'cols' => [
                'id' => '{pk}',
                'device_uuid' => 'CHAR(36) NOT NULL',
                'op_uuid' => 'CHAR(36) NOT NULL',
                'entity' => 'VARCHAR(40) NOT NULL',
                'payload_json' => 'TEXT NULL',
                'status' => "VARCHAR(12) NOT NULL DEFAULT 'processed'",
                'error' => 'VARCHAR(255) NULL',
                'received_at' => '{dt} NOT NULL',
            ],
            'uniques' => ['uq_syncq_op' => ['op_uuid']],
            'indexes' => ['ix_syncq_status' => ['status', 'received_at']],
        ],

        'sync_log' => [
            'cols' => [
                'id' => '{pk}',
                'device_uuid' => 'CHAR(36) NOT NULL',
                'direction' => 'VARCHAR(6) NOT NULL',
                'ops_in' => 'INT NOT NULL DEFAULT 0',
                'ops_ok' => 'INT NOT NULL DEFAULT 0',
                'http_status' => 'INT NOT NULL DEFAULT 0',
                'ip_hash' => 'CHAR(64) NULL',
                'created_at' => '{dt} NOT NULL',
            ],
            'indexes' => ['ix_synclog_time' => ['created_at']],
        ],

        'api_sources' => [
            'cols' => [
                'id' => '{pk}',
                'code' => 'VARCHAR(40) NOT NULL',
                'name' => 'VARCHAR(120) NOT NULL',
                'base_url' => 'VARCHAR(255) NOT NULL',
                'endpoint' => 'VARCHAR(255) NULL',
                'enabled' => 'TINYINT NOT NULL DEFAULT 1',
                'priority' => 'INT NOT NULL DEFAULT 100',
                'last_status' => 'VARCHAR(20) NULL',
                'last_run_at' => '{dt} NULL',
                'config_json' => 'TEXT NULL',
            ],
            'uniques' => ['uq_apisources_code' => ['code']],
        ],

        'model_runs' => [
            'cols' => [
                'id' => '{pk}',
                'source_id' => 'INT NOT NULL',
                'run_time' => '{dt} NOT NULL',
                'region' => "VARCHAR(60) NOT NULL DEFAULT ''",
                'status' => "VARCHAR(20) NOT NULL DEFAULT 'ok'",
                'points' => 'INT NOT NULL DEFAULT 0',
                'started_at' => '{dt} NOT NULL',
                'finished_at' => '{dt} NULL',
                'error' => 'VARCHAR(255) NULL',
            ],
            'indexes' => ['ix_runs_source' => ['source_id', 'run_time']],
        ],

        'aggregates' => [
            'cols' => [
                'id' => '{pk}',
                'kind' => 'VARCHAR(20) NOT NULL',
                'cell_lat' => 'DECIMAL(6,2) NOT NULL DEFAULT 0',
                'cell_lon' => 'DECIMAL(6,2) NOT NULL DEFAULT 0',
                'hour_bucket' => '{dt} NOT NULL',
                'source_code' => "VARCHAR(40) NOT NULL DEFAULT ''",
                'payload_json' => 'TEXT NOT NULL',
                'sample_size' => 'INT NOT NULL DEFAULT 0',
                'computed_at' => '{dt} NOT NULL',
            ],
            'uniques' => ['uq_agg' => ['kind', 'cell_lat', 'cell_lon', 'hour_bucket', 'source_code']],
            'indexes' => ['ix_agg_kind_time' => ['kind', 'computed_at']],
        ],

        'audit_log' => [
            'cols' => [
                'id' => '{pk}',
                'actor' => "VARCHAR(80) NOT NULL DEFAULT 'system'",
                'action' => 'VARCHAR(60) NOT NULL',
                'entity' => 'VARCHAR(40) NULL',
                'entity_id' => 'VARCHAR(64) NULL',
                'ip_hash' => 'CHAR(64) NULL',
                'created_at' => '{dt} NOT NULL',
            ],
            'indexes' => ['ix_audit_time' => ['created_at']],
        ],

        'rate_limits' => [
            'cols' => [
                'minute_ts' => 'INT NOT NULL',
                'key_hash' => 'CHAR(64) NOT NULL',
                'count' => 'INT NOT NULL DEFAULT 0',
            ],
            'pk_composite' => ['minute_ts', 'key_hash'],
        ],
        ];
    }

    /** @return array<string,string> name => DDL (для sqlite может содержать несколько операторов через ";\n") */
    public static function tables(string $driver): array {
        $mysql = $driver === 'mysql';
        $out = [];
        foreach (self::defs() as $name => $d) {
            $cols = [];
            foreach ($d['cols'] as $col => $def) {
                $cols[] = "`$col` " . str_replace(
                    ['{dt}', '{pk}'],
                    [$mysql ? 'DATETIME' : 'TEXT',
                     $mysql ? 'INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY' : 'INTEGER PRIMARY KEY AUTOINCREMENT'],
                    $def
                );
            }
            if (!empty($d['pk_composite'])) {
                $cols[] = 'PRIMARY KEY (' . implode(', ', array_map(static fn($c) => "`$c`", $d['pk_composite'])) . ')';
            }
            if ($mysql) {
                $parts = $cols;
                foreach ($d['uniques'] ?? [] as $uk => $ucols) {
                    $parts[] = "UNIQUE KEY `$uk` (" . implode(',', array_map(static fn($c) => "`$c`", $ucols)) . ')';
                }
                foreach ($d['indexes'] ?? [] as $ix => $icols) {
                    $parts[] = "KEY `$ix` (" . implode(',', array_map(static fn($c) => "`$c`", $icols)) . ')';
                }
                $out[$name] = "CREATE TABLE `$name` (\n  " . implode(",\n  ", $parts) . "\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
            } else {
                $parts = $cols;
                foreach ($d['uniques'] ?? [] as $uk => $ucols) {
                    $parts[] = "CONSTRAINT `$uk` UNIQUE (" . implode(',', array_map(static fn($c) => "`$c`", $ucols)) . ')';
                }
                $ddl = "CREATE TABLE `$name` (\n  " . implode(",\n  ", $parts) . "\n)";
                foreach ($d['indexes'] ?? [] as $ix => $icols) {
                    $ddl .= ";\nCREATE INDEX IF NOT EXISTS `$ix` ON `$name` (" . implode(',', array_map(static fn($c) => "`$c`", $icols)) . ')';
                }
                $out[$name] = $ddl;
            }
        }
        return $out;
    }

    /** Сиды: источники прогнозов и внешние API (идемпотентно). */
    public static function seed(PDO $pdo): void {
        $insert = static function (string $table, array $row) use ($pdo): void {
            $cols = array_keys($row);
            $sql = "INSERT OR IGNORE INTO `$table` (" . implode(',', $cols) . ') VALUES ('
                . implode(',', array_fill(0, count($cols), '?')) . ')';
            if (Db::driver() === 'mysql') {
                $sql = str_replace('INSERT OR IGNORE', 'INSERT IGNORE', $sql);
            }
            $pdo->prepare($sql)->execute(array_values($row));
        };

        $insert('forecast_sources', ['code' => 'open-meteo', 'name' => 'Open-Meteo (best match)', 'type' => 'api', 'base_weight' => 1.0, 'active' => 1]);
        $insert('forecast_sources', ['code' => 'open-meteo-gfs', 'name' => 'Open-Meteo (GFS)', 'type' => 'api', 'base_weight' => 0.9, 'active' => 0]);
        $insert('forecast_sources', ['code' => 'open-meteo-ecmwf', 'name' => 'Open-Meteo (ECMWF)', 'type' => 'api', 'base_weight' => 0.95, 'active' => 0]);

        $insert('api_sources', [
            'code' => 'open-meteo', 'name' => 'Open-Meteo Forecast API',
            'base_url' => 'https://api.open-meteo.com', 'endpoint' => '/v1/forecast',
            'enabled' => 1, 'priority' => 100, 'config_json' => json_encode(['cache_ttl' => 1200]),
        ]);
        $insert('api_sources', [
            'code' => 'open-meteo-archive', 'name' => 'Open-Meteo Archive API (ERA5)',
            'base_url' => 'https://archive-api.open-meteo.com', 'endpoint' => '/v1/archive',
            'enabled' => 1, 'priority' => 200, 'config_json' => json_encode([]),
        ]);
    }

    /** Создание всех таблиц + сиды. Идемпотентно. @return string[] созданные таблицы */
    public static function install(PDO $pdo): array {
        $created = [];
        $driver = Db::driver();
        foreach (self::tables($driver) as $name => $ddl) {
            $exists = $driver === 'mysql'
                ? $pdo->query('SHOW TABLES LIKE ' . $pdo->quote($name))->fetch()
                : $pdo->query("SELECT name FROM sqlite_master WHERE type='table' AND name=" . $pdo->quote($name))->fetch();
            if ($exists) {
                continue;
            }
            // DDL может содержать несколько операторов (индексы sqlite)
            foreach (array_filter(array_map('trim', explode(";\n", $ddl))) as $stmt) {
                $pdo->exec($stmt);
            }
            $created[] = $name;
        }
        self::seed($pdo);
        return $created;
    }
}
