package una.codice.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import una.codice.core.domain.usecase.auth.ObserveSessionUseCase

/**
 * El único ViewModel de nivel de aplicación: decide con qué grafo debe
 * arrancar [CodiceNavHost], observando la sesión persistida.
 *
 * ### Por qué vive en `:app` y no en `:feature:auth`
 *
 * Decidir el punto de arranque de TODA la app es una responsabilidad que
 * cruza los dos grafos (estudiante y profesor) — ninguno de los dos features
 * debería saber que el otro existe, así que esa decisión no le corresponde a
 * ninguno en particular. `:app` es el único módulo que conoce ambos grafos
 * (la misma razón por la que el `NavHost` también vive aquí).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = observeSession()
        .map { session ->
            if (session == null) SessionState.LoggedOut else SessionState.LoggedIn(session.role)
        }
        .stateIn(
            scope = viewModelScope,
            // WhileSubscribed y no Eager ly: no tiene sentido seguir
            // observando DataStore si en algún momento nadie está mirando
            // sessionState (por ejemplo, la Activity en background).
            started = SharingStarted.WhileSubscribed(5_000),
            // Arranca en Loading a propósito: ver el KDoc de SessionState
            // para el porqué de este tercer estado.
            initialValue = SessionState.Loading,
        )
}
