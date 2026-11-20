package una.codice.core.domain.usecase.auth

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.result.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [RegisterUseCase]: una prueba por cada regla del issue #2.
 *
 * ### El patron que usa esta clase
 *
 * En vez de repetir los seis parametros en cada test, hay un helper
 * [registrar] con todos los valores por defecto validos. Cada test sobrescribe
 * unicamente el campo que quiere romper, por ejemplo:
 *
 *     registrar(firstName = "")   // solo el nombre esta mal, lo demas correcto
 *
 * Asi el test dice de un vistazo que esta probando, y si maniana se agrega un
 * septimo campo al registro no hay que tocar los once tests.
 */
class RegisterUseCaseTest {

    private val repositorio = FakeAuthRepository()
    private val register = RegisterUseCase(
        authRepository = repositorio,
        validateEmail = ValidateEmailUseCase(),
        validatePassword = ValidatePasswordUseCase(),
    )

    /** Registro completamente valido; cada test rompe solo lo que necesita. */
    private suspend fun registrar(
        firstName: String = "Ana",
        lastName: String = "Rojas",
        email: String = "ana@una.ac.cr",
        password: String = "codice2026",
        passwordConfirmation: String = "codice2026",
        role: UserRole? = UserRole.STUDENT,
    ) = register(firstName, lastName, email, password, passwordConfirmation, role)

    /**
     * Funcion de extension local para no repetir el doble casteo
     * ((this as Failure).error as Validation).field en cada test.
     */
    private fun DomainResult<*>.campoInvalido(): DomainError.Field =
        ((this as DomainResult.Failure).error as DomainError.Validation).field

    /** El camino feliz: ademas comprueba que el rol elegido llega a la sesion. */
    @Test
    fun `un registro valido crea la cuenta e inicia sesion con el rol elegido`() = runTest {
        val resultado = registrar(role = UserRole.TEACHER)

        val sesion = (resultado as DomainResult.Success).data
        assertEquals(UserRole.TEACHER, sesion.role)
        assertEquals(1, repositorio.registerCalls.size)
    }

    @Test
    fun `el nombre vacio se rechaza sin llamar al repositorio`() = runTest {
        val resultado = registrar(firstName = "")

        assertEquals(DomainError.Field.FIRST_NAME, resultado.campoInvalido())
        // Igual que en el login: si la validacion falla, no se gasta la llamada.
        assertTrue(repositorio.registerCalls.isEmpty())
    }

    /** Regla "sin espacios al inicio ni al final", en los dos campos de nombre. */
    @Test
    fun `un nombre con espacios al inicio o al final se rechaza`() = runTest {
        assertEquals(DomainError.Field.FIRST_NAME, registrar(firstName = " Ana").campoInvalido())
        assertEquals(DomainError.Field.LAST_NAME, registrar(lastName = "Rojas ").campoInvalido())
    }

    /** Caso aparte del vacio: aqui si escribio algo, aunque no sirva. */
    @Test
    fun `un apellido de solo espacios se rechaza`() = runTest {
        assertEquals(DomainError.Field.LAST_NAME, registrar(lastName = "   ").campoInvalido())
    }

    @Test
    fun `un correo invalido se rechaza sin llamar al repositorio`() = runTest {
        val resultado = registrar(email = "ana@una")

        assertEquals(DomainError.Field.EMAIL, resultado.campoInvalido())
        assertTrue(repositorio.registerCalls.isEmpty())
    }

    /** Delega en ValidatePasswordUseCase; esto verifica que este conectado. */
    @Test
    fun `una contrasenia debil se rechaza`() = runTest {
        val resultado = registrar(password = "abc", passwordConfirmation = "abc")

        assertEquals(DomainError.Field.PASSWORD, resultado.campoInvalido())
    }

    @Test
    fun `la confirmacion que no coincide se rechaza`() = runTest {
        val resultado = registrar(passwordConfirmation = "codice2027")

        assertEquals(DomainError.Field.PASSWORD_CONFIRMATION, resultado.campoInvalido())
    }

    /** El selector de rol del issue #5 arranca vacio: "no eligio" es invalido. */
    @Test
    fun `no elegir rol se rechaza`() = runTest {
        val resultado = registrar(role = null)

        assertEquals(DomainError.Field.ROLE, resultado.campoInvalido())
        assertTrue(repositorio.registerCalls.isEmpty())
    }

    /** El repositorio debe recibir el correo normalizado, igual que en login. */
    @Test
    fun `el correo llega normalizado al repositorio`() = runTest {
        registrar(email = "  Ana@UNA.ac.CR ")

        assertEquals("ana@una.ac.cr", repositorio.registerCalls.single().email)
    }

    /**
     * Criterio de aceptacion del issue #4, verificado ya desde el dominio:
     * un correo repetido devuelve un error de validacion normal y corriente,
     * NO una excepcion que haga crashear la app.
     */
    @Test
    fun `un correo repetido devuelve error de validacion y no una excepcion`() = runTest {
        registrar() // primera vez: se crea

        val resultado = registrar(firstName = "Luis") // mismo correo, otra persona

        assertEquals(DomainError.Field.EMAIL, resultado.campoInvalido())
    }

    /**
     * Fija la decision de devolver solo el primer fallo.
     *
     * Aqui el nombre Y el correo estan mal a la vez. Debe ganar el nombre, que
     * es el que va primero en el formulario. Si alguien reordena las
     * validaciones sin querer, este test lo detecta.
     */
    @Test
    fun `se devuelve solo el primer fallo aunque haya varios campos malos`() = runTest {
        val resultado = registrar(firstName = "", email = "roto")

        assertEquals(DomainError.Field.FIRST_NAME, resultado.campoInvalido())
    }
}
