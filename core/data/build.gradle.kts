import java.io.FileInputStream
import java.util.Properties

plugins {
    // com.android.library y no com.android.application: este modulo no
    // produce un APK, es una pieza que :app consume. Por eso no lleva
    // applicationId ni buildFeatures.compose: aqui no hay UI.
    //
    // Es necesario porque DataStore Preferences se distribuye como AAR, no
    // como JAR. Un modulo Kotlin JVM puro (como :core:domain) no puede
    // resolver un AAR: le falta el soporte de Android que sabe desempacarlo.
    alias(libs.plugins.android.library)

    // Este modulo define clases con @Inject y un @Module de Hilt
    // (AuthModule, DataStoreModule). El plugin de Hilt tiene que estar
    // aplicado AQUI, no solo en :app: es lo que le dice al procesador de KSP
    // que este es un modulo "library" y que agregue sus bindings al grafo
    // final que arma :app.
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)

    // Genera los serializadores de los DTO @Serializable de la capa de red
    // (AuthApiService, NoteApiService y sus request/response). Solo hace
    // falta en :core:data: es el unico modulo que habla HTTP con Retrofit.
    alias(libs.plugins.kotlin.serialization)
}

// Lee local.properties (nunca se sube a git — es distinto por maquina) para
// permitir que cada quien apunte la app a otro backend sin tocar codigo
// compartido. Pensado para quien prueba en un telefono fisico: ahi
// "10.0.2.2" no existe (es un alias exclusivo del emulador), asi que
// necesita su propia IP de LAN o "localhost" + `adb reverse tcp:8080 tcp:8080`.
// El backend desplegado (Google Cloud) se usa poniendo su URL https en api.baseUrl.
// Sin esta linea en su local.properties, todo el mundo sigue viendo el
// mismo default de siempre.
val localProperties = Properties()
run {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        FileInputStream(file).use { stream -> localProperties.load(stream) }
    }
}
// La URL de produccion es la de release y no se cambia desde local.properties: una build de release nunca debe salir
// apuntando a la PC de quien la compilo. local.properties solo afecta a la variante debug.
val productionApiUrl = "https://codice-api-771575174411.us-central1.run.app/"
val debugApiUrl: String = localProperties.getProperty("api.baseUrl") ?: productionApiUrl

android {
    namespace = "una.codice.core.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Debe coincidir con :app: es quien finalmente empaqueta este modulo.
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // NetworkModule.kt lee esto en vez de tener la URL escrita a mano. Por defecto (release) es la de produccion;
        // la variante debug la sobreescribe abajo con la de local.properties.
        buildConfigField("String", "API_BASE_URL", "\"$productionApiUrl\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiUrl\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Mitigacion de bajo costo: si alguna ruta interna de DataStore
        // tocara una API de Android no soportada en tests unitarios locales
        // (que corren contra un android.jar "vacio", sin emulador), esto
        // hace que devuelva un valor por defecto en vez de lanzar. No cambia
        // nada si ese caso nunca ocurre.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // La capa de datos conoce los contratos y modelos de dominio para poder
    // implementarlos y devolverlos. Nunca al reves.
    implementation(project(":core:domain"))

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // --- Red (Retrofit contra el backend de Codice) ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // --- Pruebas ---
    // Nada de Robolectric: los repositorios y SessionPreferences reciben sus dependencias ya construidas por
    // constructor, asi que los tests corren en la JVM pura, igual que los de :core:domain.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // MockK: para mockear AuthApiService/NoteApiService en los tests de los
    // repositorios sin levantar el backend durante `./gradlew test`.
    testImplementation(libs.mockk)
}
