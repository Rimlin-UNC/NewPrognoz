<?php
declare(strict_types=1);

namespace WA\Services;

/**
 * HTTP-клиент Open-Meteo (без ключей). curl с фолбэком на file_get_contents —
 * совместимо с любым shared-хостингом.
 */
final class OpenMeteoClient {

    public static function fetchForecast(float $lat, float $lon, int $hours, int $pastDays = 0): ?array {
        $cfg = wa_config()['open_meteo'] ?? [];
        $baseUrl = rtrim((string) ($cfg['base_url'] ?? 'https://api.open-meteo.com/v1/forecast'), '/');
        $query = http_build_query([
            'latitude' => $lat,
            'longitude' => $lon,
            'hourly' => 'temperature_2m,wind_speed_10m,wind_gusts_10m,precipitation,'
                . 'precipitation_probability,pressure_msl,relative_humidity_2m,visibility,'
                . 'cloud_cover,weather_code',
            'past_days' => $pastDays,
            'forecast_days' => (int) ceil(max(1, $hours) / 24) + 1,
            'timezone' => 'UTC',
            'wind_speed_unit' => 'ms',
        ]);
        $url = $baseUrl . '?' . $query;
        $json = self::httpGet($url, (int) ($cfg['timeout'] ?? 15));
        if ($json === null) {
            return null;
        }
        /** @var mixed $data */
        $data = json_decode($json, true);
        if (!is_array($data) || !isset($data['hourly']['time'])) {
            return null;
        }
        return $data;
    }

    public static function httpGet(string $url, int $timeout): ?string {
        // 1) curl
        if (function_exists('curl_init')) {
            $ch = curl_init($url);
            curl_setopt_array($ch, [
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_TIMEOUT => $timeout,
                CURLOPT_CONNECTTIMEOUT => 8,
                CURLOPT_USERAGENT => 'WeatherPro2/1.0 (+https://open-meteo.com attribution)',
                CURLOPT_FOLLOWLOCATION => true,
                CURLOPT_MAXREDIRS => 2,
                CURLOPT_SSL_VERIFYPEER => true,
            ]);
            $body = curl_exec($ch);
            $code = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
            $err = curl_error($ch);
            curl_close($ch);
            if (is_string($body) && $code >= 200 && $code < 300) {
                return $body;
            }
            Logger::app('warn', 'open-meteo curl failed', ['code' => $code, 'err' => $err]);
            return null;
        }
        // 2) file_get_contents + allow_url_fopen
        $ctx = stream_context_create(['http' => ['timeout' => $timeout, 'header' => "User-Agent: WeatherPro2/1.0\r\n"]]);
        $body = @file_get_contents($url, false, $ctx);
        return is_string($body) ? $body : null;
    }
}
