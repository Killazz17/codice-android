package una.codice.core.domain.usecase.enrollment

import una.codice.core.domain.model.StudentGroup
import una.codice.core.domain.repository.EnrollmentRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.result.validationFailure
import una.codice.core.domain.error.DomainError

/**
 * Caso de Uso: Traduce un código de invitación al grupo correspondiente.
 * 
 * Antes de consultar al repositorio, valida que el código cumpla con el 
 * formato oficial (EIF411-01-7K2A).
 */
class ResolveInviteCodeUseCase(
    private val enrollmentRepository: EnrollmentRepository,
) {
    // CURSO - GRUPO - CLAVE: el código del curso va de 5 a 10 caracteres (lo que acepta el servidor), el grupo lleva 2 y la clave 4.
    private val codeRegex = Regex("^[A-Z0-9]{5,10}-[A-Z0-9]{2}-[A-Z0-9]{4}$")

    /**
     * @param code El código ingresado por el usuario.
     * @return El grupo encontrado o un error de validación/búsqueda.
     */
    suspend operator fun invoke(code: String): DomainResult<StudentGroup> {
        val normalizedCode = code.trim().uppercase()

        // Validación local de formato para ahorrar peticiones innecesarias al servidor
        if (!codeRegex.matches(normalizedCode)) {
            return validationFailure(
                DomainError.Field.CODE,
                "El código debe tener el formato CURSO-00-XXXX (por ejemplo, EQP101-01-V63H)"
            )
        }

        return enrollmentRepository.resolveInviteCode(normalizedCode)
    }
}
