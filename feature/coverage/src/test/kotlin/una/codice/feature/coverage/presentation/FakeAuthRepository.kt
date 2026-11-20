package una.codice.feature.coverage.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.NewUser
import una.codice.core.domain.model.Session
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.result.DomainResult

/**
 * Doble mínimo de [AuthRepository], solo para que [ObserveSessionUseCase]
 * tenga una sesión que emitir en los tests de [CoverageViewModel] — este
 * ViewModel no llama a `login`/`register`/`logout`, así que esos tres solo
 * devuelven lo mínimo para compilar.
 *
 * Copia local a propósito, mismo criterio que la de `:feature:auth`: no hay
 * `testFixtures` configurado en el proyecto (ver el KDoc de esa clase).
 */
class FakeAuthRepository(seedSession: Session? = null) : AuthRepository {

    private val session = MutableStateFlow(seedSession)

    override suspend fun login(email: String, password: String): DomainResult<Session> =
        DomainResult.Failure(DomainError.Unknown())

    override suspend fun register(newUser: NewUser): DomainResult<Session> =
        DomainResult.Failure(DomainError.Unknown())

    override fun currentSession(): Flow<Session?> = session

    override suspend fun logout(): DomainResult<Unit> {
        session.value = null
        return DomainResult.Success(Unit)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): DomainResult<Unit> =
        error("no se usa en este test")
}
