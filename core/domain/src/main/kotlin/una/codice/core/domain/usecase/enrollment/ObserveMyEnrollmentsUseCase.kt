package una.codice.core.domain.usecase.enrollment

import kotlinx.coroutines.flow.Flow
import una.codice.core.domain.model.Enrollment
import una.codice.core.domain.repository.EnrollmentRepository

/**
 * Caso de Uso: Observa las matrículas del estudiante actual.
 * Proporciona un flujo (Flow) que se actualiza automáticamente cuando
 * el estado de alguna matrícula cambia en la base de datos local o remota.
 */
class ObserveMyEnrollmentsUseCase(
    private val enrollmentRepository: EnrollmentRepository,
) {
    operator fun invoke(): Flow<List<Enrollment>> {
        return enrollmentRepository.observeMyEnrollments()
    }
}
