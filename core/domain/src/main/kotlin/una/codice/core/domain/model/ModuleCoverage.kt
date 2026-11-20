package una.codice.core.domain.model

/**
 * Todos los subtemas de un módulo con su cobertura, más el porcentaje ya
 * calculado.
 *
 * Es lo que consume directamente la barra de progreso por módulo del issue
 * #21 y cada fila con su etiqueta de estado del issue #20 — ninguna de esas
 * dos pantallas debería tener que volver a contar subtemas por su cuenta.
 *
 * @property subtopics En el orden en que los entregó el repositorio (por
 *   `position` dentro del módulo, ver `idx_subtopic_module` en el modelo de
 *   datos). [GetCoverageMapUseCase][una.codice.core.domain.usecase.coverage.GetCoverageMapUseCase]
 *   no los reordena.
 */
data class ModuleCoverage(
    val moduleNumber: Int,
    val moduleTitle: String,
    val subtopics: List<SubtopicCoverage>,
) {
    /**
     * Porcentaje de subtemas [CoverageStatus.countsAsCovered] dentro de este
     * módulo, de `0.0` a `100.0`.
     *
     * `0.0` cuando el módulo no tiene subtemas, en vez de `NaN` por una
     * división entre cero — así la UI no tiene que blindarse contra ese caso
     * antes de dibujar la barra de progreso.
     */
    val coveragePercentage: Double
        get() {
            if (subtopics.isEmpty()) return 0.0
            val cubiertos = subtopics.count { it.status.countsAsCovered }
            return cubiertos.toDouble() / subtopics.size * 100.0
        }
}
