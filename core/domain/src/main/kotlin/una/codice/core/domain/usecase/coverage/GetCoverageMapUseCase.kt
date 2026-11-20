package una.codice.core.domain.usecase.coverage

import una.codice.core.domain.model.CoverageMap
import una.codice.core.domain.model.ModuleCoverage
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.repository.CoverageRepository
import una.codice.core.domain.result.DomainResult

/**
 * Arma el mapa de cobertura de un estudiante: pide las filas planas al
 * repositorio y las agrupa por módulo con su porcentaje ya calculado.
 *
 * ### Por qué el agrupamiento vive aquí y no en la consulta SQL
 *
 * [CoverageRepository] entrega una fila por subtema a propósito (ver su
 * KDoc). Agrupar y calcular el porcentaje aquí, en Kotlin puro, es lo que
 * permite probar esa cuenta con `JUnit` sin Room ni un emulador — es
 * exactamente el criterio que ya sigue el resto de `:core:domain` (por
 * ejemplo, la validación de [una.codice.core.domain.usecase.auth.LoginUseCase]
 * vive en el caso de uso y no en el backend).
 *
 * @param coverageRepository de dónde salen las filas planas. En producción,
 *   `CoverageRepositoryImpl` (issue #23); en los tests, un fake en memoria.
 */
class GetCoverageMapUseCase(
    private val coverageRepository: CoverageRepository,
) {

    suspend operator fun invoke(studentId: String, groupId: String? = null): DomainResult<CoverageMap> {
        val subtopicCoverages = when (val result = coverageRepository.getSubtopicCoverage(studentId, groupId)) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> result.data
        }

        return DomainResult.Success(buildCoverageMap(subtopicCoverages))
    }

    /**
     * Agrupa por módulo (número + título) y ordena el resultado por número de
     * módulo. Dentro de cada módulo se respeta el orden en que llegaron los
     * subtemas — ver la nota de orden en [ModuleCoverage.subtopics].
     */
    private fun buildCoverageMap(subtopicCoverages: List<SubtopicCoverage>): CoverageMap {
        val modules = subtopicCoverages
            .groupBy { it.moduleNumber to it.moduleTitle }
            .map { (modulo, subtemasDelModulo) ->
                ModuleCoverage(
                    moduleNumber = modulo.first,
                    moduleTitle = modulo.second,
                    subtopics = subtemasDelModulo,
                )
            }
            .sortedBy { it.moduleNumber }

        return CoverageMap(modules)
    }
}
