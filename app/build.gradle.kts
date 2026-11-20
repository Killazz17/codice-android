plugins {
    alias(libs.plugins.android.application)

    // Plugin del compilador de Compose. Desde Kotlin 2.0 va como plugin aparte
    // (antes se configuraba con composeOptions). Es el que transforma las
    // funciones @Composable.
    alias(libs.plugins.kotlin.compose)

    // KSP = Kotlin Symbol Processing. Genera codigo en tiempo de compilacion.
    // Aqui lo necesita Hilt para crear las clases de inyeccion.
    alias(libs.plugins.ksp)

    alias(libs.plugins.hilt.android)
}

android {
    // `namespace` es el paquete base del codigo y de la clase R generada.
    namespace = "una.codice"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // `applicationId` es como identifica Google Play a la app; es
        // independiente del namespace, aunque aqui se dejan iguales para no
        // confundir. Se cambio desde com.example.* porque "com.example" es un
        // marcador de posicion que Google no permite publicar.
        applicationId = "una.codice"

        minSdk = 24      // Android 7.0. Cubre practicamente todos los telefonos en uso.
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // La firma del release sale de variables de entorno: ninguna llave ni contrasena vive en el repositorio. Sin ellas
    // (por ejemplo en CI de pruebas) el release queda sin firmar y no se puede instalar.
    //   CODICE_KEYSTORE=ruta\al\archivo.jks  CODICE_KEYSTORE_PASSWORD=...  CODICE_KEY_ALIAS=...  CODICE_KEY_PASSWORD=...
    val releaseKeystore: String? = System.getenv("CODICE_KEYSTORE")
    if (releaseKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("CODICE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CODICE_KEY_ALIAS")
                keyPassword = System.getenv("CODICE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: encoge y ofusca el codigo y quita los recursos que no se usan. Las reglas de lo que no debe tocar
            // (los DTO de la red) estan en src/main/keepRules/rules.keep.
            optimization {
                enable = true
            }
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        // Java 17: debe coincidir con el jvmToolchain(17) de :core:domain para
        // que el bytecode de los dos modulos sea compatible.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        // Activa Compose. Sin esto, las funciones @Composable no compilan.
        compose = true
    }
}

dependencies {
    // --- Modulos propios ---
    // :app conoce tanto a :core:domain (para construir los casos de uso e
    // inyectarlos) como a :core:data (issue #4: AuthRepositoryImpl). Es el
    // UNICO modulo autorizado a conocer la capa de datos; un ViewModel nunca
    // debe importar nada de :core:data directamente.
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    // Issue #6: la pantalla de acceso. :app la monta dentro de su propio
    // arbol de composables (por ahora, directo en MainActivity).
    implementation(project(":feature:auth"))
    // Pantalla de inscripción a cursos
    implementation(project(":feature:enrollment"))
    // Mapa de cobertura (tab "Mapa" de la barra de navegación inferior)
    implementation(project(":feature:coverage"))
    // CodiceNavigationBar — barra de navegación inferior (Figma, nodo 24:114)
    implementation(project(":core:designsystem"))
    // Biblioteca de apuntes (tab "Apuntes" de la barra de navegación inferior)
    implementation(project(":feature:notes"))
    // Temario del profesor (tab "Temario" de la barra de navegación del profesor)
    implementation(project(":feature:syllabus"))
    // Quiz de repaso con repetición espaciada (tab "Repaso" de la barra de navegación inferior)
    implementation(project(":feature:review"))

    // --- AndroidX base ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // Desbloqueo con huella: BiometricPrompt necesita una FragmentActivity (ver settings/Biometric.kt).
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.biometric)

    // --- Compose ---
    // El BOM va con platform(): no aporta codigo, solo fija que versiones de
    // las librerias de Compose son compatibles entre si. Por eso las de abajo
    // se declaran sin numero de version.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // --- Inyeccion de dependencias ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)   // ksp() y no implementation(): genera codigo, no se empaqueta
    // Puente que permite pedir un ViewModel con hiltViewModel() desde un
    // composable (issue #5: MainViewModel en MainActivity).
    implementation(libs.androidx.hilt.navigation.compose)

    // Nota: DataStore ya no se declara aqui. Vive dentro de :core:data
    // (issue #4), que es quien de verdad lo usa; :app solo conoce el
    // resultado a traves de AuthRepository, nunca a DataStore directamente.

    // --- Pruebas unitarias (corren en la JVM, sin dispositivo) ---
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core.testing)

    // --- Pruebas instrumentadas (necesitan emulador o telefono) ---
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // --- Solo en builds de depuracion ---
    debugImplementation(libs.androidx.ui.tooling)        // vista previa en Android Studio
    debugImplementation(libs.androidx.ui.test.manifest)
}
