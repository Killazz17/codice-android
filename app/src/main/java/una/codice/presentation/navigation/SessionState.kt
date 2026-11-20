package una.codice.presentation.navigation

import una.codice.core.domain.model.UserRole

/**
 * En qué estado está la sesión del usuario, tal como lo necesita
 * [CodiceNavHost] para decidir con qué pantalla arrancar la app.
 *
 * ### Por qué existen tres estados y no solo `Session?`
 *
 * [una.codice.core.domain.usecase.auth.ObserveSessionUseCase] ya expone
 * `Flow<Session?>`, y en principio `null` podría bastar para decir "no hay
 * sesión". El problema es que ese `Flow`, como cualquier `Flow` que lee de
 * DataStore, tarda un instante (así sea milisegundos) en emitir su primer
 * valor real — y durante ese instante, `null` sería indistinguible de "sí
 * revisé y de verdad no hay sesión". Sin un tercer estado, la app tendría
 * que asumir "no hay sesión" desde el primer instante y mostrar el login,
 * para luego —si sí había sesión— saltar fuera de golpe: exactamente el
 * parpadeo que el issue #5 pide evitar. [Loading] es lo que le da a
 * [CodiceNavHost] permiso para esperar, en vez de adivinar.
 */
sealed interface SessionState {
    /** `ObserveSessionUseCase` todavía no emitió su primer valor. */
    data object Loading : SessionState

    /** Ya se consultó, y no hay ninguna sesión guardada. */
    data object LoggedOut : SessionState

    /** Hay una sesión guardada, para este rol. */
    data class LoggedIn(val role: UserRole) : SessionState
}
