pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SierraAppMobile"

// Modulos del proyecto. Arquitectura Clean por capas:
//
//   :app          -> punto de entrada. MainActivity, NavHost y la raiz de Hilt.
//                    Es el UNICO modulo que conoce a :core:data.
//   :core:domain  -> entidades y casos de uso en Kotlin puro, sin Android.
//                    Es la capa que se prueba con JUnit, sin emulador.
//   :core:data    -> implementaciones reales de los repositorios de dominio:
//                    DataStore para la sesion, y por ahora un data source
//                    falso en memoria en vez de Retrofit (issue #4).
//   :feature:auth -> pantalla de acceso: ViewModel + composables (issue #6).
//                    Depende solo de :core:domain, nunca de :core:data.
//   :feature:coverage -> Mapa de cobertura: CoverageViewModel (issue #22),
//                    todavia sin sus pantallas (#20/#21) ni montar en :app.
//                    Mismo :core:domain-only que :feature:auth.
//   :feature:notes -> Biblioteca de apuntes: LibraryViewModel (issue #15),
//                    sobre GetNotesUseCase/SearchNotesUseCase (issue #16).
//                    Todavia sin sus pantallas (#14/#17) ni montar en :app.
//
// Pendientes de crear conforme avancen los issues:
//   :core:designsystem ya existe; falta, por ejemplo, la parte de :core:data
//   que respalde a NoteRepository con Room.
//
// Regla de dependencias: feature -> core:domain. Un ViewModel o un composable
// nunca importa nada de :core:data directamente.
include(":app")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":feature:auth")
include(":feature:enrollment")
include(":feature:coverage")
include(":feature:notes")
// :feature:review -> Quiz de repaso con repeticion espaciada (tab "Repaso", issue #42).
include(":feature:review")
// :feature:syllabus -> Cargar/editar el temario del curso (issue #32, rol
//                    profesor). Flujo manual primero; la extraccion por IA
//                    del PDF llega despues, cuando exista esa llamada.
include(":feature:syllabus")
