-- Weather Professional 2.0 — схема БД (MySQL/MariaDB)
-- Сгенерировано из src/Schema.php. НЕ редактируйте вручную.
-- Эквивалентно запуску php install.php.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `uuid` CHAR(36) NOT NULL,
  `email` VARCHAR(190) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `role` VARCHAR(20) NOT NULL DEFAULT 'user',
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted_at` DATETIME NULL,
  UNIQUE KEY `uq_users_uuid` (`uuid`),
  UNIQUE KEY `uq_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `devices` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `uuid` CHAR(36) NOT NULL,
  `user_id` INT NOT NULL,
  `name` VARCHAR(120) NOT NULL DEFAULT '',
  `platform` VARCHAR(30) NOT NULL DEFAULT 'android',
  `app_version` VARCHAR(20) NOT NULL DEFAULT '',
  `api_key_hash` CHAR(64) NOT NULL,
  `last_seen_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted_at` DATETIME NULL,
  UNIQUE KEY `uq_devices_uuid` (`uuid`),
  UNIQUE KEY `uq_devices_key` (`api_key_hash`),
  KEY `ix_devices_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_profiles` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `uuid` CHAR(36) NOT NULL,
  `user_id` INT NOT NULL,
  `profile_code` VARCHAR(30) NOT NULL,
  `thresholds_json` TEXT NULL,
  `updated_at` DATETIME NOT NULL,
  UNIQUE KEY `uq_profiles_uuid` (`uuid`),
  KEY `ix_profiles_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `locations` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `uuid` CHAR(36) NOT NULL,
  `user_id` INT NOT NULL,
  `name` VARCHAR(190) NOT NULL DEFAULT '',
  `lat` DECIMAL(8,5) NOT NULL,
  `lon` DECIMAL(8,5) NOT NULL,
  `radius_m` INT NOT NULL DEFAULT 1000,
  `is_favorite` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted_at` DATETIME NULL,
  UNIQUE KEY `uq_locations_uuid` (`uuid`),
  KEY `ix_locations_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `observations` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `uuid` CHAR(36) NOT NULL,
  `device_hash` CHAR(64) NOT NULL,
  `lat` DECIMAL(8,5) NOT NULL,
  `lon` DECIMAL(8,5) NOT NULL,
  `observed_at` DATETIME NOT NULL,
  `temp_c` DECIMAL(5,1) NULL,
  `wind_ms` DECIMAL(5,1) NULL,
  `wind_gust_ms` DECIMAL(5,1) NULL,
  `precip_mm` DECIMAL(6,1) NULL,
  `pressure_hpa` DECIMAL(7,1) NULL,
  `humidity_pct` DECIMAL(5,1) NULL,
  `visibility_m` INT NULL,
  `weather_code` INT NULL,
  `quality` VARCHAR(10) NOT NULL DEFAULT 'raw',
  `reject_reason` VARCHAR(40) NULL,
  `received_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted_at` DATETIME NULL,
  UNIQUE KEY `uq_obs_uuid` (`uuid`),
  KEY `ix_obs_geo_time` (`lat`,`lon`,`observed_at`),
  KEY `ix_obs_quality` (`quality`),
  KEY `ix_obs_device` (`device_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `forecast_sources` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `code` VARCHAR(40) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `type` VARCHAR(20) NOT NULL DEFAULT 'api',
  `base_weight` DECIMAL(4,2) NOT NULL DEFAULT 1.00,
  `active` TINYINT NOT NULL DEFAULT 1,
  `last_run_at` DATETIME NULL,
  `last_status` VARCHAR(20) NULL,
  UNIQUE KEY `uq_sources_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `forecasts` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `source_id` INT NOT NULL,
  `lat` DECIMAL(8,5) NOT NULL,
  `lon` DECIMAL(8,5) NOT NULL,
  `issued_at` DATETIME NOT NULL,
  `target_time` DATETIME NOT NULL,
  `temp_c` DECIMAL(5,1) NULL,
  `wind_ms` DECIMAL(5,1) NULL,
  `wind_gust_ms` DECIMAL(5,1) NULL,
  `precip_mm` DECIMAL(6,1) NULL,
  `precip_prob` DECIMAL(5,1) NULL,
  `pressure_hpa` DECIMAL(7,1) NULL,
  `humidity_pct` DECIMAL(5,1) NULL,
  `visibility_m` INT NULL,
  `cloud_pct` DECIMAL(5,1) NULL,
  `weather_code` INT NULL,
  `created_at` DATETIME NOT NULL,
  KEY `ix_fc_lookup` (`source_id`,`lat`,`lon`,`target_time`),
  KEY `ix_fc_issued` (`issued_at`),
  KEY `ix_fc_target` (`target_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sync_queue` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `device_uuid` CHAR(36) NOT NULL,
  `op_uuid` CHAR(36) NOT NULL,
  `entity` VARCHAR(40) NOT NULL,
  `payload_json` TEXT NULL,
  `status` VARCHAR(12) NOT NULL DEFAULT 'processed',
  `error` VARCHAR(255) NULL,
  `received_at` DATETIME NOT NULL,
  UNIQUE KEY `uq_syncq_op` (`op_uuid`),
  KEY `ix_syncq_status` (`status`,`received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sync_log` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `device_uuid` CHAR(36) NOT NULL,
  `direction` VARCHAR(6) NOT NULL,
  `ops_in` INT NOT NULL DEFAULT 0,
  `ops_ok` INT NOT NULL DEFAULT 0,
  `http_status` INT NOT NULL DEFAULT 0,
  `ip_hash` CHAR(64) NULL,
  `created_at` DATETIME NOT NULL,
  KEY `ix_synclog_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `api_sources` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `code` VARCHAR(40) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `base_url` VARCHAR(255) NOT NULL,
  `endpoint` VARCHAR(255) NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `priority` INT NOT NULL DEFAULT 100,
  `last_status` VARCHAR(20) NULL,
  `last_run_at` DATETIME NULL,
  `config_json` TEXT NULL,
  UNIQUE KEY `uq_apisources_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `model_runs` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `source_id` INT NOT NULL,
  `run_time` DATETIME NOT NULL,
  `region` VARCHAR(60) NOT NULL DEFAULT '',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ok',
  `points` INT NOT NULL DEFAULT 0,
  `started_at` DATETIME NOT NULL,
  `finished_at` DATETIME NULL,
  `error` VARCHAR(255) NULL,
  KEY `ix_runs_source` (`source_id`,`run_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `aggregates` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `kind` VARCHAR(20) NOT NULL,
  `cell_lat` DECIMAL(6,2) NOT NULL DEFAULT 0,
  `cell_lon` DECIMAL(6,2) NOT NULL DEFAULT 0,
  `hour_bucket` DATETIME NOT NULL,
  `source_code` VARCHAR(40) NOT NULL DEFAULT '',
  `payload_json` TEXT NOT NULL,
  `sample_size` INT NOT NULL DEFAULT 0,
  `computed_at` DATETIME NOT NULL,
  UNIQUE KEY `uq_agg` (`kind`,`cell_lat`,`cell_lon`,`hour_bucket`,`source_code`),
  KEY `ix_agg_kind_time` (`kind`,`computed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `audit_log` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `actor` VARCHAR(80) NOT NULL DEFAULT 'system',
  `action` VARCHAR(60) NOT NULL,
  `entity` VARCHAR(40) NULL,
  `entity_id` VARCHAR(64) NULL,
  `ip_hash` CHAR(64) NULL,
  `created_at` DATETIME NOT NULL,
  KEY `ix_audit_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `rate_limits` (
  `minute_ts` INT NOT NULL,
  `key_hash` CHAR(64) NOT NULL,
  `count` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`minute_ts`, `key_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Сиды справочников
INSERT IGNORE INTO `forecast_sources` (`code`,`name`,`type`,`base_weight`,`active`) VALUES
  ('open-meteo','Open-Meteo (best match)','api',1.00,1),
  ('open-meteo-gfs','Open-Meteo (GFS)','api',0.90,0),
  ('open-meteo-ecmwf','Open-Meteo (ECMWF)','api',0.95,0);

INSERT IGNORE INTO `api_sources` (`code`,`name`,`base_url`,`endpoint`,`enabled`,`priority`,`config_json`) VALUES
  ('open-meteo','Open-Meteo Forecast API','https://api.open-meteo.com','/v1/forecast',1,100,'{"cache_ttl":1200}'),
  ('open-meteo-archive','Open-Meteo Archive API (ERA5)','https://archive-api.open-meteo.com','/v1/archive',1,200,'[]');
