plugins {
    // Kotlin JVM, NO com.android.library.
    //
    // Esto es deliberado y es la pieza clave de la arquitectura: al no ser un
    // modulo Android, las clases de android.* ni siquiera estan en el classpath.
    // Si alguien escribe `import android.util.Patterns` aqui, el modulo NO
    // COMPILA. El criterio de aceptacion del issue #2 ("los casos de uso no
    // importan nada de android.*") deja de ser un acuerdo de palabra y pasa a
    // estar garantizado por el compilador.
    //
    // Beneficio practico: estos tests corren en milisegundos con JUnit puro,
    // sin emulador y sin Robolectric.
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    // El "toolchain" es la version de Java con la que se compila este modulo.
    // Debe coincidir con la de :app (Java 17) para que el bytecode sea
    // compatible cuando :app dependa de este modulo.
    jvmToolchain(17)
}

dependencies {
    // Coroutines core aporta Flow, que ObserveSessionUseCase necesita para
    // exponer la sesion como un stream al que la UI se suscribe.
    // Es una libreria de Kotlin multiplataforma: no arrastra nada de Android.
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    // runTest{} viene de aqui: permite probar funciones `suspend` sin bloquear
    // el hilo y controlando el tiempo virtualmente.
    testImplementation(libs.kotlinx.coroutines.test)
}
