package una.codice.feature.coverage.presentation.teacher

import una.codice.core.domain.model.GroupCoverage
import una.codice.core.domain.model.TeacherGroupSummary

/** Estado de "Cobertura del grupo" (D7). */
sealed interface TeacherCoverageUiState {

    data object Loading : TeacherCoverageUiState

    /** El profesor todavía no tiene grupos: no hay cobertura que mostrar. */
    data object NoGroups : TeacherCoverageUiState

    data class Error(val message: String) : TeacherCoverageUiState

    data class Content(
        val groups: List<TeacherGroupSummary>,
        val selectedGroupId: String,
        val coverage: GroupCoverage,
    ) : TeacherCoverageUiState
}
