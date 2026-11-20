package una.codice.core.domain.usecase.auth

import una.codice.core.domain.model.Session
import una.codice.core.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observa la sesión guardada en el dispositivo.
 *
 * Es la pieza que hace posibles dos comportamientos que el usuario da por
 * sentados:
 *
 * - Al abrir la app con sesión iniciada, entra directo a su grafo de navegación
 *   sin volver a pedir la contraseña.
 * - Al cerrar sesión (o si el token expira), la app **vuelve sola** al login.
 *
 * La pantalla de arranque se suscribe a este [Flow] y reacciona a lo que emita,
 * en vez de preguntar "¿hay sesión?" una única vez al inicio.
 *
 * ### Por qué existe si solo reenvía la llamada al repositorio
 *
 * Es una pregunta justa: este caso de uso no tiene lógica propia. Su valor no
 * está en lo que hace, sino en **la dirección de la dependencia que impone**.
 *
 * Gracias a él, el ViewModel de `:feature:auth` depende de
 * `ObserveSessionUseCase` y no de [AuthRepository]. Eso mantiene la regla de la
 * arquitectura ("los features solo conocen casos de uso") y deja la puerta
 * abierta a que mañana esto crezca —por ejemplo, comprobar si el token está
 * vencido antes de emitirlo— sin tocar ni una línea del ViewModel.
 *
 * Es el mismo criterio que se aplica a los demás casos de uso: consistencia por
 * encima de ahorrarse una clase.
 */
class ObserveSessionUseCase(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): Flow<Session?> = authRepository.currentSession()
}
