<?php
declare(strict_types=1);

namespace WA\Services;

/**
 * Профессиональные профили и критичные пороги (серверная копия правил
 * из приложения; используется для флагов в /forecast).
 */
final class ProfileRules {

    public const PROFILES = ['universal', 'roofer', 'builder', 'pilot', 'fisherman', 'alpinist'];

    /**
     * @param float|null $temp °C, @param float|null $wind м/с, @param float|null $gust м/с,
     * @param float|null $precip мм/ч, @param float|null $vis м, @param int|null $code WMO,
     * @param bool $freezing риск гололёда (temp ≤ 0)
     * @return array<int, array{key:string, level:string, message:string}>
     */
    public static function flags(string $profile, ?float $temp, ?float $wind, ?float $gust,
                                  ?float $precip, ?float $vis, ?int $code, bool $freezing): array {
        $thunder = $code !== null && $code >= 95;
        $snow = $code !== null && in_array($code, [71, 73, 75, 77, 85, 86], true);
        $rainy = ($precip !== null && $precip >= 0.2) || in_array($code, [51,53,55,56,57,61,63,65,66,67,80,81,82], true);
        $flags = [];
        $add = static function (string $key, string $level, string $message) use (&$flags): void {
            $flags[] = ['key' => $key, 'level' => $level, 'message' => $message];
        };

        switch ($profile) {
            case 'roofer': // кровельщик
                if ($gust !== null && $gust >= 12.0) $add('gust', 'critical', "Порывы ветра {$gust} м/с — работы на высоте запрещены (норма < 12)");
                elseif ($gust !== null && $gust >= 9.0) $add('gust', 'warn', "Порывы ветра {$gust} м/с — крепить материалы");
                if ($rainy) $add('precip', 'critical', 'Осадки — монтажные работы со мембранами/мастиками запрещены');
                if ($thunder) $add('thunder', 'critical', 'Гроза — покинуть крышу');
                if ($freezing && $rainy) $add('ice', 'critical', 'Гололёд — риск падения');
                if ($temp !== null && $temp >= 30.0) $add('heat', 'warn', "Жара {$temp}°C — перерывы, питьевой режим");
                break;

            case 'builder': // строитель
                if ($gust !== null && $gust >= 15.0) $add('gust', 'critical', "Порывы {$gust} м/с — остановка крановых работ");
                elseif ($gust !== null && $gust >= 10.0) $add('gust', 'warn', "Порывы {$gust} м/с — осторожно с краном/лесами");
                if ($rainy) $add('precip', 'warn', 'Осадки — бетонные работы требуют защиты');
                if ($thunder) $add('thunder', 'critical', 'Гроза — прекратить работы на открытом воздухе');
                if ($temp !== null && $temp <= -20.0) $add('cold', 'critical', "Мороз {$temp}°C — ограничения на бетонирование");
                break;

            case 'pilot': // лётчик (GA)
                if ($vis !== null && $vis < 5000) $add('visibility', 'critical', 'Видимость < 5 км — VMC не гарантируется');
                elseif ($vis !== null && $vis < 8000) $add('visibility', 'warn', 'Видимость < 8 км — уточнить правила полётов');
                if ($gust !== null && $gust >= 15.0) $add('gust', 'critical', "Порывы {$gust} м/с — сдвиг ветра/турбулентность");
                if ($thunder) $add('thunder', 'critical', 'Гроза — CB, полёты запрещены');
                if ($snow) $add('snow', 'critical', 'Снегопад — обледенение');
                if ($code !== null && in_array($code, [45, 48], true)) $add('fog', 'critical', 'Туман — видимость может быть ниже минимума');
                break;

            case 'fisherman': // рыбак
                if ($wind !== null && $wind >= 10.0) $add('wind', 'critical', "Ветер {$wind} м/с — опасно для лодки");
                elseif ($wind !== null && $wind >= 7.0) $add('wind', 'warn', "Ветер {$wind} м/с — волна");
                if ($thunder) $add('thunder', 'critical', 'Гроза — выйти на берег');
                if ($temp !== null && $temp <= -5.0 && $rainy) $add('ice', 'warn', 'Замерзающая мокрая снасть/лёд');
                break;

            case 'alpinist': // альпинист
                if ($wind !== null && $wind >= 15.0) $add('wind', 'critical', "Ветер {$wind} м/с — шторм, спуск");
                elseif ($wind !== null && $wind >= 10.0) $add('wind', 'warn', "Ветер {$wind} м/с — оценка маршрута");
                if ($vis !== null && $vis < 1000) $add('visibility', 'critical', 'Видимость < 1 км — навигация критична');
                if ($snow) $add('snow', 'critical', 'Снегопад — лавиноопасно');
                if ($temp !== null && $temp <= -15.0) $add('cold', 'critical', "Мороз {$temp}°C — обморожение");
                break;

            default: // universal
                if ($gust !== null && $gust >= 17.0) $add('gust', 'critical', "Шквалы до {$gust} м/с");
                if ($thunder) $add('thunder', 'warn', 'Гроза');
        }
        return $flags;
    }
}
