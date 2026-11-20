package una.codice.feature.auth.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.NewUser
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.result.DomainResult

/**
 * Implementación falsa de [AuthRepository] para probar [AuthViewModel].
 *
 * Es una copia pequeña y local del mismo patrón que ya usa
 * `core/domain/src/test/.../FakeAuthRepository.kt` — no se comparte entre
 * módulos a propósito (no existe un mecanismo de `testFixtures` configurado
 * en este proyecto, y duplicar 30 líneas es más simple que agregar esa
 * infraestructura solo para esto).
 *
 * A diferencia del fake de `:core:domain`, este además puede simular una
 * cuenta ya existente cuya contraseña no coincide — es justo el escenario
 * ambiguo que [AuthViewModel] necesita distinguir (ver su KDoc).
 */
class FakeAuthRepository(
    private val accounts: MutableMap<String, String> = mutableMapOf(),
) : AuthRepository {

    private val session = MutableStateFlow<Session?>(null)

    override suspend fun login(email: String, password: String): DomainResult<Session> {
        return if (accounts[email] == password) {
            val nueva = Session(token = "token-$email", userId = "id-$email", role = UserRole.STUDENT)
            session.value = nueva
            DomainResult.Success(nueva)
        } else {
            DomainResult.Failure(DomainError.InvalidCredentials())
        }
    }

    override suspend fun register(newUser: NewUser): DomainResult<Session> {
        if (accounts.containsKey(newUser.email)) {
            return DomainResult.Failure(
                DomainError.Validation(DomainError.Field.EMAIL, "Ese correo ya está registrado"),
            )
        }
        accounts[newUser.email] = newUser.password
        val nueva = Session(token = "token-${newUser.email}", userId = "id-${newUser.email}", role = newUser.role)
        session.value = nueva
        return DomainResult.Success(nueva)
    }

    override fun currentSession(): Flow<Session?> = session

    override suspend fun logout(): DomainResult<Unit> {
        session.value = null
        return DomainResult.Success(Unit)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): DomainResult<Unit> =
        error("no se usa en este test")
}
