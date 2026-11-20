package una.codice.core.data.network.coverage

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import una.codice.core.domain.model.CoverageStatus
import una.codice.core.domain.model.SubtopicCoverage

/**
 * Cobertura personal contra el backend de Códice: `GET /api/v1/grupos/{grupoId}/cobertura/mia`.
 *
 * El backend responde 403 si el usuario no tiene matrícula aprobada en el grupo, por eso
 * `CoverageRepositoryImpl` primero busca el grupo en las matrículas activas.
 */
interface CoverageApiService {

    @GET("api/v1/grupos/{grupoId}/cobertura/mia")
    suspend fun getMyCoverage(@Path("grupoId") groupId: Long): PersonalCoverageDto
}

/** `PersonalCoverageDTO` del backend. El porcentaje lo vuelve a calcular el dominio. */
@Serializable
data class PersonalCoverageDto(
    val groupId: Long,
    val coveredPercentage: Double = 0.0,
    val subtopics: List<SubtopicCoverageDto> = emptyList(),
)

/** Un subtema del temario con su estado. Llega uno por subtema, aunque no tenga apuntes (`NO_NOTES`). */
@Serializable
data class SubtopicCoverageDto(
    val subtopicId: Long,
    val subtopicCode: String,
    val subtopicTitle: String,
    val status: String,
    val moduleNumber: Int? = null,
    val moduleTitle: String? = null,
)

fun SubtopicCoverageDto.toDomain(): SubtopicCoverage = SubtopicCoverage(
    subtopicId = subtopicId.toString(),
    subtopicCode = subtopicCode,
    subtopicTitle = subtopicTitle,
    moduleNumber = moduleNumber ?: 0,
    moduleTitle = moduleTitle.orEmpty(),
    // Un estado que la app no conoce se muestra como sin apuntes en vez de tumbar el mapa.
    status = CoverageStatus.entries.firstOrNull { it.name == status } ?: CoverageStatus.NO_NOTES,
)
