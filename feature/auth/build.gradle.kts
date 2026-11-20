plugins {
    // Modulo de biblioteca Android, no de aplicacion: :feature:auth no produce
    // un APK propio, es una pieza que :app monta dentro de su NavHost.
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "una.codice.feature.auth"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Solo :core:domain. NUNCA :core:data: esa es la regla de la arquitectura
    // ("feature -> core:domain") — solo :app conoce la capa de datos real.
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Iconos de mostrar/ocultar contraseña (Visibility/VisibilityOff).
    implementation(libs.androidx.material.icons.extended)

    // ViewModel dentro de Compose (collectAsStateWithLifecycle) y el puente
    // que permite pedir un ViewModel con hiltViewModel() desde un composable.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.ui.tooling)
}
