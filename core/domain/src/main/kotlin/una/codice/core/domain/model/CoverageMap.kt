package una.codice.core.domain.model

/**
 * El mapa de cobertura completo de un estudiante: todos sus módulos, cada
 * uno con sus subtemas y el porcentaje ya calculado.
 *
 * Es lo que devuelve
 * [GetCoverageMapUseCase][una.codice.core.domain.usecase.coverage.GetCoverageMapUseCase]
 * y lo que expondrá `CoverageViewModel` (issue #22) en su `UiState`.
 *
 * @property modules Ordenados por [ModuleCoverage.moduleNumber] ascendente.
 */
data class CoverageMap(
    val modules: List<ModuleCoverage>,
) {
    /**
     * Porcentaje global de cobertura, de `0.0` a `100.0`, para el resumen del
     * issue #21.
     *
     * Se calcula sobre el TOTAL de subtemas de todos los módulos juntos, no
     * como el promedio de [ModuleCoverage.coveragePercentage] de cada uno.
     * La diferencia importa: un módulo de 1 subtema no debe pesar lo mismo
     * que uno de 10 en el número global, o el resumen mentiría sobre cuánto
     * del curso completo lleva cubierto el estudiante.
     */
    val globalPercentage: Double
        get() {
            val todos = modules.flatMap { it.subtopics }
            if (todos.isEmpty()) return 0.0
            val cubiertos = todos.count { it.status.countsAsCovered }
            return cubiertos.toDouble() / todos.size * 100.0
        }
}
