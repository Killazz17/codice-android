package una.codice.feature.review.presentation

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.model.QuizQuestion
import una.codice.core.domain.model.ReviewOutcome
import una.codice.core.domain.model.ReviewSchedule
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.review.GenerateReviewsUseCase
import una.codice.core.domain.usecase.review.GetPendingReviewsUseCase
import una.codice.core.domain.usecase.review.GetScheduledReviewsUseCase
import una.codice.core.domain.usecase.review.RecordReviewResultUseCase

/**
 * Recorre el quiz como lo haría el estudiante contra un [FakeReviewRepository]. Igual que en los demás tests de
 * ViewModel, `Dispatchers.Main` se reemplaza antes de crearlo porque su `init {}` ya pide los repasos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeReviewRepository
    private lateinit var viewModel: ReviewViewModel

    @Before
    fun antesDeCadaTest() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeReviewRepository()
    }

    @After
    fun despuesDeCadaTest() {
        Dispatchers.resetMain()
    }

    private fun pregunta(correcta: Int = 1) = QuizQuestion(
        statement = "¿Cuál es la correcta?",
        options = listOf("A", "B", "C"),
        correctIndex = correcta,
        explanation = "Porque sí.",
    )

    private fun repaso(id: String, preguntas: List<QuizQuestion> = listOf(pregunta())) = PendingReview(
        id = id,
        subtopicId = "s$id",
        subtopicCode = "4.$id",
        subtopicTitle = "Subtema $id",
        intervalDay = 1,
        nextDate = LocalDate.of(2026, 10, 28),
        questions = preguntas,
        origin = null,
    )

    /** Crea el ViewModel con los repasos dados (o ninguno) y corre su `init {}`. */
    private fun abrir(vararg repasos: PendingReview) {
        repository.pendingResult = DomainResult.Success(repasos.toList())
        crearViewModel()
    }

    private fun crearViewModel() {
        viewModel = ReviewViewModel(
            getPendingReviews = GetPendingReviewsUseCase(repository),
            getScheduledReviews = GetScheduledReviewsUseCase(repository),
            generateReviews = GenerateReviewsUseCase(repository),
            recordReviewResult = RecordReviewResultUseCase(repository),
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun quiz() = viewModel.state.value as ReviewUiState.Quiz

    /** Elige la opción, comprueba y continúa, y deja correr el guardado del resultado. */
    private fun responder(opcion: Int) {
        viewModel.onSelectOption(opcion)
        viewModel.onCheck()
        viewModel.onContinue()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `al abrir pide los repasos y muestra la primera pregunta`() = runTest(testDispatcher) {
        abrir(repaso("1"), repaso("2"))

        val estado = quiz()
        assertEquals("1", estado.review.id)
        assertEquals(1, estado.position)
        assertEquals(2, estado.total)
        assertEquals(0, estado.questionIndex)
        assertNull(estado.selectedOption)
        assertFalse(estado.isChecked)
    }

    @Test
    fun `sin repasos que toquen muestra el estado vacio`() = runTest(testDispatcher) {
        abrir()

        assertEquals(ReviewUiState.Empty(), viewModel.state.value)
    }

    @Test
    fun `si falla al abrir muestra el error y reintentar vuelve a pedir los repasos`() = runTest(testDispatcher) {
        repository.pendingResult = DomainResult.Failure(DomainError.Network())
        crearViewModel()
        assertEquals(ReviewUiState.Error(DomainError.Network().message), viewModel.state.value)

        repository.pendingResult = DomainResult.Success(listOf(repaso("1")))
        viewModel.onRetry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value is ReviewUiState.Quiz)
        assertEquals(2, repository.pendingCalls)
    }

    @Test
    fun `elegir y comprobar revela si acerto`() = runTest(testDispatcher) {
        abrir(repaso("1"))

        viewModel.onSelectOption(1)
        viewModel.onCheck()

        assertTrue(quiz().isChecked)
        assertTrue(quiz().isCorrect)
    }

    @Test
    fun `una opcion equivocada se marca como incorrecta`() = runTest(testDispatcher) {
        abrir(repaso("1"))

        viewModel.onSelectOption(0)
        viewModel.onCheck()

        assertTrue(quiz().isChecked)
        assertFalse(quiz().isCorrect)
    }

    @Test
    fun `no se puede comprobar sin elegir una opcion`() = runTest(testDispatcher) {
        abrir(repaso("1"))

        viewModel.onCheck()

        assertFalse(quiz().isChecked)
    }

    @Test
    fun `una vez comprobada la pregunta no se puede cambiar la respuesta`() = runTest(testDispatcher) {
        abrir(repaso("1"))
        viewModel.onSelectOption(1)
        viewModel.onCheck()

        viewModel.onSelectOption(0)

        assertEquals(1, quiz().selectedOption)
    }

    @Test
    fun `una opcion que no existe se ignora`() = runTest(testDispatcher) {
        abrir(repaso("1"))

        viewModel.onSelectOption(9)

        assertNull(quiz().selectedOption)
    }

    @Test
    fun `no se puede continuar antes de comprobar`() = runTest(testDispatcher) {
        abrir(repaso("1"))
        viewModel.onSelectOption(1)

        viewModel.onContinue()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.recorded.isEmpty())
    }

    @Test
    fun `acertar y continuar registra PASSED y pasa al siguiente repaso`() = runTest(testDispatcher) {
        abrir(repaso("1"), repaso("2"))

        responder(1)

        assertEquals(listOf("1" to ReviewOutcome.PASSED), repository.recorded)
        val estado = quiz()
        assertEquals("2", estado.review.id)
        assertEquals(2, estado.position)
        assertNull(estado.selectedOption)
        assertFalse(estado.isChecked)
    }

    @Test
    fun `fallar y continuar registra FAILED`() = runTest(testDispatcher) {
        abrir(repaso("1"), repaso("2"))

        responder(0)

        assertEquals(listOf("1" to ReviewOutcome.FAILED), repository.recorded)
    }

    @Test
    fun `al terminar el ultimo repaso muestra el resumen con aprobados y fallados`() = runTest(testDispatcher) {
        abrir(repaso("1"), repaso("2"))
        repository.recordResult = DomainResult.Success(ReviewSchedule(7, LocalDate.of(2026, 11, 4)))

        responder(1)
        repository.recordResult = DomainResult.Success(ReviewSchedule(1, LocalDate.of(2026, 10, 29)))
        responder(0)

        val resumen = viewModel.state.value as ReviewUiState.Finished
        assertEquals(1, resumen.passedCount)
        assertEquals(1, resumen.failedCount)
        assertEquals(
            listOf(
                ReviewResultLine("4.1 Subtema 1", passed = true, nextIntervalDay = 7),
                ReviewResultLine("4.2 Subtema 2", passed = false, nextIntervalDay = 1),
            ),
            resumen.results,
        )
    }

    @Test
    fun `con varias preguntas solo guarda al responder la ultima`() = runTest(testDispatcher) {
        abrir(repaso("1", preguntas = listOf(pregunta(correcta = 0), pregunta(correcta = 2))))

        responder(0)

        assertTrue(repository.recorded.isEmpty())
        assertEquals(1, quiz().questionIndex)
        assertNull(quiz().selectedOption)

        responder(2)

        assertEquals(listOf("1" to ReviewOutcome.PASSED), repository.recorded)
    }

    @Test
    fun `con varias preguntas fallar una basta para no aprobar`() = runTest(testDispatcher) {
        abrir(repaso("1", preguntas = listOf(pregunta(correcta = 0), pregunta(correcta = 2))))

        responder(0)
        responder(1)

        assertEquals(listOf("1" to ReviewOutcome.FAILED), repository.recorded)
    }

    // Si el servidor no confirma, el estudiante sigue en la pregunta con el motivo y no pierde lo que respondió.
    @Test
    fun `si guardar falla se queda en la pregunta y reintentar no pide responder otra vez`() = runTest(testDispatcher) {
        abrir(repaso("1"), repaso("2"))
        repository.recordResult = DomainResult.Failure(DomainError.Unknown("Este repaso todavía no vence"))

        responder(1)

        val conError = quiz()
        assertEquals("1", conError.review.id)
        assertFalse(conError.isSaving)
        assertTrue(conError.isChecked)
        assertEquals(1, conError.selectedOption)
        assertEquals("Este repaso todavía no vence", conError.saveError)

        repository.recordResult = DomainResult.Success(ReviewSchedule(3, LocalDate.of(2026, 10, 31)))
        viewModel.onContinue()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("1" to ReviewOutcome.PASSED, "1" to ReviewOutcome.PASSED), repository.recorded)
        assertEquals("2", quiz().review.id)
        assertNull(quiz().saveError)
    }

    @Test
    fun `un doble toque en continuar no guarda dos veces`() = runTest(testDispatcher) {
        abrir(repaso("1"), repaso("2"))
        viewModel.onSelectOption(1)
        viewModel.onCheck()

        viewModel.onContinue()
        viewModel.onContinue()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.recorded.size)
    }

    @Test
    fun `listo al terminar vuelve a pedir los repasos`() = runTest(testDispatcher) {
        abrir(repaso("1"))
        responder(1)
        assertTrue(viewModel.state.value is ReviewUiState.Finished)

        repository.pendingResult = DomainResult.Success(emptyList())
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReviewUiState.Empty(), viewModel.state.value)
        assertEquals(2, repository.pendingCalls)
    }

    @Test
    fun `generar repasos cuenta cuantos se crearon y que empiezan manana`() = runTest(testDispatcher) {
        abrir()
        repository.generateResult = DomainResult.Success(listOf(repaso("1"), repaso("2")))

        viewModel.onGenerate()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            ReviewUiState.Empty(notice = "Se crearon 2 repasos. Tocan mañana, y puedes practicarlos ahora mismo."),
            viewModel.state.value,
        )
        assertEquals(1, repository.generateCalls)
    }

    @Test
    fun `generar un solo repaso lo dice en singular`() = runTest(testDispatcher) {
        abrir()
        repository.generateResult = DomainResult.Success(listOf(repaso("1")))

        viewModel.onGenerate()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            ReviewUiState.Empty(notice = "Se creó 1 repaso. Toca mañana, y puedes practicarlo ahora mismo."),
            viewModel.state.value,
        )
    }

    @Test
    fun `generar sin vacios de cobertura avisa que no hay nada que repasar`() = runTest(testDispatcher) {
        abrir()
        repository.generateResult = DomainResult.Success(emptyList())

        viewModel.onGenerate()
        testDispatcher.scheduler.advanceUntilIdle()

        val vacio = viewModel.state.value as ReviewUiState.Empty
        assertTrue(vacio.notice!!.startsWith("No hay vacíos de cobertura sin repaso"))
    }

    @Test
    fun `si la IA falla al generar muestra el motivo y se puede volver a intentar`() = runTest(testDispatcher) {
        abrir()
        repository.generateResult = DomainResult.Failure(DomainError.Unknown("La IA no está disponible en este momento"))

        viewModel.onGenerate()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            ReviewUiState.Empty(notice = "La IA no está disponible en este momento"),
            viewModel.state.value,
        )

        viewModel.onGenerate()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, repository.generateCalls)
    }

    @Test
    fun `un doble toque en generar no pide dos veces a la IA`() = runTest(testDispatcher) {
        abrir()

        viewModel.onGenerate()
        viewModel.onGenerate()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.generateCalls)
    }

    // ---------- Practicar antes de tiempo ----------

    @Test
    fun `sin repasos que toquen ofrece practicar los programados`() = runTest(testDispatcher) {
        repository.scheduledResult = DomainResult.Success(listOf(repaso("9")))
        abrir()

        assertEquals(ReviewUiState.Empty(upcoming = listOf(repaso("9"))), viewModel.state.value)
    }

    @Test
    fun `si no se pueden pedir los programados no es un error y solo no se ofrece practicar`() = runTest(testDispatcher) {
        repository.scheduledResult = DomainResult.Failure(DomainError.Network())
        abrir()

        assertEquals(ReviewUiState.Empty(), viewModel.state.value)
    }

    @Test
    fun `practicar recorre los programados y no guarda nada en el servidor`() = runTest(testDispatcher) {
        repository.scheduledResult = DomainResult.Success(listOf(repaso("9")))
        abrir()

        viewModel.onPractice()
        assertTrue(quiz().isPractice)
        responder(1)

        val fin = viewModel.state.value as ReviewUiState.Finished
        assertTrue(fin.isPractice)
        assertEquals(listOf(ReviewResultLine("4.9 Subtema 9", passed = true, nextIntervalDay = null)), fin.results)
        assertTrue(repository.recorded.isEmpty())
    }

    @Test
    fun `una practica con la respuesta equivocada sale a repetir y tampoco se guarda`() = runTest(testDispatcher) {
        repository.scheduledResult = DomainResult.Success(listOf(repaso("9")))
        abrir()

        viewModel.onPractice()
        responder(0)

        val fin = viewModel.state.value as ReviewUiState.Finished
        assertEquals(1, fin.failedCount)
        assertTrue(repository.recorded.isEmpty())
    }

    @Test
    fun `practicar sin repasos programados no hace nada`() = runTest(testDispatcher) {
        abrir()

        viewModel.onPractice()

        assertEquals(ReviewUiState.Empty(), viewModel.state.value)
    }

    @Test
    fun `al terminar la practica Listo vuelve a la pantalla de repasos y se puede volver a practicar`() = runTest(testDispatcher) {
        repository.scheduledResult = DomainResult.Success(listOf(repaso("9")))
        abrir()
        viewModel.onPractice()
        responder(1)

        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ReviewUiState.Empty(upcoming = listOf(repaso("9"))), viewModel.state.value)

        viewModel.onPractice()
        assertTrue(quiz().isPractice)
        assertEquals(1, quiz().position)
    }

    @Test
    fun `al generar repasos nuevos se ofrecen para practicarlos ahora`() = runTest(testDispatcher) {
        abrir()
        repository.generateResult = DomainResult.Success(listOf(repaso("1"), repaso("2")))
        repository.scheduledResult = DomainResult.Success(listOf(repaso("1"), repaso("2")))

        viewModel.onGenerate()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, (viewModel.state.value as ReviewUiState.Empty).upcoming.size)
    }

    @Test
    fun `generar cuando todos los vacios ya tienen repaso lo dice y sigue ofreciendo practicar`() = runTest(testDispatcher) {
        repository.scheduledResult = DomainResult.Success(listOf(repaso("1")))
        abrir()
        repository.generateResult = DomainResult.Success(emptyList())

        viewModel.onGenerate()
        testDispatcher.scheduler.advanceUntilIdle()

        val vacio = viewModel.state.value as ReviewUiState.Empty
        assertTrue(vacio.notice!!.startsWith("Ya tienes repasos para todos tus vacíos"))
        assertEquals(1, vacio.upcoming.size)
    }
}
