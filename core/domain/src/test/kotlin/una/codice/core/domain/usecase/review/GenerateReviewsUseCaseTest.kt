package una.codice.core.domain.usecase.review

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.result.DomainResult

class GenerateReviewsUseCaseTest {

    private val repository = FakeReviewRepository()
    private val useCase = GenerateReviewsUseCase(repository)

    @Test
    fun `pide al repositorio generar los repasos y entrega los nuevos`() = runTest {
        val nuevos = listOf(pendingReview("7"))
        repository.generateResult = DomainResult.Success(nuevos)

        val resultado = useCase()

        assertEquals(1, repository.generateCalls)
        assertEquals(DomainResult.Success(nuevos), resultado)
    }

    @Test
    fun `propaga el error cuando la IA no responde`() = runTest {
        val error = DomainError.Unknown("La IA no está disponible en este momento")
        repository.generateResult = DomainResult.Failure(error)

        assertEquals(DomainResult.Failure(error), useCase())
    }
}
