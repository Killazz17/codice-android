package una.codice.feature.enrollment.presentation.teacher

import una.codice.core.domain.model.GroupInviteInfo
import una.codice.core.domain.model.TeacherGroupSummary

data class TeacherInviteUiState(
    val isLoading: Boolean = false,
    val inviteInfo: GroupInviteInfo? = null,
    val groups: List<TeacherGroupSummary> = emptyList(),
    val selectedGroupId: String = "",
    val showRegenerateConfirmation: Boolean = false
) {
    val selectedGroup: TeacherGroupSummary? = groups.find { it.group.id == selectedGroupId }
}
