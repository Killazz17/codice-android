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
import una.codice.core.domain.usecase.auth.LoginUseCase

/**
 * Orquesta la pantalla de login.
 *
 * ### Por qué ya no reintenta como registro
 *
 * Hasta la separación de login y registro en dos pantallas, este ViewModel
 * intentaba [LoginUseCase] y, si fallaba por credenciales inválidas,
 * reintentaba crear la cuenta con los mismos datos — la pantalla de acceso de
 * Figma no tenía un selector "Iniciar sesión" / "Registrarse", así que el
 * botón "Entrar" decidía solo qué hacer.
 *
 * Esa solución tenía un costo real: como no había un segundo campo de
 * contraseña, no existía forma de detectar un typo al crear la cuenta (se
 * mandaba el mismo valor dos veces como contraseña y como confirmación). Con
 * `RegisterScreen`/`RegisterViewModel` aparte, ese problema se resuelve de
 * raíz — y el login vuelve a ser exactamente lo que su nombre dice: solo
 * inicia sesión. Si la cuenta no existe, [LoginUseCase] devuelve
 * [DomainError.InvalidCredentials] y este ViewModel simplemente lo muestra;
 * crear la cuenta ahora es responsabilidad exclusiva de `RegisterViewModel`.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    // Channel y no StateFlow: navegar es un evento de una sola vez ("andá a la
    // pantalla de estudiante"), no un estado que deba re-emitirse si alguien
    // vuelve a suscribirse (por ejemplo, tras un cambio de configuración).
    private val _navigateAsRole = Channel<UserRole>(capacity = Channel.CONFLATED)
    val navigateAsRole = _navigateAsRole.receiveAsFlow()

    fun onEmailChanged(value: String) {
        _state.value = _state.value.copy(
            email = value,
            fieldErrors = clearError(DomainError.Field.EMAIL),
            generalError = null,
        )
    }

    fun onPasswordChanged(value: String) {
        _state.value = _state.value.copy(
            password = value,
            fieldErrors = clearError(DomainError.Field.PASSWORD),
            generalError = null,
        )
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

        _state.value = current.copy(isLoading = true, fieldErrors = emptyMap(), generalError = null)

        viewModelScope.launch {
            when (val result = loginUseCase(current.email, current.password)) {
                is DomainResult.Success -> {
                    _state.value = _state.value.copy(isLoading = false)
                    _navigateAsRole.trySend(result.data.role)
                }
                is DomainResult.Failure -> {
                    _state.value = _state.value.copy(isLoading = false)
                    showError(result.error)
                }
            }
        }
    }

    /**
     * Vuelca un [DomainError] al estado: [DomainError.Validation] va al mapa
     * de errores por campo (sí tiene un campo propio del dominio); todo lo
     * demás (credenciales inválidas, red caída, error desconocido) va a
     * [AuthUiState.generalError].
     *
     * Hallazgo H9.2 de la evaluación heurística: antes, los errores sin campo
     * propio se forzaban sobre `Field.PASSWORD`, sugiriendo que el problema
     * era la contraseña aunque en realidad pudiera ser el correo. Un banner
     * general no apunta a ningún campo en particular.
     */
    private fun showError(error: DomainError) {
        if (error is DomainError.Validation) {
            _state.value = _state.value.copy(
                fieldErrors = _state.value.fieldErrors + (error.field to error.message),
            )
        } else {
            _state.value = _state.value.copy(generalError = error.message)
        }
    }
}
