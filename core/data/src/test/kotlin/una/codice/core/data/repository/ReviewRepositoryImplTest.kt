package una.codice.core.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import una.codice.core.data.network.review.PendingReviewDto
import una.codice.core.data.network.review.QuizOriginDto
import una.codice.core.data.network.review.QuizQuestionDto
import una.codice.core.data.network.review.ReviewApiService
import una.codice.core.data.network.review.ReviewResultRequestDto
import una.codice.core.data.network.review.ReviewScheduleDto
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.ReviewOutcome
import una.codice.core.domain.model.ReviewSchedule
import una.codice.core.domain.result.DomainResult

/**
 * Tests de [ReviewRepositoryImpl] contra un [ReviewApiService] mockeado con MockK.
 */
class ReviewRepositoryImplTest {

    private val api = mockk<ReviewApiService>()
    private val repository = ReviewRepositoryImpl(api)

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    private fun reviewDto(id: Long = 7, nextDate: String = "2026-10-29") = PendingReviewDto(
        id = id,
        subtopicId = 44,
        subtopicCode = "4.4",
        subtopicTitle = "Servicios REST",
        intervalDay = 3,
        nextDate = nextDate,
        questions = listOf(
            QuizQuestionDto(
                enunciado = "¿Qué verbo HTTP crea un recurso?",
                opciones = listOf("GET", "POST", "DELETE"),
                correcta = 1,
                explicacion = "POST crea recursos.",
            ),
        ),
        origin = QuizOriginDto(noteId = 12, justification = "Tu apunte no menciona los verbos."),
    )

    @Test
    fun `getPendingReviews traduce los DTO del backend a modelos de dominio`() = runTest {
        coEvery { api.getPending() } returns listOf(reviewDto())

        val repasos = (repository.getPendingReviews() as DomainResult.Success).data

        val repaso = repasos.single()
        assertEquals("7", repaso.id)
        assertEquals("44", repaso.subtopicId)
        assertEquals("4.4", repaso.subtopicCode)
        assertEquals("Servicios REST", repaso.subtopicTitle)
        assertEquals(3, repaso.intervalDay)
        assertEquals(LocalDate.of(2026, 10, 29), repaso.nextDate)
        assertEquals("12", repaso.origin?.noteId)
        assertEquals("Tu apunte no menciona los verbos.", repaso.origin?.justification)
        val pregunta = repaso.questions.single()
        assertEquals("¿Qué verbo HTTP crea un recurso?", pregunta.statement)
        assertEquals(listOf("GET", "POST", "DELETE"), pregunta.options)
        assertEquals(1, pregunta.correctIndex)
        assertEquals("POST crea recursos.", pregunta.explanation)
    }

    @Test
    fun `getScheduledReviews pide los programados y los traduce a modelos de dominio`() = runTest {
        coEvery { api.getScheduled() } returns listOf(reviewDto(id = 8, nextDate = "2026-11-03"))

        val repaso = (repository.getScheduledReviews() as DomainResult.Success).data.single()

        assertEquals("8", repaso.id)
        assertEquals(LocalDate.of(2026, 11, 3), repaso.nextDate)
        coVerify(exactly = 0) { api.getPending() }
    }

    @Test
    fun `getScheduledReviews convierte un 403 en un error de dominio y no en un cierre`() = runTest {
        coEvery { api.getScheduled() } throws httpError(403)

        val resultado = repository.getScheduledReviews()

        assertEquals(DomainResult.Failure(DomainError.Unknown("Los repasos son solo para estudiantes")), resultado)
    }

    @Test
    fun `getPendingReviews tolera un repaso sin subtema ni origen`() = runTest {
        coEvery { api.getPending() } returns listOf(
            PendingReviewDto(id = 1, subtopicId = 2, nextDate = "2026-10-29"),
        )

        val repaso = (repository.getPendingReviews() as DomainResult.Success).data.single()

        assertEquals("", repaso.subtopicCode)
        assertEquals(null, repaso.origin)
        assertTrue(repaso.questions.isEmpty())
    }

    // Una fecha ilegible es un error controlado, no un cierre de la app.
    @Test
    fun `una fecha invalida del servidor es un error y no una excepcion`() = runTest {
        coEvery { api.getPending() } returns listOf(reviewDto(nextDate = "mañana"))

        val resultado = repository.getPendingReviews()

        assertEquals(DomainResult.Failure(DomainError.Unknown()), resultado)
    }

    @Test
    fun `sin conexion getPendingReviews devuelve el error de red`() = runTest {
        coEvery { api.getPending() } throws IOException("sin red")

        assertEquals(DomainResult.Failure(DomainError.Network()), repository.getPendingReviews())
    }

    @Test
    fun `un 401 se avisa como sesion vencida y un 403 como repasos solo para estudiantes`() = runTest {
        coEvery { api.getPending() } throws httpError(401) andThenThrows httpError(403)

        assertEquals(DomainResult.Failure(DomainError.Unauthorized()), repository.getPendingReviews())
        assertEquals(
            DomainResult.Failure(DomainError.Unknown("Los repasos son solo para estudiantes")),
            repository.getPendingReviews(),
        )
    }

    @Test
    fun `generateReviews devuelve los repasos nuevos`() = runTest {
        coEvery { api.generate() } returns listOf(reviewDto(id = 8))

        val repasos = (repository.generateReviews() as DomainResult.Success).data

        assertEquals(listOf("8"), repasos.map { it.id })
    }

    @Test
    fun `generateReviews explica el 503 cuando la IA no responde`() = runTest {
        coEvery { api.generate() } throws httpError(503)

        assertEquals(
            DomainResult.Failure(DomainError.Unknown("La IA no está disponible en este momento. Intenta de nuevo más tarde")),
            repository.generateReviews(),
        )
    }

    @Test
    fun `recordResult manda PASSED o FAILED y devuelve cuando vuelve a tocar`() = runTest {
        coEvery { api.recordResult(7, any()) } returns ReviewScheduleDto(id = 7, intervalDay = 7, nextDate = "2026-11-04")

        val resultado = repository.recordResult("7", ReviewOutcome.PASSED)

        assertEquals(DomainResult.Success(ReviewSchedule(7, LocalDate.of(2026, 11, 4))), resultado)
        coVerify { api.recordResult(7, ReviewResultRequestDto("PASSED")) }

        repository.recordResult("7", ReviewOutcome.FAILED)

        coVerify { api.recordResult(7, ReviewResultRequestDto("FAILED")) }
    }

    @Test
    fun `recordResult con un id que no es numerico falla sin llamar al servidor`() = runTest {
        val resultado = repository.recordResult("abc", ReviewOutcome.PASSED)

        assertEquals(DomainResult.Failure(DomainError.Unknown("No se pudo identificar el repaso")), resultado)
        coVerify(exactly = 0) { api.recordResult(any(), any()) }
    }

    @Test
    fun `recordResult explica los errores del servidor`() = runTest {
        coEvery { api.recordResult(7, any()) } throws httpError(422) andThenThrows httpError(403) andThenThrows
            httpError(404) andThenThrows httpError(400)

        val mensajes = List(4) {
            (repository.recordResult("7", ReviewOutcome.PASSED) as DomainResult.Failure).error.message
        }

        assertEquals(
            listOf(
                "Este repaso todavía no vence",
                "Solo el dueño del repaso puede registrar su resultado",
                "No se encontró el repaso en el servidor",
                "El resultado del repaso no es válido",
            ),
            mensajes,
        )
    }
}
