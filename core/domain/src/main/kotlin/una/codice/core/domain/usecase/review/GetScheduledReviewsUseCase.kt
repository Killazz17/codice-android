package una.codice.core.domain.usecase.review

import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.repository.ReviewRepository
import una.codice.core.domain.result.DomainResult

/**
 * Los repasos ya generados que todavía no vencen, para que el estudiante pueda practicarlos antes de tiempo. Igual que
 * [GetPendingReviewsUseCase], solo entrega preguntas que se pueden responder.
 */
class GetScheduledReviewsUseCase(
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(): DomainResult<List<PendingReview>> =
        when (val result = reviewRepository.getScheduledReviews()) {
            is DomainResult.Success -> DomainResult.Success(result.data.onlyAnswerable())
            is DomainResult.Failure -> result
        }
}
