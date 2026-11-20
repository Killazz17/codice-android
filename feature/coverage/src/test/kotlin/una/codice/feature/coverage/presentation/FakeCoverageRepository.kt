package una.codice.feature.coverage.presentation

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.GroupCoverage
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.repository.CoverageRepository
import una.codice.core.domain.result.DomainResult

/**
 * Copia local de la [CoverageRepository] falsa que ya usa
 * `GetCoverageMapUseCaseTest` en `:core:domain` — no compartida a propósito
 * (mismo motivo de siempre: no hay `testFixtures` configurado).
 */
class FakeCoverageRepository(
    private val rows: List<SubtopicCoverage> = emptyList(),
    private val failure: DomainError? = null,
) : CoverageRepository {

    /** Cada `studentId` con el que se invocó [getSubtopicCoverage] — cuenta cuántas veces se consultó. */
    val calls = mutableListOf<String>()

    override suspend fun getSubtopicCoverage(studentId: String, groupId: String?): DomainResult<List<SubtopicCoverage>> {
        calls += studentId
        return failure?.let { DomainResult.Failure(it) } ?: DomainResult.Success(rows)
    }

    /** La cobertura del grupo es del profesor; estos tests solo usan la del estudiante. */
    override suspend fun getGroupCoverage(groupId: String): DomainResult<GroupCoverage> =
        DomainResult.Failure(DomainError.Unknown())
}
