<?php
declare(strict_types=1);

namespace WA;

use PDO;
use RuntimeException;

/**
 * PDO-подключение ( singleton ). Драйверы: mysql (хостинг) и sqlite (тесты/локально).
 */
final class Db {
    private static ?PDO $pdo = null;
    private static string $driver = 'mysql';

    public static function pdo(): PDO {
        if (self::$pdo === null) {
            $cfg = wa_config()['db'] ?? [];
            $driver = (string) ($cfg['driver'] ?? 'mysql');
            self::$driver = $driver;
            if ($driver === 'sqlite') {
                $path = (string) ($cfg['sqlite_path'] ?? sys_get_temp_dir() . '/weather.sqlite');
                $dir = dirname($path);
                if (!is_dir($dir)) {
                    @mkdir($dir, 0775, true);
                }
                $dsn = 'sqlite:' . $path;
                $pdo = new PDO($dsn, null, null, [
                    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                ]);
                $pdo->exec('PRAGMA journal_mode = WAL');
                $pdo->exec('PRAGMA foreign_keys = ON');
            } elseif ($driver === 'mysql') {
                $host = (string) ($cfg['host'] ?? 'localhost');
                $port = (int) ($cfg['port'] ?? 3306);
                $name = (string) ($cfg['name'] ?? '');
                $dsn = sprintf('mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4', $host, $port, $name);
                $pdo = new PDO($dsn, (string) ($cfg['user'] ?? ''), (string) ($cfg['pass'] ?? ''), [
                    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                    PDO::MYSQL_ATTR_INIT_COMMAND => 'SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci',
                ]);
            } else {
                throw new RuntimeException("Неизвестный драйвер БД: $driver");
            }
            self::$pdo = $pdo;
        }
        return self::$pdo;
    }

    public static function driver(): string {
        self::pdo();
        return self::$driver;
    }
}
