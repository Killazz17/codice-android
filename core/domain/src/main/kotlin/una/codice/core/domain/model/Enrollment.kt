package una.codice.core.domain.model

/**
 * El estado de la relación entre un estudiante y un grupo.
 *
 * El flujo típico es:
 * 1. El estudiante solicita unirse (estado [PENDING]).
 * 2. El profesor lo acepta (estado [APPROVED]) o lo rechaza (se borra).
 */
data class Enrollment(
    val id: String,
    val group: StudentGroup,
    val status: EnrollmentStatus,
    /** Porcentaje de cobertura del curso para este estudiante (ej: 0.61 para 61%). */
    val coverage: Float = 0f,
)

enum class EnrollmentStatus {
    PENDING,
    ACTIVE,
    REJECTED
}
