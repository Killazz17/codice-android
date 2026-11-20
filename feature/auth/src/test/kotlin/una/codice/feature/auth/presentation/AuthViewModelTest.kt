package una.codice.feature.auth.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.usecase.auth.LoginUseCase
import una.codice.core.domain.usecase.auth.ValidateEmailUseCase

/**
 * Tests de [AuthViewModel]: verifican las transiciones de [AuthUiState] que
 * pide `convenciones.md`, contra un [FakeAuthRepository] real (no un mock) —
 * mismo criterio que ya usan los tests de `:core:domain`.
 *
 * Desde que login y registro se separaron en dos pantallas, este ViewModel
 * solo hace una cosa: intentar [LoginUseCase] y mostrar el resultado. Los
 * casos de "la cuenta no existe, se crea sola" viven ahora en
 * [RegisterViewModelTest].
 *
 * ### `Dispatchers.setMain`
 *
 * `viewModelScope` (usado dentro de [AuthViewModel.onSubmit]) lanza sus
 * corrutinas en `Dispatchers.Main` por defecto, que en un test unitario de
 * JVM pura no existe (no hay un Android real corriendo). `setMain` lo
 * reemplaza por un dispatcher de prueba antes de cada test, y `resetMain` lo
 * devuelve a la normalidad después — si se omite cualquiera de los dos, el
 * test revienta con "Module with the Main dispatcher had failed to
 * initialize".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(cuentas: MutableMap<String, String> = mutableMapOf()): AuthViewModel {
        val repositorio = FakeAuthRepository(cuentas)
        return AuthViewModel(loginUseCase = LoginUseCase(repositorio, ValidateEmailUseCase()))
    }

    @Test
    fun `estado inicial no tiene errores ni esta cargando`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()

        assertTrue(viewModel.state.value.fieldErrors.isEmpty())
        assertTrue(!viewModel.state.value.isLoading)
    }

    @Test
    fun `entrar con credenciales correctas de una cuenta existente navega`() = runTest(testDispatcher) {
        val cuentas = mutableMapOf("ana@una.ac.cr" to "codice2026")
        val viewModel = crearViewModel(cuentas)
        with(viewModel) {
            onEmailChanged("ana@una.ac.cr")
            onPasswordChanged("codice2026")
        }

        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()
        val rolNavegado = viewModel.navigateAsRole.first()

        assertEquals(UserRole.STUDENT, rolNavegado)
        assertTrue(viewModel.state.value.fieldErrors.isEmpty())
    }

    /**
     * Sin el reintento automático como registro, una cuenta que no existe
     * simplemente muestra el error — crear la cuenta es trabajo exclusivo
     * de [RegisterViewModel] ahora.
     */
    @Test
    fun `entrar con una cuenta que no existe muestra credenciales invalidas`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        with(viewModel) {
            onEmailChanged("ana@una.ac.cr")
            onPasswordChanged("codice2026")
        }

        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.state.value.generalError
        assertEquals("Correo o contraseña incorrectos", error)
        assertTrue(!viewModel.state.value.isLoading)
    }

    @Test
    fun `contrasenia incorrecta en una cuenta existente muestra credenciales invalidas`() = runTest(testDispatcher) {
        val cuentas = mutableMapOf("ana@una.ac.cr" to "codice2026")
        val viewModel = crearViewModel(cuentas)
        with(viewModel) {
            onEmailChanged("ana@una.ac.cr")
            onPasswordChanged("otraCosa9")
        }

        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.state.value.generalError
        assertEquals("Correo o contraseña incorrectos", error)
        assertTrue(!viewModel.state.value.isLoading)
    }

    @Test
    fun `un correo con formato invalido se rechaza sin cargar`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        with(viewModel) {
            onEmailChanged("ana-sin-arroba")
            onPasswordChanged("codice2026")
        }

        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.state.value.fieldErrors[DomainError.Field.EMAIL]
        assertTrue(error != null)
        assertTrue(!viewModel.state.value.isLoading)
    }

    @Test
    fun `tocar el boton dos veces rapido no dispara dos inicios de sesion`() = runTest(testDispatcher) {
        val cuentas = mutableMapOf("ana@una.ac.cr" to "codice2026")
        val viewModel = crearViewModel(cuentas)
        with(viewModel) {
            onEmailChanged("ana@una.ac.cr")
            onPasswordChanged("codice2026")
        }

        // El primer onSubmit deja isLoading = true hasta que el dispatcher
        // de prueba avance; un segundo onSubmit mientras tanto debe ser
        // ignorado por el guard `if (current.isLoading) return`.
        viewModel.onSubmit()
        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(!viewModel.state.value.isLoading)
    }
}
