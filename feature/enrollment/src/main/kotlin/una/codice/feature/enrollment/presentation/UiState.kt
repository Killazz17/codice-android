package una.codice.feature.enrollment.presentation

/**
 * Representa el estado de una carga de datos asíncrona.
 * Facilita el manejo de estados de carga, éxito y error en la UI.
 */
sealed interface UiState<out T> {
    // Los datos se están cargando
    object Loading : UiState<Nothing>
    // Los datos se cargaron correctamente
    data class Success<T>(val data: T) : UiState<T>
    // Ocurrió un error durante la carga
    data class Error(val message: String) : UiState<Nothing>
}
