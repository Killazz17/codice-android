package una.codice.core.domain.usecase.review

import java.time.LocalDate
import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.model.QuizQuestion
import una.codice.core.domain.model.ReviewOutcome
import una.codice.core.domain.model.ReviewSchedule
import una.codice.core.domain.repository.ReviewRepository
import una.codice.core.domain.result.DomainResult

/** [ReviewRepository] en memoria para los tests de los casos de uso: devuelve lo que el test deje en cada resultado. */
class FakeReviewRepository : ReviewRepository {

    var pendingResult: DomainResult<List<PendingReview>> = DomainResult.Success(emptyList())
    var scheduledResult: DomainResult<List<PendingReview>> = DomainResult.Success(emptyList())
    var generateResult: DomainResult<List<PendingReview>> = DomainResult.Success(emptyList())
    var recordResult: DomainResult<ReviewSchedule> = DomainResult.Success(ReviewSchedule(3, LocalDate.of(2026, 10, 31)))

    var generateCalls = 0

    /** Lo último que se registró: el repaso y su resultado. */
    var recordedReviewId: String? = null
    var recordedOutcome: ReviewOutcome? = null

    override suspend fun getPendingReviews(): DomainResult<List<PendingReview>> = pendingResult

    override suspend fun getScheduledReviews(): DomainResult<List<PendingReview>> = scheduledResult

    override suspend fun generateReviews(): DomainResult<List<PendingReview>> {
        generateCalls++
        return generateResult
    }

    override suspend fun recordResult(reviewId: String, outcome: ReviewOutcome): DomainResult<ReviewSchedule> {
        recordedReviewId = reviewId
        recordedOutcome = outcome
        return recordResult
    }
}

fun quizQuestion(correctIndex: Int = 1, options: List<String> = listOf("A", "B", "C")) = QuizQuestion(
    statement = "¿Cuál es la correcta?",
    options = options,
    correctIndex = correctIndex,
    explanation = "Porque sí.",
)

fun pendingReview(id: String = "1", questions: List<QuizQuestion> = listOf(quizQuestion())) = PendingReview(
    id = id,
    subtopicId = "10",
    subtopicCode = "4.3",
    subtopicTitle = "Manejo de errores",
    intervalDay = 1,
    nextDate = LocalDate.of(2026, 10, 28),
    questions = questions,
    origin = null,
)
