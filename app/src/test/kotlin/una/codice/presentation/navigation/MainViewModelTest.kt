package una.codice.presentation.navigation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import una.codice.core.domain.model.NewUser
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.auth.ObserveSessionUseCase

/**
 * Tests de [MainViewModel]: solo el mapeo `Session? -> SessionState`, que es
 * toda la lógica que tiene esta clase — el resto es delegar en
 * [ObserveSessionUseCase]. No prueba navegación (eso se verifica a mano en
 * el emulador, ver el plan del issue #5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `arranca en Loading antes de que ObserveSessionUseCase emita`() = runTest(testDispatcher) {
        val viewModel = MainViewModel(ObserveSessionUseCase(FakeAuthRepository(sesionInicial = null)))

        // Sin avanzar el dispatcher: el StateFlow interno (stateIn) todavia
        // no tuvo oportunidad de recolectar el primer valor real.
        assertEquals(SessionState.Loading, viewModel.sessionState.value)
    }

    @Test
    fun `sin sesion guardada pasa a LoggedOut`() = runTest(testDispatcher) {
        val viewModel = MainViewModel(ObserveSessionUseCase(FakeAuthRepository(sesionInicial = null)))
        // WhileSubscribed solo arranca el Flow interno mientras haya un
        // suscriptor activo — leer .value no cuenta como suscribirse. Sin
        // esto, sessionState se queda en Loading para siempre en el test.
        viewModel.sessionState.onEach { }.launchIn(backgroundScope)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SessionState.LoggedOut, viewModel.sessionState.value)
    }

    @Test
    fun `con sesion de estudiante pasa a LoggedIn con ese rol`() = runTest(testDispatcher) {
        val sesion = Session(token = "t", userId = "u", role = UserRole.STUDENT)
        val viewModel = MainViewModel(ObserveSessionUseCase(FakeAuthRepository(sesionInicial = sesion)))
        viewModel.sessionState.onEach { }.launchIn(backgroundScope)

        testDispatcher.scheduler.advanceUntilIdle()

        val estado = viewModel.sessionState.value
        assertTrue(estado is SessionState.LoggedIn)
        assertEquals(UserRole.STUDENT, (estado as SessionState.LoggedIn).role)
    }

    @Test
    fun `con sesion de profesor pasa a LoggedIn con ese rol`() = runTest(testDispatcher) {
        val sesion = Session(token = "t", userId = "u", role = UserRole.TEACHER)
        val viewModel = MainViewModel(ObserveSessionUseCase(FakeAuthRepository(sesionInicial = sesion)))
        viewModel.sessionState.onEach { }.launchIn(backgroundScope)

        testDispatcher.scheduler.advanceUntilIdle()

        val estado = viewModel.sessionState.value
        assertTrue(estado is SessionState.LoggedIn)
        assertEquals(UserRole.TEACHER, (estado as SessionState.LoggedIn).role)
    }
}

/**
 * Fake mínimo de [AuthRepository] solo para este test: [MainViewModel] únicamente
 * observa `currentSession()`, así que es lo único que hace falta implementar
 * de verdad — los demás métodos de la interfaz no se llaman aquí nunca.
 */
private class FakeAuthRepository(sesionInicial: Session?) : AuthRepository {
    private val session = MutableStateFlow(sesionInicial)

    override fun currentSession(): Flow<Session?> = session

    override suspend fun login(email: String, password: String): DomainResult<Session> =
        error("no se usa en este test")

    override suspend fun register(newUser: NewUser): DomainResult<Session> =
        error("no se usa en este test")

    override suspend fun logout(): DomainResult<Unit> = error("no se usa en este test")

    override suspend fun changePassword(currentPassword: String, newPassword: String): DomainResult<Unit> =
        error("no se usa en este test")
}
