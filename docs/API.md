# Weather Pro 2.0 — спецификация REST API v1

Base URL: `https://<ваш-домен>` — все пути ниже относительно корня.

## Аутентификация

| Способ | Где | Для чего |
|---|---|---|
| `X-Api-Key: wa_…` | заголовок | все запросы приложения (устройство) |
| `Authorization: Bearer <jwt>` | заголовок | управление аккаунтом (создание устройств) |

API-ключ выдаётся один раз при регистрации/создании устройства и хранится
только на клиенте; сервер хранит лишь SHA-256 хэш.

Ошибки — единый формат:
```json
{ "error": "код_ошибки", "message": "человекочитаемое описание" }
```
HTTP-коды: 400/401/403/404/405/409/413/422/429/500/503.

Rate limit: 120 запросов/мин на ключ (настраивается), ответ 429
с `retry_after_seconds`.

---

## GET /api/v1/health
Статус сервиса. Без авторизации.

```json
{ "status": "ok", "service": "weather-pro-2.0", "version": "2.0.0",
  "db": "ok", "driver": "mysql", "time": "2026-09-12T10:00:00+00:00" }
```

## POST /api/v1/auth/register
Регистрация пользователя + первого устройства.

Тело: `{ "email": "...", "password": "...", "device_name": "Android",
"platform": "android", "app_version": "2.0.0" }`

Ответ `201`:
```json
{ "user_uuid": "…", "device_uuid": "…", "api_key": "wa_…",
  "notice": "api_key показывается один раз — сохраните его" }
```
Ошибки: 422 (email/пароль), 409 (email занят).

## POST /api/v1/auth/login
Тело: `{ "email": "...", "password": "..." }`
Ответ: `{ "token": "<jwt>", "expires_at": "…", "role": "user" }` (401 при неудаче).

## POST /api/v1/devices  (JWT)
Создать дополнительное устройство. Тело: `{ "name": "…", "platform": "…", "app_version": "…" }`
Ответ `201`: `{ "device_uuid": "…", "api_key": "wa_…" }`

## GET /api/v1/me  (X-Api-Key)
Информация о пользователе и устройстве.

## POST /api/v1/sync/push  (X-Api-Key)
Батч наблюдений (до 200). Идемпотентно по `uuid`.

Тело:
```json
{ "observations": [
  { "uuid": "…", "lat": 55.75, "lon": 37.62,
    "observed_at": "2026-09-12T09:40:00Z",
    "temp_c": 15.2, "wind_ms": 3.4, "wind_gust_ms": 5.0,
    "precip_mm": 0.0, "pressure_hpa": 1011.0, "humidity_pct": 62.0,
    "visibility_m": 10000, "weather_code": 1 }
] }
```
Все измерения опциональны, но нужно ≥1. Диапазоны: temp −60..60 °C,
wind 0..80, gust 0..120, precip 0..300 мм/ч, pressure 850..1100 гПа,
humidity 0..100 %, visibility 0..60000 м; время: не в будущем >10 мин
и не старше 48 ч.

Ответ:
```json
{ "accepted": 1, "duplicate": 1, "rejected": 0,
  "results": [ { "uuid": "…", "status": "accepted" },
               { "uuid": "…", "status": "duplicate" },
               { "uuid": "…", "status": "rejected", "reason": "…" } ] }
```

## POST /api/v1/observations  (X-Api-Key)
Синоним push для одного наблюдения (тело — сам объект). Ответ 201/422.

## GET /api/v1/sync/pull?since=\<cursor\>  (X-Api-Key)
Инкрементальная выдача серверных данных. `since` — курсор из прошлого pull
(ISO 8601); без него — последние 24 ч.

Ответ:
```json
{ "cursor": "2026-09-12T10:05:00", "server_time": "2026-09-12T10:05:01+00:00",
  "provider_bias": { "temp_c": 0.42, "wind_ms": 0.15, "sample_size": 128 },
  "aggregates": [ { "kind": "cell_hour", "cell": {"lat": 55.8, "lon": 37.6},
      "hour_bucket": "2026-09-12 09:00:00", "source_code": "",
      "payload": { "temp_c": {"median": 15.1, "p25": 14.8, "p75": 15.4, "n": 3} },
      "sample_size": 3, "computed_at": "2026-09-12 10:00:00" } ],
  "sources": [ { "code": "open-meteo", "name": "Open-Meteo (best match)",
      "type": "api", "base_weight": 1.0, "active": 1 } ] }
```
`provider_bias` — текущее сглаженное смещение источника (для коррекции
ансамбля на клиенте).

## GET /api/v1/forecast?lat=&lon=&profile=&hours=  (X-Api-Key)
Прогноз с сервера: кэш (20 мин) → Open-Meteo → bias-коррекция →
confidence → флаги профиля.

Параметры: `lat`, `lon` (обязательны), `hours` (1..168, по умолчанию 48),
`profile` (universal|roofer|builder|pilot|fisherman|alpinist).

Ответ (сокращено):
```json
{ "issued_at": "…", "fetched_now": true, "lat": 55.75, "lon": 37.62,
  "cell": {"lat": 55.75, "lon": 37.6},
  "bias_applied": true,
  "bias": { "temp_c": 0.42, "wind_ms": 0.15, "mae_temp": 1.1, "sample_size": 128 },
  "profile": "roofer",
  "points": [
    { "time": "2026-09-12T11:00:00+00:00", "temp_c": 15.9, "wind_ms": 3.2,
      "wind_gust_ms": 5.1, "precip_mm": 0.0, "precip_prob": 5,
      "pressure_hpa": 1011, "humidity_pct": 61, "visibility_m": 10000,
      "cloud_pct": 40, "weather_code": 2, "confidence": 92,
      "flags": [ { "key": "gust", "level": "warn", "message": "…" } ] } ],
  "attribution": "Данные: Open-Meteo (CC BY 4.0), bias-коррекция Weather Pro 2.0" }
```
`confidence` = `100 · 0.92^(часов_вперёд/24) · (1 − |bias_temp|/6)`, 5..100.
503 — апстрим недоступен и нет кэша.

## GET /api/v1/aggregates?kind=&limit=  (X-Api-Key)
Агрегаты: `kind=cell_hour|bias`, лимит 1..500 (по умолчанию 100).

## POST /admin/* — админ-панель
HTML-интерфейс `/admin/` (сессия, CSRF). Программного API нет.

## /cron.php?job=&token= — cron-обёртка
`job=aggregate|fetch|cleanup`, `token` = `cron.secret`. Ответ — JSON со статистикой.

---

## Алгоритм синхронизации (клиент ↔ сервер)

```
при появлении сети / раз в 6 ч / вручную:
  1. PUSH: взять из очереди ≤200 записей (uuid, sync_status='pending')
     → POST /sync/push
     → по ответу: accepted/duplicate → sync_status='synced';
                  rejected → sync_status='rejected' (причина сохраняется)
  2. PULL: GET /sync/pull?since=<cursor>
     → сохранить provider_bias (bias-коррекция ансамбля)
     → cursor = ответ.cursor
  3. Ошибки сети/5xx → записи остаются pending, retry с backoff
     (WorkManager: 1 мин → 10 мин → 1 ч → 6 ч)
```

Инварианты:
- каждое наблюдение имеет UUIDv4 — повторная отправка безопасна;
- сервер дедуплицирует по UNIQUE(uuid) — идемпотентность;
- наблюдения append-only → конфликтов редактирования нет;
- пользовательские настройки живут локально (LWW не требуется);
- батчи сжимаются gzip (Retrofit OkHttp делает это автоматически).

## Алгоритм агрегации (сервер, ежечасно)

```
1. observations quality='raw' за 48 ч:
     диапазоны → частота (≤1/10 мин на устройство) → 'rejected'
2. пространственные выбросы: в группе (ячейка 0.1° × час), n≥3:
     |x − median| > max(4·MAD, допуск) → 'rejected' (outlier)
3. кластеризация: (ячейка 0.1°, час) → медиана/p25/p75/n
     → aggregates kind='cell_hour' (UPSERT)
4. верификация: forecasts (цель за 24 ч, issued ≥1 ч до цели)
     ⋈ cell_hour по ячейке и часу:
     error = forecast − median(наблюдения)
5. bias EMA: bias = 0.7·bias_prev + 0.3·mean(errors)
     → aggregates kind='bias' (глобально и по источникам)
6. качество подтверждённых наблюдений → 'ok'
```
