package una.codice.core.domain.usecase.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.result.DomainResult

class ChangePasswordUseCaseTest {

    private val repositorio = FakeAuthRepository()
    private val cambiar = ChangePasswordUseCase(repositorio, ValidatePasswordUseCase())

    private fun DomainResult<Unit>.campo() = ((this as DomainResult.Failure).error as DomainError.Validation).field

    @Test
    fun `una contrasena valida y distinta llega al repositorio`() = runTest {
        val resultado = cambiar("Vieja1234", "Nueva5678", "Nueva5678")

        assertTrue(resultado is DomainResult.Success)
        assertEquals(listOf("Vieja1234" to "Nueva5678"), repositorio.changePasswordCalls)
    }

    @Test
    fun `una contrasena nueva debil nunca llega al repositorio`() = runTest {
        val corta = cambiar("Vieja1234", "abc1", "abc1")
        val sinNumero = cambiar("Vieja1234", "soloLetras", "soloLetras")
        val sinLetra = cambiar("Vieja1234", "12345678", "12345678")

        assertEquals(DomainError.Field.PASSWORD, corta.campo())
        assertEquals(DomainError.Field.PASSWORD, sinNumero.campo())
        assertEquals(DomainError.Field.PASSWORD, sinLetra.campo())
        assertTrue(repositorio.changePasswordCalls.isEmpty())
    }

    @Test
    fun `si la confirmacion no coincide no se llama al repositorio`() = runTest {
        val resultado = cambiar("Vieja1234", "Nueva5678", "Nueva5679")

        assertEquals(DomainError.Field.PASSWORD_CONFIRMATION, resultado.campo())
        assertTrue(repositorio.changePasswordCalls.isEmpty())
    }

    @Test
    fun `la contrasena actual vacia no llega al repositorio`() = runTest {
        val resultado = cambiar("", "Nueva5678", "Nueva5678")

        assertEquals(DomainError.Field.PASSWORD, resultado.campo())
        assertTrue(repositorio.changePasswordCalls.isEmpty())
    }

    @Test
    fun `la nueva igual a la actual se rechaza sin llamar al repositorio`() = runTest {
        val resultado = cambiar("Vieja1234", "Vieja1234", "Vieja1234")

        assertEquals(DomainError.Field.PASSWORD, resultado.campo())
        assertTrue(repositorio.changePasswordCalls.isEmpty())
    }

    @Test
    fun `si el servidor rechaza la contrasena actual el error llega tal cual`() = runTest {
        val error = DomainError.Validation(DomainError.Field.PASSWORD, "La contraseña actual no es correcta")
        repositorio.changePasswordResult = DomainResult.Failure(error)

        val resultado = cambiar("Equivocada1", "Nueva5678", "Nueva5678")

        assertEquals(error, (resultado as DomainResult.Failure).error)
        assertEquals(1, repositorio.changePasswordCalls.size)
    }
}
