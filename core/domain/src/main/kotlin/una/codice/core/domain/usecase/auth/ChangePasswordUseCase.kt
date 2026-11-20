package una.codice.core.domain.usecase.auth

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.result.validationFailure

/**
 * Cambiar la contraseña de la sesión iniciada. Valida lo que se puede saber sin red ANTES de llamar al servidor, para que
 * una contraseña débil o mal confirmada nunca llegue a él: la actual no está vacía, la nueva cumple las mismas reglas que
 * al registrarse ([ValidatePasswordUseCase]), coincide con su confirmación y es distinta de la actual.
 *
 * Que la contraseña actual sea la correcta solo lo sabe el servidor.
 */
class ChangePasswordUseCase(
    private val authRepository: AuthRepository,
    private val validatePassword: ValidatePasswordUseCase,
) {

    suspend operator fun invoke(
        currentPassword: String,
        newPassword: String,
        confirmation: String,
    ): DomainResult<Unit> {
        if (currentPassword.isEmpty()) {
            return validationFailure(DomainError.Field.PASSWORD, "Escribe tu contraseña actual")
        }
        when (val result = validatePassword(newPassword, confirmation)) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> Unit
        }
        if (newPassword == currentPassword) {
            return validationFailure(DomainError.Field.PASSWORD, "La contraseña nueva debe ser distinta de la actual")
        }
        return authRepository.changePassword(currentPassword, newPassword)
    }
}
