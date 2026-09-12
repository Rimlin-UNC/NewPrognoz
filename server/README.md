# Weather Professional 2.0 — серверная часть

REST API + агрегатор для shared-хостинга (ymaster.ru и любой PHP-хостинг).

**Стек:** PHP 8.1+ (без Composer), MySQL/MariaDB (или SQLite для тестов), mod_rewrite.

## Структура

```
server/
├── htdocs/            ← document root (единственная веб-доступная папка)
│   ├── index.php      — фронт-контроллер API (/api/v1/*)
│   ├── cron.php       — HTTP-обёртка cron (?job=…&token=…)
│   ├── .htaccess      — rewrite + security headers
│   └── admin/         — админ-панель
├── src/               — классы (WA\*), вне docroot
│   ├── bootstrap.php  — автозагрузка, конфиг, JSON-помощники
│   ├── Db.php         — PDO (mysql/sqlite)
│   ├── Schema.php     — каноническая схема БД (оба драйвера)
│   ├── Auth.php       — API-ключи, JWT (HS256), device_hash
│   ├── RateLimiter.php, Router.php, Validator.php, Logger.php
│   ├── Controllers.php
│   └── Services/      — Sync, Aggregation, Forecast, OpenMeteo, ProfileRules
├── cron/              — aggregate.php, fetch_forecasts.php, cleanup.php
├── tests/             — e2e самотест (SQLite) + тестовый конфиг
├── install.php        — установщик: схема + сиды + админ
├── schema_dump.php    — генератор schema.mysql.sql
├── schema.mysql.sql   — дамп схемы для phpMyAdmin
└── config.example.php
```

## Быстрый старт (локально, SQLite)

```bash
cd server
cp tests/config.sqlite.php config.php
php install.php                      # схема + админ
php -S 127.0.0.1:8099 -t htdocs &    # встроенный сервер
curl http://127.0.0.1:8099/api/v1/health
php tests/api_test.php               # e2e самотест
```

## Развёртывание на хостинге

Полная пошаговая инструкция — в [`docs/DEPLOY.md`](../docs/DEPLOY.md).

Кратко:
1. Создать БД MySQL в панели хостинга.
2. Скопировать `config.example.php` → `config.php`, заполнить доступы, pepper, секреты.
3. Загрузить `server/` на хостинг; document root → `server/htdocs`
   (либо содержимое `htdocs/` в public_html, остальное — уровнем выше).
4. Выполнить `php install.php` (SSH) или импортировать `schema.mysql.sql` через phpMyAdmin.
5. Настроить cron: `aggregate` ежечасно, `fetch` каждые 30 мин, `cleanup` ежедневно.
6. Проверить `https://домен/api/v1/health`.

## API (кратко)

| Метод | Путь | Назначение |
|---|---|---|
| GET  | /api/v1/health | статус сервиса |
| POST | /api/v1/auth/register | регистрация + устройство + api_key |
| POST | /api/v1/auth/login | JWT |
| POST | /api/v1/devices | ещё одно устройство (JWT) |
| GET  | /api/v1/me | профиль по X-Api-Key |
| POST | /api/v1/sync/push | батч наблюдений (идемпотентно) |
| GET  | /api/v1/sync/pull?since= | агрегаты, bias, курсор |
| POST | /api/v1/observations | одиночное наблюдение |
| GET  | /api/v1/forecast?lat&lon&profile&hours | прогноз с bias + confidence + флаги |
| GET  | /api/v1/aggregates?kind= | агрегаты |

Полная спецификация — [`docs/API.md`](../docs/API.md).
