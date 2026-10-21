plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.lector"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.lector"
        minSdk = 26
        targetSdk = 35
        // En GitHub Actions el número sube solo en cada compilación (1000 + número de ejecución): así cada versión
        // publicada es mayor que la anterior y Android la instala como actualización. Compilado a mano: 9.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 1000 + it } ?: 9
        versionName = "0.10"
    }

    // Una sola firma fija para todas las versiones (antes cada compilación de GitHub creaba una clave de depuración
    // nueva y Android no dejaba instalar encima: había que desinstalar). La clave está en el repositorio a propósito,
    // para que cualquier compilación pueda actualizar a la anterior; ver HANDOFF (PASO 23).
    signingConfigs {
        create("shiori") {
            storeFile = file("firma/shiori.jks")
            storePassword = "shiori-lector"
            keyAlias = "shiori"
            keyPassword = "shiori-lector"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shiori")
        }
        release {
            // Como Komikku: se reduce el código pero sin ofuscar, para que las extensiones encuentren las clases por nombre.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shiori")
            // Solo para la prueba automática en el emulador (gradle assembleRelease -Pprueba).
            isDebuggable = project.hasProperty("prueba")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Las librerías del motor de fuentes pueden venir con metadatos de un Kotlin más nuevo que el del proyecto.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                // Varias librerías de OkHttp traen el mismo archivo OSGi y Gradle no sabe cuál usar.
                "/META-INF/versions/**/OSGI-INF/MANIFEST.MF",
            )
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    // Trabajos en segundo plano: buscar capítulos nuevos, descargas, revisar extensiones y copias automáticas.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // --- Motor de fuentes (paso 4): source-api de Komikku/Mihon ---
    // Mismas versiones que Komikku: las extensiones se compilan contra ellas y llaman a sus métodos por nombre
    // (con 1.10.2 faltaba runBlockingK y la app se cerraba con NoSuchMethodError al cargar una fuente).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okio:okio:3.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.11.0")
    implementation("io.github.pdvrieze.xmlutil:core-android:0.91.3")
    implementation("io.github.pdvrieze.xmlutil:serialization:0.91.3")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    // Módulos de OkHttp que las extensiones usan por nombre (Brotli, DoH, logging, zstd), como en Komikku.
    implementation("com.squareup.okhttp3:okhttp-brotli:5.3.2")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.3.2")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")
    implementation("com.squareup.okhttp3:okhttp-zstd:5.3.2")
    implementation("io.reactivex:rxjava:1.3.8")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.11.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.github.mihonapp:injekt:91edab2317")
    // Motor de JavaScript que usan las extensiones (JavaScriptEngine), la misma librería y versión que Komikku.
    implementation("com.github.zhanghai.quickjs-java:quickjs-android:547f5b1597")
}
