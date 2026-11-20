package una.codice.feature.auth.presentation

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.UserRole

/**
 * Todo lo que la pantalla de registro necesita para dibujarse en cualquier
 * momento — el `RegisterScreen` composable no guarda ningún estado propio,
 * todo sale de aquí. Mismo criterio que [AuthUiState] para el login.
 *
 * ### Por qué hay `fullName` y no `firstName`/`lastName` por separado
 *
 * El diseño solo tiene un campo de nombre. `RegisterUseCase` sí exige
 * `firstName` y `lastName` por separado (ya validados desde el issue #2), así
 * que `RegisterViewModel` divide este único campo en dos justo antes de
 * llamar al caso de uso — la pantalla nunca necesita saber que esa división
 * existe.
 *
 * ### Por qué `passwordConfirmation` es un campo real (y no lo era en el login mezclado)
 *
 * Antes de separar login y registro, la pantalla única no tenía un segundo
 * campo de contraseña, así que se mandaba el mismo valor dos veces como
 * `password` y `passwordConfirmation` — no había forma de detectar un typo.
 * Ahora que el registro tiene su propia pantalla, sí tiene sentido pedirlo,
 * y `RegisterUseCase.PASSWORD_CONFIRMATION` (que ya existía desde el issue #2
 * pero nunca podía dispararse) por fin es alcanzable.
 */
data class RegisterUiState(
    val email: String = "",
    val fullName: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val role: UserRole? = null,
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,

    /**
     * Un mensaje de error por campo, listo para mostrar debajo de ese campo
     * en coral.
     *
     * Como la pantalla solo tiene un campo de nombre (no uno de nombre y otro
     * de apellido), tanto [DomainError.Field.FIRST_NAME] como
     * [DomainError.Field.LAST_NAME] del dominio se normalizan a la misma
     * llave [DomainError.Field.FIRST_NAME] al construir este mapa — misma
     * convención que ya usaba el login mezclado.
     *
     * Los errores sin campo propio del dominio (red caída, error
     * desconocido) se guardan bajo [DomainError.Field.PASSWORD]: es el campo
     * más cercano al botón de envío.
     */
    val fieldErrors: Map<DomainError.Field, String> = emptyMap(),
)
