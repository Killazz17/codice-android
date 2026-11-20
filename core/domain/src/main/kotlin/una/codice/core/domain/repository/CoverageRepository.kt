package una.codice.core.domain.repository

import una.codice.core.domain.model.GroupCoverage
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.result.DomainResult

/**
 * El contrato de cobertura: entrega la cobertura del estudiante en filas
 * planas, una por subtema. Ver [una.codice.core.domain.model.SubtopicCoverage]
 * para por qué la forma es plana y no ya agrupada por módulo.
 *
 * Vive en `:core:domain`; lo implementa `CoverageRepositoryImpl` en
 * `:core:data` (issue #23), igual que [AuthRepository] con
 * `AuthRepositoryImpl`. Mismo motivo: los tests de
 * [una.codice.core.domain.usecase.coverage.GetCoverageMapUseCase] pueden usar
 * un fake en memoria sin Room ni emulador.
 */
interface CoverageRepository {

    /**
     * La cobertura de todos los subtemas del curso, para el estudiante
     * [studentId].
     *
     * Un subtema sin ningún registro en `coverage` todavía debe llegar igual
     * en la lista, con [una.codice.core.domain.model.CoverageStatus.NO_NOTES]
     * — el mapa de cobertura tiene que mostrar el 100% del temario publicado,
     * no solo los subtemas donde ya hay algo. Esa garantía es responsabilidad
     * de la implementación (issue #23), típicamente con un `LEFT JOIN` desde
     * `subtopic` hacia `coverage` en vez de partir de `coverage`.
     */
    /** @param groupId El grupo (curso) del mapa; con `null`, el primero con matrícula aprobada. */
    suspend fun getSubtopicCoverage(studentId: String, groupId: String? = null): DomainResult<List<SubtopicCoverage>>

    /**
     * La cobertura agregada y anónima de un grupo, para su profesor: una fila por subtema con qué
     * porcentaje del grupo lo tiene cubierto, parcial o sin apuntes, ya agrupada por módulo. Ningún
     * dato identifica a un estudiante.
     */
    suspend fun getGroupCoverage(groupId: String): DomainResult<GroupCoverage>
}
