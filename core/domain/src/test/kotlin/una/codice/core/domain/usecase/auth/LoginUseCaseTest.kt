package una.codice.core.domain.usecase.auth

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.result.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [LoginUseCase].
 *
 * ### Qué hace `runTest { }`
 *
 * `invoke` de LoginUseCase es una función `suspend`, y las funciones suspend
 * solo pueden llamarse desde una corrutina. `runTest` crea esa corrutina y
 * además adelanta el tiempo virtualmente, así que un `delay(5000)` no haría
 * esperar cinco segundos de verdad. Viene de `kotlinx-coroutines-test`.
 */
class LoginUseCaseTest {

    // Una cuenta precargada: es la que "existe" para estos tests.
    // Es `var`-friendly (mutableMap) porque un test le agrega otra cuenta.
    private val cuentas = mutableMapOf("ana@una.ac.cr" to "codice2026")
    private val repositorio = FakeAuthRepository(cuentas)
    private val login = LoginUseCase(repositorio, ValidateEmailUseCase())

    @Test
    fun `credenciales correctas devuelven sesion`() = runTest {
        val resultado = login("ana@una.ac.cr", "codice2026")

        val sesion = (resultado as DomainResult.Success).data
        assertEquals("id-ana@una.ac.cr", sesion.userId)
        assertEquals("token-ana@una.ac.cr", sesion.token)
    }

    @Test
    fun `credenciales incorrectas devuelven error`() = runTest {
        val resultado = login("ana@una.ac.cr", "otraCosa9")

        // Importa que sea InvalidCredentials y no Validation: la contraseña
        // tiene formato correcto, simplemente no es la de esa cuenta.
        assertTrue((resultado as DomainResult.Failure).error is DomainError.InvalidCredentials)
    }

    /**
     * **El test más importante del issue #2.**
     *
     * El criterio de aceptación dice literalmente "un correo inválido nunca
     * llega al repositorio". Por eso no basta con verificar que devuelva error:
     * hay que comprobar que `loginCalls` esté VACÍA, es decir, que la petición
     * ni siquiera se intentó.
     */
    @Test
    fun `un correo invalido nunca llega al repositorio`() = runTest {
        val resultado = login("ana-sin-arroba", "codice2026")

        assertTrue(repositorio.loginCalls.isEmpty())
        val validacion = (resultado as DomainResult.Failure).error as DomainError.Validation
        assertEquals(DomainError.Field.EMAIL, validacion.field)
    }

    /** Mismo principio: no gastar una llamada de red que sabemos que fallará. */
    @Test
    fun `una contrasenia vacia no llega al repositorio`() = runTest {
        val resultado = login("ana@una.ac.cr", "")

        assertTrue(repositorio.loginCalls.isEmpty())
        val validacion = (resultado as DomainResult.Failure).error as DomainError.Validation
        assertEquals(DomainError.Field.PASSWORD, validacion.field)
    }

    /**
     * Verifica que lo que llega al repositorio es el correo YA normalizado, no
     * el que escribió el usuario. Sin esto, el servidor recibiría variantes
     * distintas de la misma cuenta.
     */
    @Test
    fun `el correo llega normalizado al repositorio`() = runTest {
        login("  Ana@UNA.ac.CR  ", "codice2026")

        assertEquals(listOf("ana@una.ac.cr" to "codice2026"), repositorio.loginCalls)
    }

    /**
     * Fija por escrito la decisión de NO aplicar las reglas de fortaleza al
     * iniciar sesión (ver el comentario largo en LoginUseCase).
     *
     * "1234" no cumple ninguna regla del registro, pero si esa cuenta existe en
     * el servidor debe poder entrar. Si alguien añade la validación de
     * fortaleza al login, este test falla y explica por qué no debe hacerse.
     */
    @Test
    fun `al iniciar sesion no se aplican las reglas de fortaleza`() = runTest {
        cuentas["luis@una.ac.cr"] = "1234"

        val resultado = login("luis@una.ac.cr", "1234")

        assertTrue(resultado is DomainResult.Success)
        assertEquals(listOf("luis@una.ac.cr" to "1234"), repositorio.loginCalls)
    }
}
