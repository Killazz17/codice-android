package una.codice.core.domain.model

/**
 * Representa un estudiante dentro de un grupo desde la vista del profesor.
 */
data class GroupStudent(
    val user: User,
    val enrollmentId: String,
    val status: EnrollmentStatus,
    /**
     * Cobertura del estudiante, de 0 a 1. `null` cuando no se conoce: la cobertura que ve el profesor
     * es agregada y anónima, así que el backend no la entrega por estudiante y la lista no la muestra.
     */
    val coverage: Float? = null,
    val requestDate: String? = null // Ej: "hace 2 h", "ayer"
)
