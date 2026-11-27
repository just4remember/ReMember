import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Ключ сервера отзывов хранится локально в secrets.properties (пример — secrets.properties.example).
val secrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun Properties.configString(key: String): String = getProperty(key, "").trim().replace("\\", "\\\\").replace("\"", "\\\"")

val serverConfig = Properties().apply {
    rootProject.file("server.properties").inputStream().use(::load)
}

val signingProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val useReleaseSigning = providers.gradleProperty("useReleaseSigning").map(String::toBoolean)
    .getOrElse(signingProperties.containsKey("signing.storeFile"))

android {
    namespace = "dev.chaik.remember"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.chaik.remember"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Только 64-битный ARM — как у телефона. Карта и шифрование базы приносят свой нативный код
        // под каждый процессор, и без фильтра приложение весило бы в разы больше
        ndk { abiFilters += "arm64-v8a" }

        buildConfigField("String", "FEEDBACK_ENDPOINT", "\"${serverConfig.configString("feedback.endpoint")}\"")
        buildConfigField("String", "FEEDBACK_KEY", "\"${secrets.configString("feedback.key")}\"")
        buildConfigField("String", "LEGAL_PRIVACY_URL", "\"${serverConfig.configString("legal.privacyUrl")}\"")
        buildConfigField("String", "LEGAL_TERMS_URL", "\"${serverConfig.configString("legal.termsUrl")}\"")
        buildConfigField("String", "LEGAL_CONSENT_URL", "\"${serverConfig.configString("legal.consentUrl")}\"")
        buildConfigField("String", "DICTIONARY_BASE_URL", "\"${serverConfig.configString("dictionary.baseUrl")}\"")
        buildConfigField("String", "DICTIONARY_VERSION_PATH", "\"${serverConfig.configString("dictionary.versionPath")}\"")
        buildConfigField("String", "DICTIONARY_DOWNLOAD_PATH", "\"${serverConfig.configString("dictionary.downloadPath")}\"")
        val dictionaryInterval = serverConfig.getProperty("dictionary.checkIntervalHours").trim().toInt()
        require(dictionaryInterval > 0) { "dictionary.checkIntervalHours должен быть больше нуля" }
        buildConfigField("int", "DICTIONARY_CHECK_INTERVAL_HOURS", dictionaryInterval.toString())
        // Ссылка на сбор средств «Поддержать автора»; пусто — раздела нет
        buildConfigField("String", "SUPPORT_URL", "\"${secrets.configString("support.url")}\"")
        // Сколько отзывов в день можно отправить с телефона; нет значения или оно неверное — 5
        val feedbackLimit = serverConfig.getProperty("feedback.dailyLimit")?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 5
        buildConfigField("int", "FEEDBACK_DAILY_LIMIT", feedbackLimit.toString())
    }

    signingConfigs {
        if (useReleaseSigning) {
            fun required(name: String): String =
                signingProperties.getProperty(name)?.takeIf { it.isNotBlank() }
                    ?: error("Для релизной подписи укажите $name в local.properties")

            create("localRelease") {
                storeFile = rootProject.file(required("signing.storeFile")).also {
                    check(it.isFile) { "Файл из signing.storeFile не найден" }
                }
                storePassword = required("signing.storePassword")
                keyAlias = required("signing.keyAlias")
                keyPassword = signingProperties.getProperty("signing.keyPassword", storePassword)
            }
        }
    }

    buildTypes {
        debug {
            if (useReleaseSigning) signingConfig = signingConfigs.getByName("localRelease")
        }
        release {
            signingConfig = signingConfigs.getByName(if (useReleaseSigning) "localRelease" else "debug")
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
