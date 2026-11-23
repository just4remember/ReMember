import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Адрес и ключ сервера отзывов — из secrets.properties в корне проекта (в git не попадает,
// пример — secrets.properties.example). Нет файла или значений — отправка отзывов недоступна
val secrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun secret(key: String): String = secrets.getProperty(key, "").trim().replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "dev.chaik.remember"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.chaik.remember"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Только 64-битный ARM — как у телефона. Карта и шифрование базы приносят свой нативный код
        // под каждый процессор, и без фильтра приложение весило бы в разы больше
        ndk { abiFilters += "arm64-v8a" }

        buildConfigField("String", "FEEDBACK_ENDPOINT", "\"${secret("feedback.endpoint")}\"")
        buildConfigField("String", "FEEDBACK_KEY", "\"${secret("feedback.key")}\"")
        // Ссылка на сбор средств «Поддержать автора»; пусто — раздела нет
        buildConfigField("String", "SUPPORT_URL", "\"${secret("support.url")}\"")
        // Сколько отзывов в день можно отправить с телефона; нет значения или оно неверное — 5
        val feedbackLimit = secret("feedback.dailyLimit").toIntOrNull()?.takeIf { it > 0 } ?: 5
        buildConfigField("int", "FEEDBACK_DAILY_LIMIT", feedbackLimit.toString())
    }

    buildTypes {
        release {
            // Приложение только для себя: релиз подписываем отладочным ключом, чтобы ставить его
            // на телефон напрямую (`gradlew installRelease`) — он заметно быстрее отладочной сборки
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles("proguard-rules.pro")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    // Схемы базы хранятся в git — по ним Room строит миграции между версиями
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.coil.compose)
    // Картинки выпусков «Ра[скобок]» — SVG
    implementation(libs.coil.svg)
    // Видео в записях: сжатие до 720p при вставке и плеер
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.biometric)
    // biometric тянет старый Fragment, с которым запросы разрешений работают неправильно
    implementation(libs.androidx.fragment)
    // Шаги, калории и тренировки из Samsung Health
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.lifecycle.process)
    // Карта мест: векторная карта и скачивание городов для офлайна
    implementation(libs.maplibre.android)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.kotlinx.serialization.core)
    // Импорт тренировок из JSON
    implementation(libs.kotlinx.serialization.json)
    // Явная версия корутин: иначе в приложении оказывается старая (от Room/Lifecycle),
    // а тестам нужна та же, что у kotlinx-coroutines-test
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}