package una.codice.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import una.codice.core.domain.repository.CoverageRepository
import una.codice.core.domain.usecase.coverage.GetCoverageMapUseCase

/**
 * Construye los casos de uso de cobertura para que Hilt se los pueda
 * entregar a `CoverageViewModel` (`:feature:coverage`, issue #22).
 *
 * Mismo motivo que `AuthUseCaseModule` — ver su KDoc para el porqué completo
 * de que este puente viva en `:app` y no en `:core:domain` ni en el propio
 * feature: [GetCoverageMapUseCase] es una clase plana sin `@Inject`, y quien
 * la construye necesita un [CoverageRepository] real, que solo `:core:data`
 * implementa (`CoverageModule.kt`, issue #23) y que `:feature:coverage`
 * tiene prohibido conocer.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoverageUseCaseModule {

    @Provides
    fun provideGetCoverageMapUseCase(
        coverageRepository: CoverageRepository,
    ): GetCoverageMapUseCase = GetCoverageMapUseCase(coverageRepository)
}
