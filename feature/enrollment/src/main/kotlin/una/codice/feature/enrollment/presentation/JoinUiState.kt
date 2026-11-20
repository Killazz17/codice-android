package una.codice.feature.enrollment.presentation

import una.codice.core.domain.model.Enrollment
import una.codice.core.domain.model.StudentGroup

/**
 * Estado de la pantalla de unirse a un curso.
 */
data class JoinUiState(
    // El código tal cual lo va escribiendo el usuario
    val code: String = "",
    // Si el código se validó con éxito, aquí se guarda la información del grupo encontrado
    val groupFound: StudentGroup? = null,
    // Lista de cursos actuales del alumno (con su estado)
    val myEnrollments: UiState<List<Enrollment>> = UiState.Loading,
    // Indica si hay una operación de red en curso (validación o envío)
    val isLoading: Boolean = false,
    // Mensaje de error para mostrar en el campo de texto si la validación falla
    val errorMessage: String? = null,
    // Indica si el escáner de QR está abierto
    val isScannerOpen: Boolean = false,
)
