package una.codice.core.domain.usecase.review

import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.model.QuizQuestion
import una.codice.core.domain.repository.ReviewRepository
import una.codice.core.domain.result.DomainResult

/**
 * Los repasos que toca hacer hoy. Solo entrega preguntas que se pueden responder (al menos dos opciones y una
 * correcta que exista) y descarta el repaso al que no le quede ninguna: el servidor omite las preguntas mal
 * formadas y puede dejar un repaso vacío, y la pantalla no tiene qué mostrar de él.
 */
class GetPendingReviewsUseCase(
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(): DomainResult<List<PendingReview>> =
        when (val result = reviewRepository.getPendingReviews()) {
            is DomainResult.Success -> DomainResult.Success(result.data.onlyAnswerable())
            is DomainResult.Failure -> result
        }
}

/** Deja las preguntas que se pueden responder y descarta el repaso al que no le quede ninguna. */
internal fun List<PendingReview>.onlyAnswerable(): List<PendingReview> =
    map { review -> review.copy(questions = review.questions.filter { it.isAnswerable() }) }
        .filter { it.questions.isNotEmpty() }

private fun QuizQuestion.isAnswerable() = options.size >= 2 && correctIndex in options.indices
