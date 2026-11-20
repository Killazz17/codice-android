package una.codice.core.domain.usecase.review

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.model.ReviewResolution
import una.codice.core.domain.repository.ReviewRepository
import una.codice.core.domain.result.DomainResult

/**
 * Cierra un repaso: decide si el estudiante lo aprobó según sus respuestas y se lo manda al servidor, que lo
 * reprograma (aprobar sube al siguiente intervalo; fallar vuelve a 1 día) y, si aprobó, refuerza la cobertura.
 */
class RecordReviewResultUseCase(
    private val reviewRepository: ReviewRepository,
) {
    /** [chosenOptions] lleva una opción por pregunta, en el orden de [PendingReview.questions]. */
    suspend operator fun invoke(review: PendingReview, chosenOptions: List<Int>): DomainResult<ReviewResolution> {
        if (chosenOptions.size < review.questions.size) {
            return DomainResult.Failure(DomainError.Unknown("Responde todas las preguntas antes de terminar el repaso"))
        }
        val outcome = review.outcomeFor(chosenOptions)
        return when (val result = reviewRepository.recordResult(review.id, outcome)) {
            is DomainResult.Success -> DomainResult.Success(ReviewResolution(outcome, result.data))
            is DomainResult.Failure -> result
        }
    }
}
