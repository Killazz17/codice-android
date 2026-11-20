package una.codice.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import una.codice.core.data.network.apiCall
import una.codice.core.data.network.review.ReviewApiService
import una.codice.core.data.network.review.ReviewResultRequestDto
import una.codice.core.data.network.review.toDomain
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.model.ReviewOutcome
import una.codice.core.domain.model.ReviewSchedule
import una.codice.core.domain.repository.ReviewRepository
import una.codice.core.domain.result.DomainResult

/**
 * [ReviewRepository] contra el backend de Códice. Los repasos y sus intervalos viven en el servidor: la app no guarda
 * una copia ni calcula fechas, solo muestra lo que el servidor entrega y le manda cómo salió cada repaso.
 *
 * La conversión de los DTO va dentro de [apiCall] para que una fecha o un cuerpo inesperado sea un error controlado
 * y no un cierre de la app.
 */
@Singleton
class ReviewRepositoryImpl @Inject constructor(
    private val reviewApiService: ReviewApiService,
) : ReviewRepository {

    override suspend fun getPendingReviews(): DomainResult<List<PendingReview>> =
        apiCall(onHttpError = ::commonError) { reviewApiService.getPending().map { it.toDomain() } }

    override suspend fun getScheduledReviews(): DomainResult<List<PendingReview>> =
        apiCall(onHttpError = ::commonError) { reviewApiService.getScheduled().map { it.toDomain() } }

    override suspend fun generateReviews(): DomainResult<List<PendingReview>> =
        apiCall(onHttpError = ::generateError) { reviewApiService.generate().map { it.toDomain() } }

    override suspend fun recordResult(reviewId: String, outcome: ReviewOutcome): DomainResult<ReviewSchedule> {
        val id = reviewId.toLongOrNull()
            ?: return DomainResult.Failure(DomainError.Unknown("No se pudo identificar el repaso"))

        return apiCall(onHttpError = ::recordError) {
            reviewApiService.recordResult(id, ReviewResultRequestDto(result = outcome.name)).toDomain()
        }
    }

    private fun commonError(code: Int): DomainError? = when (code) {
        401 -> DomainError.Unauthorized()
        403 -> DomainError.Unknown("Los repasos son solo para estudiantes")
        else -> null
    }

    private fun generateError(code: Int): DomainError? = when (code) {
        503 -> DomainError.Unknown("La IA no está disponible en este momento. Intenta de nuevo más tarde")
        else -> commonError(code)
    }

    private fun recordError(code: Int): DomainError? = when (code) {
        400 -> DomainError.Unknown("El resultado del repaso no es válido")
        403 -> DomainError.Unknown("Solo el dueño del repaso puede registrar su resultado")
        404 -> DomainError.Unknown("No se encontró el repaso en el servidor")
        422 -> DomainError.Unknown("Este repaso todavía no vence")
        else -> commonError(code)
    }
}
