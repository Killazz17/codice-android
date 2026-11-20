package una.codice.core.domain.usecase.review

import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.repository.ReviewRepository
import una.codice.core.domain.result.DomainResult

/** Convierte los vacíos de cobertura del estudiante en repasos nuevos (la IA escribe las preguntas en el servidor). */
class GenerateReviewsUseCase(
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(): DomainResult<List<PendingReview>> = reviewRepository.generateReviews()
}
