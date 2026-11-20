package una.codice.core.domain.usecase.enrollment

import una.codice.core.domain.repository.EnrollmentRepository
import una.codice.core.domain.result.DomainResult

/**
 * Caso de Uso: Solicita unirse a un curso usando un código de invitación.
 * Envía la petición al repositorio para que se registre la intención del alumno.
 */
class RequestJoinUseCase(
    private val enrollmentRepository: EnrollmentRepository,
) {
    /**
     * @param code El código de 14 caracteres del grupo (ej: EIF411-01-XXXX).
     * @param studentId El identificador único del alumno que solicita la unión.
     */
    suspend operator fun invoke(code: String, studentId: String): DomainResult<Unit> {
        return enrollmentRepository.requestJoin(code, studentId)
    }
}
