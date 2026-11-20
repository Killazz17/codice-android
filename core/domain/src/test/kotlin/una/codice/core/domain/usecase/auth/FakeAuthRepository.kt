package una.codice.core.domain.usecase.auth

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.NewUser
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.result.DomainResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Implementación falsa de [AuthRepository] para los tests: guarda las cuentas
 * en un mapa en memoria, sin red ni base de datos.
 *
 * ### Por qué está escrita a mano y no con MockK
 *
 * Un mock generado sabe *responder* lo que le digas. Este fake, además,
 * **registra lo que recibió** en [loginCalls] y [registerCalls], y eso es lo
 * que permite verificar el criterio de aceptación más importante del issue #2:
 *
 * > "Un correo inválido nunca llega al repositorio"
 *
 * Para probar eso no basta con comprobar que el resultado sea un error: hay que
 * demostrar que **la llamada ni siquiera se hizo**. Sin la lista de llamadas,
 * un `LoginUseCase` que preguntara al servidor y luego descartara la respuesta
 * pasaría el test igual, aunque estuviera gastando red y filtrando el correo.
 *
 * Un fake escrito a mano también documenta el contrato: leyéndolo se entiende
 * qué se espera que haga el repositorio real del issue #4.
 *
 * @param accounts cuentas precargadas, en formato `correo -> contraseña`.
 */
class FakeAuthRepository(
    private val accounts: MutableMap<String, String> = mutableMapOf(),
) : AuthRepository {

    /**
     * `MutableStateFlow` es un Flow que además guarda el último valor emitido y
     * lo entrega de inmediato a quien se suscriba. Es justo lo que necesita una
     * sesión: quien pregunte quiere saber el estado actual, no esperar al
     * próximo cambio.
     */
    private val session = MutableStateFlow<Session?>(null)

    /** Cada par (correo, contraseña) con el que se invocó a [login]. */
    val loginCalls = mutableListOf<Pair<String, String>>()

    /** Cada [NewUser] con el que se invocó a [register]. */
    val registerCalls = mutableListOf<NewUser>()

    override suspend fun login(email: String, password: String): DomainResult<Session> {
        // Se registra ANTES de cualquier comprobación: queremos saber que la
        // llamada ocurrió, haya salido bien o mal.
        loginCalls += email to password

        return if (accounts[email] == password) {
            val nueva = Session(token = "token-$email", userId = "id-$email", role = UserRole.STUDENT)
            session.value = nueva
            DomainResult.Success(nueva)
        } else {
            DomainResult.Failure(DomainError.InvalidCredentials())
        }
    }

    override suspend fun register(newUser: NewUser): DomainResult<Session> {
        registerCalls += newUser

        // Correo repetido -> error de VALIDACIÓN, no una excepción.
        // Así lo exige el issue #4, y se prueba ya desde aquí.
        if (accounts.containsKey(newUser.email)) {
            return DomainResult.Failure(
                DomainError.Validation(DomainError.Field.EMAIL, "Ese correo ya está registrado"),
            )
        }

        accounts[newUser.email] = newUser.password
        // Registrarse deja la sesión iniciada: el usuario no tiene que escribir
        // sus credenciales otra vez inmediatamente después de crearlas.
        val nueva = Session(
            token = "token-${newUser.email}",
            userId = "id-${newUser.email}",
            role = newUser.role,
        )
        session.value = nueva
        return DomainResult.Success(nueva)
    }

    override fun currentSession(): Flow<Session?> = session

    override suspend fun logout(): DomainResult<Unit> {
        session.value = null
        return DomainResult.Success(Unit)
    }

    /** Cada par (actual, nueva) con el que se invocó a [changePassword]. */
    val changePasswordCalls = mutableListOf<Pair<String, String>>()

    /** Lo que responde [changePassword]; por defecto, éxito. */
    var changePasswordResult: DomainResult<Unit> = DomainResult.Success(Unit)

    override suspend fun changePassword(currentPassword: String, newPassword: String): DomainResult<Unit> {
        changePasswordCalls += currentPassword to newPassword
        return changePasswordResult
    }

    /**
     * Coloca una sesión ya iniciada sin pasar por [login].
     *
     * Sirve para simular "el usuario abre la app y ya había iniciado sesión
     * ayer", que es el escenario que prueba [ObserveSessionUseCaseTest].
     */
    fun seedSession(value: Session?) {
        session.value = value
    }
}
