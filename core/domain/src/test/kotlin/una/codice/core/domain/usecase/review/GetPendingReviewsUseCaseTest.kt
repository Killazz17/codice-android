package una.codice.core.domain.usecase.review

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.result.DomainResult

class GetPendingReviewsUseCaseTest {

    private val repository = FakeReviewRepository()
    private val useCase = GetPendingReviewsUseCase(repository)

    @Test
    fun `entrega los repasos que da el repositorio`() = runTest {
        val repasos = listOf(pendingReview("1"), pendingReview("2"))
        repository.pendingResult = DomainResult.Success(repasos)

        val resultado = useCase() as DomainResult.Success

        assertEquals(repasos, resultado.data)
    }

    // El servidor omite las preguntas mal formadas y puede dejar un repaso sin ninguna: no hay nada que mostrar de él.
    @Test
    fun `descarta el repaso que se quedo sin preguntas`() = runTest {
        repository.pendingResult = DomainResult.Success(
            listOf(pendingReview("1", questions = emptyList()), pendingReview("2")),
        )

        val resultado = useCase() as DomainResult.Success

        assertEquals(listOf("2"), resultado.data.map { it.id })
    }

    @Test
    fun `quita las preguntas que no se pueden responder y conserva las demas`() = runTest {
        val buena = quizQuestion(correctIndex = 0)
        val unaOpcion = quizQuestion(options = listOf("única"), correctIndex = 0)
        val correctaInexistente = quizQuestion(correctIndex = 7)
        repository.pendingResult = DomainResult.Success(
            listOf(pendingReview("1", questions = listOf(unaOpcion, buena, correctaInexistente))),
        )

        val resultado = useCase() as DomainResult.Success

        assertEquals(listOf(buena), resultado.data.single().questions)
    }

    @Test
    fun `un repaso cuyas preguntas no sirven ninguna se descarta`() = runTest {
        repository.pendingResult = DomainResult.Success(
            listOf(pendingReview("1", questions = listOf(quizQuestion(correctIndex = 9)))),
        )

        val resultado = useCase() as DomainResult.Success

        assertTrue(resultado.data.isEmpty())
    }

    @Test
    fun `propaga el error del repositorio`() = runTest {
        repository.pendingResult = DomainResult.Failure(DomainError.Network())

        val resultado = useCase()

        assertEquals(DomainResult.Failure(DomainError.Network()), resultado)
    }
}
