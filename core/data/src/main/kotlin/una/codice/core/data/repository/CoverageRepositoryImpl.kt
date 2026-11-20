package una.codice.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import una.codice.core.data.network.apiCall
import una.codice.core.data.network.coverage.CoverageApiService
import una.codice.core.data.network.coverage.toDomain
import una.codice.core.data.network.enrollment.EnrollmentApiService
import una.codice.core.data.network.teacher.TeacherApiService
import una.codice.core.data.network.teacher.toDomain as toGroupCoverage
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.GroupCoverage
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.repository.CoverageRepository
import una.codice.core.domain.result.DomainResult

/**
 * [CoverageRepository] contra el backend de Códice.
 *
 * La cobertura del backend es por grupo y el contrato de dominio solo recibe al estudiante, así que
 * primero se buscan sus matrículas (`GET /api/v1/enrollments/my`) y se usa el primer grupo con la
 * matrícula aprobada (`ACTIVE`). Sin ninguna aprobada todavía no hay temario que mostrar: se entrega
 * una lista vacía y la pantalla muestra su estado vacío en vez de un error.
 *
 * El backend ya manda todos los subtemas del temario, también los que no tienen apuntes
 * (`NO_NOTES`), que es la garantía que pide el KDoc de [CoverageRepository]. El `studentId` no
 * viaja al servidor: el backend usa el dueño del token.
 */
@Singleton
class CoverageRepositoryImpl @Inject constructor(
    private val enrollmentApiService: EnrollmentApiService,
    private val coverageApiService: CoverageApiService,
    private val teacherApiService: TeacherApiService,
) : CoverageRepository {

    override suspend fun getSubtopicCoverage(studentId: String, groupId: String?): DomainResult<List<SubtopicCoverage>> =
        apiCall(onHttpError = ::toDomainError) {
            val group = groupId?.toLongOrNull()
                ?: enrollmentApiService.getMyEnrollments().firstOrNull { it.isActive }?.groupId
            if (group == null) {
                emptyList()
            } else {
                coverageApiService.getMyCoverage(group).subtopics.map { it.toDomain() }
            }
        }

    private fun toDomainError(code: Int): DomainError? = when (code) {
        401 -> DomainError.Unauthorized()
        else -> null
    }

    /** `GET /api/v1/grupos/{id}/cobertura`: agregada y anónima. A quien no es el profesor del grupo, 403. */
    override suspend fun getGroupCoverage(groupId: String): DomainResult<GroupCoverage> =
        apiCall(onHttpError = { httpCode ->
            when (httpCode) {
                401 -> DomainError.Unauthorized()
                403 -> DomainError.Unknown("Solo el profesor del grupo ve su cobertura")
                else -> null
            }
        }) {
            teacherApiService.getGroupCoverage(groupId.toLong()).toGroupCoverage()
        }
}
