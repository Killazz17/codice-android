package una.codice.core.domain.usecase.enrollment

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import una.codice.core.domain.model.TeacherGroupSummary
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.repository.EnrollmentRepository

/**
 * Proporciona el flujo de datos para la pantalla de "Mis cursos" del profesor.
 *
 * Se encarga de identificar al profesor actual a través de la sesión y
 * delegar al repositorio la observación de sus grupos. Si no hay sesión,
 * devuelve una lista vacía.
 */
class ObserveTeacherGroupsUseCase(
    private val authRepository: AuthRepository,
    private val enrollmentRepository: EnrollmentRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<TeacherGroupSummary>> {
        return authRepository.currentSession().flatMapLatest { session ->
            val teacherId = session?.userId
            if (teacherId != null) {
                enrollmentRepository.observeTeacherGroups(teacherId)
            } else {
                flowOf(emptyList())
            }
        }
    }
}
