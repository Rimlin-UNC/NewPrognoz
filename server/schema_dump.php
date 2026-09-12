<?php
/**
 * Печать MySQL-DDL (для импорта через phpMyAdmin).
 * Использование: php schema_dump.php > schema.mysql.sql
 * Файл schema.mysql.sql в репозитории должен совпадать с выводом
 * (проверяется в CI — server-tests).
 */

declare(strict_types=1);

require __DIR__ . '/src/bootstrap.php';

echo "-- Weather Professional 2.0 — схема БД (MySQL/MariaDB)\n";
echo "-- Сгенерировано из src/Schema.php. НЕ редактируйте вручную.\n";
echo "-- Эквивалентно запуску php install.php.\n\n";
echo "SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;\n\n";

foreach (WA\Schema::tables('mysql') as $name => $ddl) {
    echo $ddl, ";\n\n";
}

echo "-- Сиды справочников\n";
echo "INSERT IGNORE INTO `forecast_sources` (`code`,`name`,`type`,`base_weight`,`active`) VALUES\n";
echo "  ('open-meteo','Open-Meteo (best match)','api',1.00,1),\n";
echo "  ('open-meteo-gfs','Open-Meteo (GFS)','api',0.90,0),\n";
echo "  ('open-meteo-ecmwf','Open-Meteo (ECMWF)','api',0.95,0);\n\n";
echo "INSERT IGNORE INTO `api_sources` (`code`,`name`,`base_url`,`endpoint`,`enabled`,`priority`,`config_json`) VALUES\n";
echo "  ('open-meteo','Open-Meteo Forecast API','https://api.open-meteo.com','/v1/forecast',1,100,'{\"cache_ttl\":1200}'),\n";
echo "  ('open-meteo-archive','Open-Meteo Archive API (ERA5)','https://archive-api.open-meteo.com','/v1/archive',1,200,'[]');\n";
