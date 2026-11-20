package una.codice.feature.review.presentation

import java.time.LocalDate
import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.model.ReviewOutcome
import una.codice.core.domain.model.ReviewSchedule
import una.codice.core.domain.repository.ReviewRepository
import una.codice.core.domain.result.DomainResult

/**
 * [ReviewRepository] en memoria para los tests del quiz: devuelve lo que el test deje en cada resultado y anota qué se
 * le pidió, sin tocar la red ni la IA.
 */
class FakeReviewRepository : ReviewRepository {

    var pendingResult: DomainResult<List<PendingReview>> = DomainResult.Success(emptyList())
    var scheduledResult: DomainResult<List<PendingReview>> = DomainResult.Success(emptyList())
    var generateResult: DomainResult<List<PendingReview>> = DomainResult.Success(emptyList())
    var recordResult: DomainResult<ReviewSchedule> = DomainResult.Success(ReviewSchedule(3, LocalDate.of(2026, 10, 31)))

    var pendingCalls = 0
    var generateCalls = 0

    /** Cada resultado que se registró, en orden: el id del repaso y cómo salió. */
    val recorded = mutableListOf<Pair<String, ReviewOutcome>>()

    override suspend fun getPendingReviews(): DomainResult<List<PendingReview>> {
        pendingCalls++
        return pendingResult
    }

    override suspend fun getScheduledReviews(): DomainResult<List<PendingReview>> = scheduledResult

    override suspend fun generateReviews(): DomainResult<List<PendingReview>> {
        generateCalls++
        return generateResult
    }

    override suspend fun recordResult(reviewId: String, outcome: ReviewOutcome): DomainResult<ReviewSchedule> {
        recorded += reviewId to outcome
        return recordResult
    }
}
