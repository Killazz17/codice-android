package una.codice.core.domain.usecase.review

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.ReviewOutcome
import una.codice.core.domain.model.ReviewResolution
import una.codice.core.domain.model.ReviewSchedule
import una.codice.core.domain.result.DomainResult

class RecordReviewResultUseCaseTest {

    private val repository = FakeReviewRepository()
    private val useCase = RecordReviewResultUseCase(repository)

    @Test
    fun `acertar la pregunta aprueba el repaso y lo registra en el servidor`() = runTest {
        val repaso = pendingReview("5", questions = listOf(quizQuestion(correctIndex = 1)))
        val agenda = ReviewSchedule(3, LocalDate.of(2026, 10, 31))
        repository.recordResult = DomainResult.Success(agenda)

        val resultado = useCase(repaso, chosenOptions = listOf(1))

        assertEquals("5", repository.recordedReviewId)
        assertEquals(ReviewOutcome.PASSED, repository.recordedOutcome)
        assertEquals(DomainResult.Success(ReviewResolution(ReviewOutcome.PASSED, agenda)), resultado)
    }

    @Test
    fun `fallar la pregunta marca el repaso como fallado`() = runTest {
        val repaso = pendingReview("5", questions = listOf(quizQuestion(correctIndex = 1)))

        val resultado = useCase(repaso, chosenOptions = listOf(0)) as DomainResult.Success

        assertEquals(ReviewOutcome.FAILED, repository.recordedOutcome)
        assertEquals(ReviewOutcome.FAILED, resultado.data.outcome)
    }

    // Con varias preguntas basta fallar una para que el repaso no se apruebe.
    @Test
    fun `con varias preguntas aprueba solo si acierta todas`() = runTest {
        val repaso = pendingReview(questions = listOf(quizQuestion(correctIndex = 0), quizQuestion(correctIndex = 2)))

        useCase(repaso, chosenOptions = listOf(0, 2))
        assertEquals(ReviewOutcome.PASSED, repository.recordedOutcome)

        useCase(repaso, chosenOptions = listOf(0, 1))
        assertEquals(ReviewOutcome.FAILED, repository.recordedOutcome)
    }

    @Test
    fun `con preguntas sin responder no registra nada`() = runTest {
        val repaso = pendingReview(questions = listOf(quizQuestion(), quizQuestion()))

        val resultado = useCase(repaso, chosenOptions = listOf(1))

        assertNull(repository.recordedReviewId)
        assertEquals(
            DomainResult.Failure(DomainError.Unknown("Responde todas las preguntas antes de terminar el repaso")),
            resultado,
        )
    }

    @Test
    fun `propaga el error del servidor`() = runTest {
        repository.recordResult = DomainResult.Failure(DomainError.Unknown("Este repaso todavía no vence"))

        val resultado = useCase(pendingReview(), chosenOptions = listOf(1))

        assertEquals(DomainResult.Failure(DomainError.Unknown("Este repaso todavía no vence")), resultado)
    }
}
