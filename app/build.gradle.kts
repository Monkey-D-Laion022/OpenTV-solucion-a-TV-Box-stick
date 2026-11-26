import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.opentv.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.opentv.app"
        // Android 7.0. Decisión de la Fase 0: Media3, Compose y Nearby Connections lo admiten.
        minSdk = 24
        targetSdk = 34
        // Creciente entre builds Y entre workflows (el número de ejecución de GitHub es distinto en cada workflow): minutos desde 1970.
        // Así un APK nuevo siempre se instala ENCIMA del anterior («Actualizar») sin desinstalar. Ver docs/ACTUALIZAR-ENCIMA.md.
        versionCode = (System.currentTimeMillis() / 60_000L).toInt()
        // Versión real de entrega (antes siempre «0.1.0»). OPENTV_VERSION se sube a mano en cada ronda; se le suma la fecha/hora UTC del build
        // para distinguir dos compilaciones de la misma ronda. Ajustes → Sistema muestra versión + número de compilación + huella de firma.
        versionName = "6.8.0+" + LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmm"))
    }

    // Firma de depuración FIJA y versionada (app/opentv-debug.keystore, clave pública de pruebas «android»): todos los workflows firman igual,
    // requisito de Android para actualizar encima. NO sirve para publicar en una tienda.
    signingConfigs {
        getByName("debug") {
            storeFile = file("opentv-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        aidl = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.androidx.tv.material) // solo lo usa com.opentv.home (prueba de Home v3)
    if (providers.gradleProperty("withNova").orNull == "true") implementation(project(":homenova")) // Home 2 de prueba (opcional)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
