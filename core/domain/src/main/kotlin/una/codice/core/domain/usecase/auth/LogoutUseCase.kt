package una.codice.core.domain.usecase.auth

import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.result.DomainResult

/**
 * Cierra la sesión activa para que el usuario pueda volver a entrar con otra
 * cuenta.
 *
 * ### Por qué existe si solo reenvía la llamada al repositorio
 *
 * Mismo motivo que [ObserveSessionUseCase]: la pantalla de Perfil depende de
 * `LogoutUseCase`, no de [AuthRepository] directamente — mantiene la regla
 * de la arquitectura ("los features solo conocen casos de uso") y dejaría
 * espacio para que mañana cerrar sesión también, por ejemplo, invalide el
 * token en el servidor, sin tocar la pantalla que lo llama.
 *
 * ### Qué pasa después de invocarlo
 *
 * [AuthRepository.logout] pone la sesión persistida en `null`.
 * `MainViewModel.sessionState` ya observa esa sesión vía
 * [ObserveSessionUseCase], así que en cuanto emite `null` la app pasa sola a
 * `SessionState.LoggedOut`, y `CodiceNavHost` reconstruye el `NavHost`
 * arrancando en el login — nadie necesita navegar manualmente después de
 * llamar a este caso de uso.
 */
class LogoutUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): DomainResult<Unit> = authRepository.logout()
}
