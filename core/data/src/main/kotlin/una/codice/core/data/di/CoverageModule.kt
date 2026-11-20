package una.codice.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import una.codice.core.data.repository.CoverageRepositoryImpl
import una.codice.core.domain.repository.CoverageRepository

/**
 * Le dice a Hilt qué implementación entregar cuando algo pida un
 * [CoverageRepository]. Mismo patrón que `AuthModule.kt` — ver su KDoc para
 * el porqué completo de `@Binds` en vez de `@Provides` aquí.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoverageModule {

    @Binds
    @Singleton
    abstract fun bindCoverageRepository(impl: CoverageRepositoryImpl): CoverageRepository
}
