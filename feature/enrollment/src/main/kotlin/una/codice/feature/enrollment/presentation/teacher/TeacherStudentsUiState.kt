package una.codice.feature.enrollment.presentation.teacher

import una.codice.core.domain.model.GroupStudent
import una.codice.core.domain.model.TeacherGroupSummary

data class TeacherStudentsUiState(
    val isLoading: Boolean = false,
    val groups: List<TeacherGroupSummary> = emptyList(),
    val selectedGroupId: String? = null,
    val students: List<GroupStudent> = emptyList(),
) {
    val selectedGroup: TeacherGroupSummary? = groups.find { it.group.id == selectedGroupId }
    
    val pendingRequests: List<GroupStudent> = students.filter { it.status.name == "PENDING" }
    val enrolledStudents: List<GroupStudent> = students.filter { it.status.name == "ACTIVE" }
}
