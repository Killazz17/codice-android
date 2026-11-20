package una.codice.feature.coverage.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.CoverageStatus
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.usecase.auth.ObserveSessionUseCase
import una.codice.core.domain.usecase.coverage.GetCoverageMapUseCase

/**
 * Tests de [CoverageViewModel]: transiciones de [CoverageUiState] contra
 * dobles reales ([FakeAuthRepository], [FakeCoverageRepository]), mismo
 * criterio que [una.codice.feature.auth.presentation.AuthViewModelTest].
 *
 * Ver el KDoc de esa clase para el porqué de `setMain`/`resetMain`: sin
 * ellos, `viewModelScope` revienta al no encontrar `Dispatchers.Main` en la
 * JVM de los tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoverageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val sesionEstudiante = Session(token = "token", userId = "est-1", role = UserRole.STUDENT)

    private fun subtema(codigo: String, estado: CoverageStatus) = SubtopicCoverage(
        subtopicId = "sub-$codigo",
        subtopicCode = codigo,
        subtopicTitle = "Subtema $codigo",
        moduleNumber = 1,
        moduleTitle = "Fundamentos",
        status = estado,
    )

    private fun crearViewModel(
        session: Session? = sesionEstudiante,
        rows: List<SubtopicCoverage> = emptyList(),
        failure: DomainError? = null,
    ): Pair<CoverageViewModel, FakeCoverageRepository> {
        val coverageRepositorio = FakeCoverageRepository(rows = rows, failure = failure)
        val viewModel = CoverageViewModel(
            observeSession = ObserveSessionUseCase(FakeAuthRepository(seedSession = session)),
            getCoverageMap = GetCoverageMapUseCase(coverageRepositorio),
            courseSelection = FakeCourseSelectionRepository(),
        )
        return viewModel to coverageRepositorio
    }

    @Test
    fun `el estado inicial es Loading antes de resolver la sesion`() {
        // Sin avanzar el dispatcher todavia: init{} arranco pero no corrio.
        val (viewModel, _) = crearViewModel()

        assertTrue(viewModel.state.value is CoverageUiState.Loading)
    }

    @Test
    fun `con subtemas devueltos el estado pasa a Content con el mapa agregado`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel(rows = listOf(subtema("1.1", CoverageStatus.COVERED)))

        testDispatcher.scheduler.advanceUntilIdle()

        val estado = viewModel.state.value as CoverageUiState.Content
        assertEquals(1, estado.coverageMap.modules.size)
        assertEquals(100.0, estado.coverageMap.globalPercentage, 0.0001)
    }

    @Test
    fun `sin subtemas el estado pasa a Empty, no a Error`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel(rows = emptyList())

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value is CoverageUiState.Empty)
    }

    @Test
    fun `un fallo del repositorio pasa a Error con el mensaje del DomainError`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel(failure = DomainError.Network())

        testDispatcher.scheduler.advanceUntilIdle()

        val estado = viewModel.state.value as CoverageUiState.Error
        assertEquals(DomainError.Network().message, estado.message)
    }

    @Test
    fun `el studentId que llega al repositorio es el de la sesion activa`() = runTest(testDispatcher) {
        val (_, repositorio) = crearViewModel(
            session = Session(token = "t", userId = "est-42", role = UserRole.STUDENT),
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("est-42"), repositorio.calls)
    }

    @Test
    fun `onRetry vuelve a consultar despues de un error`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearViewModel(failure = DomainError.Network())
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value is CoverageUiState.Error)

        viewModel.onRetry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("est-1", "est-1"), repositorio.calls)
        assertTrue(viewModel.state.value is CoverageUiState.Error) // el fake sigue fallando siempre
    }

    @Test
    fun `onRetry no hace nada si el estado no es Error`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearViewModel(rows = listOf(subtema("1.1", CoverageStatus.COVERED)))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value is CoverageUiState.Content)

        viewModel.onRetry()
        testDispatcher.scheduler.advanceUntilIdle()

        // Una sola llamada: la del arranque. onRetry() no debe disparar una segunda.
        assertEquals(listOf("est-1"), repositorio.calls)
    }
}
