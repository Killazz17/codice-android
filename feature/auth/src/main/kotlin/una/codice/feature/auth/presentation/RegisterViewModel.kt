package una.codice.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.auth.RegisterUseCase

/**
 * Orquesta la pantalla de registro.
 *
 * A diferencia de [AuthViewModel] (que decidía solo entre login y registro
 * antes de la separación en dos pantallas), este ViewModel es directo: llama
 * a [RegisterUseCase] una sola vez con lo que haya en el formulario. Todas
 * las reglas de validación (nombre, apellido, correo, contraseña,
 * confirmación, rol) ya viven en el caso de uso desde el issue #2 — repetirlas
 * aquí duplicaría la regla en dos sitios que podrían desincronizarse.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    // Mismo criterio que AuthViewModel.navigateAsRole: un evento de una sola
    // vez, no un estado que deba re-emitirse.
    private val _navigateAsRole = Channel<UserRole>(capacity = Channel.CONFLATED)
    val navigateAsRole = _navigateAsRole.receiveAsFlow()

    fun onEmailChanged(value: String) {
        _state.value = _state.value.copy(email = value, fieldErrors = clearError(DomainError.Field.EMAIL))
    }

    fun onFullNameChanged(value: String) {
        _state.value = _state.value.copy(fullName = value, fieldErrors = clearError(DomainError.Field.FIRST_NAME))
    }

    fun onPasswordChanged(value: String) {
        _state.value = _state.value.copy(password = value, fieldErrors = clearError(DomainError.Field.PASSWORD))
    }

    fun onPasswordConfirmationChanged(value: String) {
        _state.value = _state.value.copy(
            passwordConfirmation = value,
            fieldErrors = clearError(DomainError.Field.PASSWORD_CONFIRMATION),
        )
    }

    fun onRoleSelected(role: UserRole) {
        _state.value = _state.value.copy(role = role, fieldErrors = clearError(DomainError.Field.ROLE))
    }

    fun onTogglePasswordVisibility() {
        _state.value = _state.value.copy(isPasswordVisible = !_state.value.isPasswordVisible)
    }

    /** Quita el error de un campo específico sin tocar los demás — se llama cada vez que el usuario vuelve a escribir en ese campo. */
    private fun clearError(field: DomainError.Field): Map<DomainError.Field, String> =
        _state.value.fieldErrors - field

    fun onSubmit() {
        val current = _state.value
        if (current.isLoading) return // evita doble envio si el usuario toca el boton dos veces

        _state.value = current.copy(isLoading = true, fieldErrors = emptyMap())
        val (firstName, lastName) = splitFullName(current.fullName)

        viewModelScope.launch {
            val result = registerUseCase(
                firstName = firstName,
                lastName = lastName,
                email = current.email,
                password = current.password,
                passwordConfirmation = current.passwordConfirmation,
                role = current.role,
            )
            _state.value = _state.value.copy(isLoading = false)

            when (result) {
                is DomainResult.Success -> _navigateAsRole.trySend(result.data.role)
                is DomainResult.Failure -> showError(result.error)
            }
        }
    }

    /** Divide "nombre completo" en la primera palabra y el resto, recortando espacios sobrantes. */
    private fun splitFullName(fullName: String): Pair<String, String> {
        val trimmed = fullName.trim()
        val spaceIndex = trimmed.indexOf(' ')
        return if (spaceIndex == -1) {
            trimmed to ""
        } else {
            trimmed.substring(0, spaceIndex) to trimmed.substring(spaceIndex + 1).trim()
        }
    }

    /** Vuelca un [DomainError] al mapa de errores por campo del estado. */
    private fun showError(error: DomainError) {
        val field = when (error) {
            is DomainError.Validation -> {
                // FIRST_NAME y LAST_NAME comparten el único campo visual de
                // nombre completo; se normalizan a la misma llave.
                if (error.field == DomainError.Field.LAST_NAME) DomainError.Field.FIRST_NAME else error.field
            }
            // Sin campo propio en el dominio: se muestran junto a la
            // contraseña, el campo más cercano al botón de envío.
            else -> DomainError.Field.PASSWORD
        }
        _state.value = _state.value.copy(fieldErrors = _state.value.fieldErrors + (field to error.message))
    }
}
