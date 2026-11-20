package una.codice.feature.notes.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import una.codice.core.domain.model.NewUser
import una.codice.core.domain.model.Session
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.result.DomainResult

/**
 * Fake mínimo de [AuthRepository] para estos tests: `LibraryViewModel` solo
 * usa [ObserveSessionUseCase][una.codice.core.domain.usecase.auth.ObserveSessionUseCase]
 * para resolver el `studentId`, así que [login]/[register]/[logout] no
 * hacen falta y no se implementan de verdad.
 */
class FakeAuthRepository(seed: Session? = null) : AuthRepository {

    private val session = MutableStateFlow(seed)

    override suspend fun login(email: String, password: String): DomainResult<Session> =
        error("No lo usa LibraryViewModel")

    override suspend fun register(newUser: NewUser): DomainResult<Session> =
        error("No lo usa LibraryViewModel")

    override fun currentSession(): Flow<Session?> = session

    override suspend fun logout(): DomainResult<Unit> {
        session.value = null
        return DomainResult.Success(Unit)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): DomainResult<Unit> =
        error("no se usa en este test")
}
