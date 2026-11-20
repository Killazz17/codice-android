// Build de nivel raiz: aqui solo se DECLARAN los plugins disponibles para todo
// el proyecto, no se aplican.
//
// `apply false` significa exactamente eso: "este plugin existe y esta es su
// version, pero no lo actives en el proyecto raiz". Cada modulo (:app,
// :core:domain) decide cuales necesita. Sin esto, Gradle tendria que resolver
// la version del plugin en cada modulo por separado.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
