package una.codice.feature.coverage.presentation

import una.codice.core.domain.model.CoverageMap

/**
 * Todo lo que la pantalla de Mapa de cobertura necesita para dibujarse —
 * mismo criterio que [una.codice.feature.auth.presentation.AuthUiState]: el
 * composable no guarda estado propio, todo sale de aquí.
 *
 * ### Por qué es un `sealed interface` y no un `data class` con banderas sueltas
 *
 * `convenciones.md` pide los tres estados mínimos (carga, vacío, error)
 * dibujados explícitamente. Con un `data class` (`isLoading: Boolean`,
 * `error: String?`, `map: CoverageMap?`) serían posibles combinaciones sin
 * sentido — cargando Y con error a la vez, por ejemplo — y cada composable
 * tendría que decidir a mano qué combinación gana. Con un `sealed interface`
 * el compilador exige un `when` exhaustivo y esas combinaciones imposibles
 * dejan de poder existir. Mismo patrón que ya usa `SessionState` en `:app`.
 */
sealed interface CoverageUiState {

    /** [una.codice.core.domain.usecase.coverage.GetCoverageMapUseCase] todavía no respondió. */
    data object Loading : CoverageUiState

    /**
     * El estudiante no tiene ningún módulo en su temario local todavía.
     *
     * No es un error: puede pasar simplemente porque el profesor aún no
     * publicó el temario, o porque la sincronización de curso (fuera del
     * alcance de este issue) todavía no corrió. Se distingue de [Error] a
     * propósito, para que la pantalla muestre un mensaje de "sin temario
     * publicado" y no un ícono de fallo.
     */
    data object Empty : CoverageUiState

    /** [una.codice.core.domain.error.DomainError.message], ya listo para pintarse. */
    data class Error(val message: String) : CoverageUiState

    /** El mapa de cobertura con al menos un módulo. */
    data class Content(val coverageMap: CoverageMap) : CoverageUiState
}
