package una.codice.core.domain.usecase.auth

import una.codice.core.domain.model.Session
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.result.DomainResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoutUseCaseTest {

    private val repositorio = FakeAuthRepository()
    private val logout = LogoutUseCase(repositorio)
    private val observeSession = ObserveSessionUseCase(repositorio)

    // Es lo que permite entrar con otra cuenta después: la sesión guardada
    // desaparece, y ObserveSessionUseCase (del que depende MainViewModel)
    // lo nota solo, sin que nadie navegue manualmente.
    @Test
    fun `cerrar sesion deja la sesion en null`() = runTest {
        repositorio.seedSession(Session(token = "t", userId = "u", role = UserRole.STUDENT))

        logout()

        assertNull(observeSession().first())
    }

    @Test
    fun `cerrar sesion devuelve exito`() = runTest {
        repositorio.seedSession(Session(token = "t", userId = "u", role = UserRole.STUDENT))

        val resultado = logout()

        assertTrue(resultado is DomainResult.Success)
    }
}
