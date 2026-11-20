package una.codice.feature.notes.presentation

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.GroupCoverage
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.repository.CoverageRepository
import una.codice.core.domain.result.DomainResult

/**
 * [CoverageRepository] en memoria para los tests de la captura: devuelve las filas de [rowsByGroup] según el grupo
 * pedido y anota cada grupo consultado en [requestedGroups].
 */
class FakeCoverageRepository(
    var rowsByGroup: Map<String?, List<SubtopicCoverage>> = emptyMap(),
) : CoverageRepository {

    val requestedGroups = mutableListOf<String?>()

    override suspend fun getSubtopicCoverage(studentId: String, groupId: String?): DomainResult<List<SubtopicCoverage>> {
        requestedGroups += groupId
        return DomainResult.Success(rowsByGroup[groupId].orEmpty())
    }

    override suspend fun getGroupCoverage(groupId: String): DomainResult<GroupCoverage> =
        DomainResult.Failure(DomainError.Unknown())
}
