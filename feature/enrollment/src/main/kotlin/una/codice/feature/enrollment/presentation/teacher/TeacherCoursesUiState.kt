package una.codice.feature.enrollment.presentation.teacher

import una.codice.core.domain.model.TeacherGroupSummary

/**
 * Estado de la pantalla de inicio del profesor.
 *
 * @property dialog El diálogo abierto sobre un curso (agregar un grupo o confirmar que se elimina), o null.
 */
data class TeacherCoursesUiState(
    val isLoading: Boolean = true,
    val groups: List<TeacherGroupSummary> = emptyList(),
    val teacherName: String = "",
    val activeCoursesCount: Int = 0,
    val totalPendingRequests: Int = 0,
    val dialog: CourseDialog? = null,
)

/** Un diálogo sobre un curso del profesor. [error] es el mensaje del servidor y [isLoading] bloquea los botones. */
sealed interface CourseDialog {
    val courseId: String
    val courseCode: String
    val courseName: String
    val isLoading: Boolean
    val error: String?

    /** Formulario corto para agregar un grupo a un curso que ya existe, con el número y el periodo ya sugeridos. */
    data class AddGroup(
        override val courseId: String,
        override val courseCode: String,
        override val courseName: String,
        val groupNumber: String,
        val period: String,
        val requiresApproval: Boolean = true,
        override val isLoading: Boolean = false,
        override val error: String? = null,
    ) : CourseDialog

    /**
     * Confirmación de eliminar el curso. Con [activeStudents] mayores a cero no se ofrece borrar: el backend lo
     * rechazaría y es mejor decirlo antes que fallar después.
     */
    data class ConfirmDelete(
        override val courseId: String,
        override val courseCode: String,
        override val courseName: String,
        val activeStudents: Int,
        override val isLoading: Boolean = false,
        override val error: String? = null,
    ) : CourseDialog
}
