package una.codice.core.domain.model

/**
 * Representa un grupo académico al que un estudiante puede unirse.
 *
 * A diferencia del curso (que es el contenido académico), el grupo es la
 * instancia específica: tiene un número, un profesor asignado y un horario.
 * Un mismo curso puede tener múltiples grupos impartidos por distintos
 * profesores.
 */
data class StudentGroup(
    val id: String,
    val courseId: String,
    val courseName: String,
    val groupNumber: String,
    val period: String,
    val professorName: String,
    /** Código del curso para mostrar (p. ej. `EIF411`); [courseId] es el identificador. */
    val courseCode: String = "",
)
