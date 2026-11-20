package una.codice.core.domain.model

/**
 * Resumen de un grupo desde la perspectiva del profesor.
 *
 * A diferencia de [StudentGroup], que es lo que el alumno ve para unirse,
 * esta clase contiene las métricas agregadas (cobertura, cantidad de alumnos)
 * y el estado de gestión (solicitudes pendientes) que el profesor necesita
 * en su pantalla de inicio.
 */
data class TeacherGroupSummary(
    val group: StudentGroup,
    val activeStudentCount: Int,
    val averageCoverage: Float,
    val pendingRequestsCount: Int
)
