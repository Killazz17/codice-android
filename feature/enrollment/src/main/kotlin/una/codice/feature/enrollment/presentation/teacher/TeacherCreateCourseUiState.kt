package una.codice.feature.enrollment.presentation.teacher

/**
 * Formulario de "Crear curso" (D2): el curso y su primer grupo.
 *
 * @property fieldErrors Mensaje por campo vacío o inválido; se limpia al escribir en ese campo.
 * @property errorMessage Error del servidor (p. ej. código repetido) para mostrar arriba del botón.
 */
data class TeacherCreateCourseUiState(
    val code: String = "",
    val name: String = "",
    val groupNumber: String = "01",
    val period: String = "",
    val requiresApproval: Boolean = true,
    val fieldErrors: Map<CourseField, String> = emptyMap(),
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
)

enum class CourseField { CODE, NAME, GROUP_NUMBER, PERIOD }
