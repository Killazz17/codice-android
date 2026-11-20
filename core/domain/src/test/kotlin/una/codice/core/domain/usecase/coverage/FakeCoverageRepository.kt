package una.codice.core.domain.usecase.coverage

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.GroupCoverage
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.repository.CoverageRepository
import una.codice.core.domain.result.DomainResult

/**
 * Implementación falsa de [CoverageRepository] para los tests: devuelve una
 * lista fija de filas en memoria, sin Room ni red.
 *
 * Igual que `FakeAuthRepository`, registra las llamadas en [getCalls] — no
 * porque [GetCoverageMapUseCase] tenga una regla de "no llamar" que probar
 * como el login, sino porque documenta con qué `studentId` se preguntó, útil
 * si más adelante se agrega una regla que dependa de ese parámetro.
 *
 * @param rows Lo que devuelve [getSubtopicCoverage] mientras [failure] sea
 *   `null`.
 * @param failure Si no es `null`, [getSubtopicCoverage] devuelve este error
 *   en vez de [rows] — para probar que el use case propaga el fallo tal cual.
 */
class FakeCoverageRepository(
    private val rows: List<SubtopicCoverage> = emptyList(),
    private val failure: DomainError? = null,
) : CoverageRepository {

    val getCalls = mutableListOf<String>()

    override suspend fun getSubtopicCoverage(studentId: String, groupId: String?): DomainResult<List<SubtopicCoverage>> {
        getCalls += studentId
        return failure?.let { DomainResult.Failure(it) } ?: DomainResult.Success(rows)
    }

    /** La cobertura del grupo es del profesor; estos tests solo usan la del estudiante. */
    override suspend fun getGroupCoverage(groupId: String): DomainResult<GroupCoverage> =
        DomainResult.Failure(DomainError.Unknown())
}
