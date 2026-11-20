package una.codice.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import una.codice.core.data.repository.ReviewRepositoryImpl
import una.codice.core.domain.repository.ReviewRepository

/**
 * Le dice a Hilt qué implementación entregar cuando algo pida un [ReviewRepository]. Mismo patrón que
 * `CoverageModule.kt`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {

    @Binds
    @Singleton
    abstract fun bindReviewRepository(impl: ReviewRepositoryImpl): ReviewRepository
}
