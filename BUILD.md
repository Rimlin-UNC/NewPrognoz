# Сборка APK «Метео-Аналитик»

## Требования

| Инструмент | Версия |
|---|---|
| JDK | 17 (Temurin/Zulu — любой) |
| Android SDK | API 34 (platforms;android-34, build-tools;34.0.0) |
| Gradle | 8.4 (wrapper уже в репозитории — отдельно ставить не нужно) |
| ОС | Linux / macOS / Windows |

Проверить JDK: `java -version` → должен быть `17.x`.

## Быстрая сборка

```bash
# 1. Указать путь к Android SDK (пропустите, если используете Android Studio
#    или переменная ANDROID_HOME уже задана)
echo "sdk.dir=/путь/к/Android/Sdk" > local.properties

# 2. Сборка релизного APK (R8 + shrinkResources включены)
./gradlew clean
./gradlew assembleRelease

# 3. Готовый файл
ls -lh app/build/outputs/apk/release/app-release.apk
```

Итоговый APK ≈ **11 МБ** (minifyEnabled + shrinkResources, лёгкие зависимости — ТЗ п.8).

Отладочная сборка: `./gradlew assembleDebug`.

## Установка на устройство

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

Или просто скопируйте APK на телефон и откройте (разрешите установку из неизвестных источников).

> Release-сборка подписана debug-ключом, чтобы её можно было сразу установить.
> Для публикации в Google Play создайте собственный keystore:

```bash
keytool -genkeypair -v -keystore meteo-release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias meteo
```

и раскомментируйте/добавьте блок `signingConfigs` в `app/build.gradle.kts`.

## Установка Android SDK с нуля (без Android Studio)

```bash
mkdir -p ~/android-sdk/cmdline-tools
curl -L -o cmdtools.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip cmdtools.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest

export ANDROID_HOME=~/android-sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

## Сборка через GitHub Actions (без локального SDK)

В репозитории настроен workflow `.github/workflows/android-build.yml`:

1. Push в любую ветку → автоматически запускаются unit-тесты и `assembleRelease`.
2. Готовый APK скачивается на странице **Actions → последний запуск → Artifacts →
   MeteoAnalyst-release-apk**.

## Unit-тесты

```bash
./gradlew testDebugUnitTest
```

Тестируются формулы: суточный скор, EMA-рейтинг за 7 дней, ансамбль,
достоверность (`app/src/test/java/com/meteoanalyst/app/`).

## Структура релиза

```
app/build/outputs/
└── apk/release/
    └── app-release.apk        # подписан debug-ключом, готов к sideload
```

## Частые проблемы

| Ошибка | Решение |
|---|---|
| `SDK location not found` | создайте `local.properties` с `sdk.dir=...` или задайте `ANDROID_HOME` |
| `Unsupported class file major version` | вы используете не JDK 17 — проверьте `java -version` |
| `Failed to install` при `adb install` | включите «Установка из неизвестных источников» |
| Gradle не скачивает зависимости | проверьте доступ к `dl.google.com` и `repo.maven.apache.org` |
