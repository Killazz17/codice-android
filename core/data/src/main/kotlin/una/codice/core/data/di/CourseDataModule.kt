package una.codice.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import una.codice.core.data.repository.CourseRepositoryImpl
import una.codice.core.domain.repository.CourseRepository

/**
 * Le dice a Hilt qué implementación entregar cuando algo pida un
 * [CourseRepository] (issue #32). Mismo patrón que `AuthModule` y
 * `CoverageModule` — ver el KDoc de `AuthModule` para el porqué de `@Binds`
 * en vez de `@Provides`.
 *
 * Nótese que el archivo se llama `CourseModule.kt` pero la clase de dominio
 * de un módulo del temario es
 * [una.codice.core.domain.model.CourseModule]: son cosas distintas y no se
 * cruzan, porque esta clase de Hilt vive en el paquete `di` y aquélla en
 * `model`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CourseDataModule {

    @Binds
    @Singleton
    abstract fun bindCourseRepository(impl: CourseRepositoryImpl): CourseRepository
}
