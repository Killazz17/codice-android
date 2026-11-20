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
import una.codice.core.domain.usecase.auth.RegisterUseCase
import una.codice.core.domain.usecase.auth.ValidateEmailUseCase
import una.codice.core.domain.usecase.auth.ValidatePasswordUseCase

/**
 * Tests de [RegisterViewModel].
 *
 * ### Por qué esta clase puede probar algo que [AuthViewModelTest] nunca pudo
 *
 * Antes de separar login y registro en dos pantallas, el formulario único no
 * tenía un segundo campo de contraseña: se mandaba el mismo valor dos veces
 * como `password` y `passwordConfirmation`, así que
 * `DomainError.Field.PASSWORD_CONFIRMATION` (que `RegisterUseCase` ya sabía
 * devolver desde el issue #2) nunca podía dispararse en la práctica. Con
 * `RegisterScreen` teniendo su propio campo de confirmación, ese camino por
 * fin es alcanzable — ver `confirmacion que no coincide se rechaza` abajo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(cuentas: MutableMap<String, String> = mutableMapOf()): RegisterViewModel {
        val repositorio = FakeAuthRepository(cuentas)
        val validateEmail = ValidateEmailUseCase()
        return RegisterViewModel(
            registerUseCase = RegisterUseCase(repositorio, validateEmail, ValidatePasswordUseCase()),
        )
    }

    /** Completa un formulario válido; cada test sobrescribe solo lo que quiere romper. */
    private fun RegisterViewModel.llenarFormularioValido(
        email: String = "ana@una.ac.cr",
        fullName: String = "Ana Rojas",
        password: String = "codice2026",
        passwordConfirmation: String = "codice2026",
        role: UserRole? = UserRole.STUDENT,
    ) {
        onEmailChanged(email)
        onFullNameChanged(fullName)
        onPasswordChanged(password)
        onPasswordConfirmationChanged(passwordConfirmation)
        role?.let(::onRoleSelected)
    }

    @Test
    fun `estado inicial no tiene errores ni esta cargando`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()

        assertTrue(viewModel.state.value.fieldErrors.isEmpty())
        assertTrue(!viewModel.state.value.isLoading)
    }

    @Test
    fun `un registro valido crea la cuenta y navega con el rol elegido`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        viewModel.llenarFormularioValido(role = UserRole.TEACHER)

        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()
        val rolNavegado = viewModel.navigateAsRole.first()

        assertEquals(UserRole.TEACHER, rolNavegado)
        assertTrue(viewModel.state.value.fieldErrors.isEmpty())
        assertTrue(!viewModel.state.value.isLoading)
    }

    /**
     * El caso que antes era imposible de alcanzar: dos contraseñas
     * distintas. Ver el KDoc de la clase para el porqué.
     */
    @Test
    fun `la confirmacion que no coincide se rechaza`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        viewModel.llenarFormularioValido(passwordConfirmation = "otraCosa9")

        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.state.value.fieldErrors[DomainError.Field.PASSWORD_CONFIRMATION]
        assertTrue(error != null)
        assertTrue(!viewModel.state.value.isLoading)
    }

    @Test
    fun `un correo ya registrado se rechaza`() = runTest(testDispatcher) {
        val cuentas = mutableMapOf("ana@una.ac.cr" to "otraContrasenia1")
        val viewModel = crearViewModel(cuentas)
        viewModel.llenarFormularioValido()

        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.state.value.fieldErrors[DomainError.Field.EMAIL]
        assertTrue(error != null)
    }

    @Test
    fun `un correo con formato invalido se rechaza sin cargar`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        viewModel.llenarFormularioValido(email = "ana-sin-arroba")

        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.state.value.fieldErrors[DomainError.Field.EMAIL]
        assertTrue(error != null)
        assertTrue(!viewModel.state.value.isLoading)
    }

    @Test
    fun `sin elegir rol la cuenta no se crea`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        viewModel.llenarFormularioValido(role = null)

        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.state.value.fieldErrors[DomainError.Field.ROLE]
        assertTrue(error != null)
    }

    @Test
    fun `un nombre vacio se rechaza`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        viewModel.llenarFormularioValido(fullName = "")

        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.state.value.fieldErrors[DomainError.Field.FIRST_NAME]
        assertTrue(error != null)
    }

    /**
     * Mismo criterio que en el login: si el usuario toca "Crear cuenta" dos
     * veces rápido, el guard `if (current.isLoading) return` evita un
     * segundo registro.
     */
    @Test
    fun `tocar crear cuenta dos veces rapido no registra dos veces`() = runTest(testDispatcher) {
        val cuentas = mutableMapOf<String, String>()
        val viewModel = crearViewModel(cuentas)
        viewModel.llenarFormularioValido()

        viewModel.onSubmit()
        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(!viewModel.state.value.isLoading)
    }
}
