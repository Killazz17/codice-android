package una.codice.core.domain.usecase.auth

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Session
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.result.validationFailure

/**
 * Inicia sesión: **valida primero, llama al repositorio después**.
 *
 * ### Por qué la validación vive aquí y no en la pantalla
 *
 * El issue #2 lo pide explícitamente: *"un correo inválido nunca llega al
 * repositorio"*. Si la comprobación estuviera en el composable o en el
 * ViewModel, cualquier otra entrada al login —un deep link, un re-login
 * automático tras expirar el token, una pantalla nueva que alguien agregue el
 * próximo mes— tendría que acordarse de copiar la misma validación. Estando
 * aquí, todas la heredan gratis.
 *
 * Es la razón de ser de la capa de dominio: **la regla vive en un solo sitio**.
 *
 * @param authRepository de dónde salen los datos. En producción será la
 *   implementación real; en los tests, un `FakeAuthRepository`.
 * @param validateEmail se **inyecta** en vez de crearse dentro
 *   (`ValidateEmailUseCase()`) para que sea la misma instancia que usa el
 *   ViewModel al validar mientras se escribe. Así es imposible que la regla del
 *   formulario y la del envío se separen.
 */
class LoginUseCase(
    private val authRepository: AuthRepository,
    private val validateEmail: ValidateEmailUseCase,
) {

    suspend operator fun invoke(email: String, password: String): DomainResult<Session> {
        // `when` sobre un sealed type usado como EXPRESIÓN: devuelve un valor.
        // Si la validación falló, salimos aquí mismo con ese error (nótese que
        // se puede devolver `result` tal cual gracias a que Failure es
        // DomainResult<Nothing>; ver la explicación en DomainResult.kt).
        // Si salió bien, nos quedamos con el correo ya normalizado.
        val normalizedEmail = when (val result = validateEmail(email)) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> result.data
        }

        // Al INICIAR SESIÓN no se aplican las reglas de fortaleza (8
        // caracteres, letra y número). Es intencional, por dos motivos:
        //
        //  1. Esas son reglas de CREACIÓN de cuenta. Si mañana la política se
        //     endurece, todas las cuentas viejas seguirían siendo válidas en el
        //     servidor pero el cliente las rechazaría antes de intentarlo, y el
        //     usuario quedaría encerrado fuera de su propia cuenta.
        //  2. Rechazar en el cliente por "muy corta" le revela la política de
        //     contraseñas a quien esté intentando adivinar una.
        //
        // Solo se comprueba que no venga vacía, para no gastar una llamada de
        // red que sabemos de antemano que va a fallar.
        if (password.isEmpty()) {
            return validationFailure(DomainError.Field.PASSWORD, "La contraseña es obligatoria")
        }

        return authRepository.login(normalizedEmail, password)
    }
}
