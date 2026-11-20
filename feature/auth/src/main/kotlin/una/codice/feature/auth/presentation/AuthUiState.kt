package una.codice.feature.auth.presentation

import una.codice.core.domain.error.DomainError

/**
 * Todo lo que la pantalla de login necesita para dibujarse en cualquier
 * momento — el `AuthScreen` composable no guarda ningún estado propio, todo
 * sale de aquí.
 *
 * ### Por qué no hay `fullName` ni `role`
 *
 * Hasta antes de separar login y registro en dos pantallas, esta misma clase
 * traía ambos campos porque el botón "Entrar" podía terminar creando una
 * cuenta nueva (ver el historial de [AuthViewModel]). Ahora que existe
 * `RegisterScreen`/`RegisterUiState` aparte, el login vuelve a ser solo login:
 * ni el nombre ni el rol tienen sentido aquí, porque ninguno de los dos hace
 * falta para iniciar sesión en una cuenta que ya existe.
 */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,

    /**
     * Un mensaje de error por campo, listo para mostrar debajo de ese campo
     * en coral. Solo lo llena [DomainError.Validation] — es el único caso que
     * de verdad señala un campo concreto del formulario.
     */
    val fieldErrors: Map<DomainError.Field, String> = emptyMap(),

    /**
     * Mensaje de error que no señala un campo específico —
     * [DomainError.InvalidCredentials], [DomainError.Network],
     * [DomainError.Unauthorized] y [DomainError.Unknown].
     *
     * ### Por qué ya no se guarda bajo `Field.PASSWORD`
     *
     * Hallazgo H9.2 de la evaluación heurística
     * (`docs/usabilidad/informes/sprint-piloto-2026-08-26.md`): un correo
     * inválido y una contraseña incorrecta producen el mismo
     * `InvalidCredentials`, pero atribuirlo siempre al campo contraseña le
     * sugería al usuario que revisara solo esa caja, aunque el problema real
     * pudiera estar en el correo. Un banner general no apunta a ningún campo
     * en particular — ni insinúa cuál de los dos está mal.
     */
    val generalError: String? = null,
)
