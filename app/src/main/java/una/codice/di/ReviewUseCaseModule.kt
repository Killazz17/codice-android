package una.codice.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import una.codice.core.domain.repository.ReviewRepository
import una.codice.core.domain.usecase.review.GenerateReviewsUseCase
import una.codice.core.domain.usecase.review.GetPendingReviewsUseCase
import una.codice.core.domain.usecase.review.GetScheduledReviewsUseCase
import una.codice.core.domain.usecase.review.RecordReviewResultUseCase

/**
 * Construye los casos de uso del quiz de repaso para que Hilt se los pueda entregar a `ReviewViewModel`.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReviewUseCaseModule {

    @Provides
    fun provideGetPendingReviewsUseCase(reviewRepository: ReviewRepository): GetPendingReviewsUseCase =
        GetPendingReviewsUseCase(reviewRepository)

    @Provides
    fun provideGetScheduledReviewsUseCase(reviewRepository: ReviewRepository): GetScheduledReviewsUseCase =
        GetScheduledReviewsUseCase(reviewRepository)

    @Provides
    fun provideGenerateReviewsUseCase(reviewRepository: ReviewRepository): GenerateReviewsUseCase =
        GenerateReviewsUseCase(reviewRepository)

    @Provides
    fun provideRecordReviewResultUseCase(reviewRepository: ReviewRepository): RecordReviewResultUseCase =
        RecordReviewResultUseCase(reviewRepository)
}
