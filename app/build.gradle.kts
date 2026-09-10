plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.meteoanalyst.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.meteoanalyst.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            // Оптимизация веса APK (ТЗ п.8)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Подпись debug-ключом, чтобы собранный APK можно было сразу
            // установить на устройство (sideload). Для публикации в магазин
            // замените на собственный keystore — см. BUILD.md.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX + Lifecycle
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose (BOM фиксирует согласованные версии)
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Векторные погодные иконки (R8 вырезает неиспользуемые)
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Room — хранение проверок и рейтингов
    implementation("androidx.room:room-runtime:2.6.0")
    implementation("androidx.room:room-ktx:2.6.0")
    kapt("androidx.room:room-compiler:2.6.0")

    // WorkManager — фоновая сверка в 15:00 МСК
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Retrofit + Moshi — запросы к Open-Meteo
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.moshi:moshi:1.15.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")

    // Coil — зарезервирован для загрузки удалённых иконок погоды
    // (сейчас используются локальные векторные иконки; R8 вырезает неиспользуемый код)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Unit-тесты формул скора/рейтинга/ансамбля
    testImplementation("junit:junit:4.13.2")
}
