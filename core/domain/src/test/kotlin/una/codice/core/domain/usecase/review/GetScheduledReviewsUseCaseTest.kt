package una.codice.core.domain.usecase.review

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.result.DomainResult

class GetScheduledReviewsUseCaseTest {

    private val repository = FakeReviewRepository()
    private val useCase = GetScheduledReviewsUseCase(repository)

    @Test
    fun `entrega los repasos programados que da el repositorio`() = runTest {
        val repasos = listOf(pendingReview("1"), pendingReview("2"))
        repository.scheduledResult = DomainResult.Success(repasos)

        val resultado = useCase() as DomainResult.Success

        assertEquals(repasos, resultado.data)
    }

    @Test
    fun `no toma los repasos pendientes de hoy`() = runTest {
        repository.pendingResult = DomainResult.Success(listOf(pendingReview("1")))
        repository.scheduledResult = DomainResult.Success(listOf(pendingReview("2")))

        val resultado = useCase() as DomainResult.Success

        assertEquals(listOf("2"), resultado.data.map { it.id })
    }

    @Test
    fun `descarta el repaso sin preguntas que se puedan responder`() = runTest {
        repository.scheduledResult = DomainResult.Success(
            listOf(pendingReview("1", questions = listOf(quizQuestion(correctIndex = 9))), pendingReview("2")),
        )

        val resultado = useCase() as DomainResult.Success

        assertEquals(listOf("2"), resultado.data.map { it.id })
    }

    @Test
    fun `propaga el error del repositorio`() = runTest {
        repository.scheduledResult = DomainResult.Failure(DomainError.Network())

        val resultado = useCase()

        assertEquals(DomainResult.Failure(DomainError.Network()), resultado)
    }
}
