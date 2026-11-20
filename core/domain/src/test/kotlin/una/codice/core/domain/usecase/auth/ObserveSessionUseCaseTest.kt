package una.codice.core.domain.usecase.auth

import una.codice.core.domain.model.Session
import una.codice.core.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de [ObserveSessionUseCase].
 *
 * `.first()` toma el primer valor que emite el Flow y se desuscribe. Como el
 * fake usa un `MutableStateFlow` (que siempre tiene un valor actual), eso
 * equivale a "dame el estado de la sesion en este momento".
 */
class ObserveSessionUseCaseTest {

    private val repositorio = FakeAuthRepository()
    private val observeSession = ObserveSessionUseCase(repositorio)

    /** App recien instalada: la pantalla de arranque debe mandar al login. */
    @Test
    fun `sin sesion guardada emite null`() = runTest {
        assertNull(observeSession().first())
    }

    /**
     * El escenario de "cerre la app ayer y la abro hoy": debe entrar directo,
     * y el rol es lo que decide a cual de los dos grafos de navegacion va.
     */
    @Test
    fun `con sesion guardada emite el rol para elegir el grafo de navegacion`() = runTest {
        repositorio.seedSession(
            Session(token = "t", userId = "u", role = UserRole.TEACHER),
        )

        assertEquals(UserRole.TEACHER, observeSession().first()?.role)
    }

    /**
     * Esto es lo que hace que la app vuelva SOLA al login al cerrar sesion:
     * nadie navega manualmente, simplemente el Flow emite null y la pantalla
     * que esta suscrita reacciona.
     */
    @Test
    fun `cerrar sesion vuelve a emitir null`() = runTest {
        repositorio.seedSession(Session(token = "t", userId = "u", role = UserRole.STUDENT))

        repositorio.logout()

        assertNull(observeSession().first())
    }
}
