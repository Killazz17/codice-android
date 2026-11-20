package una.codice.feature.enrollment.presentation.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import una.codice.core.domain.model.NewGroup
import una.codice.core.domain.repository.EnrollmentRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.auth.ObserveSessionUseCase
import una.codice.core.domain.usecase.enrollment.ObserveTeacherGroupsUseCase
import javax.inject.Inject

/**
 * Inicio del profesor: sus grupos y, sobre cada curso, dos acciones: agregarle un grupo y eliminarlo.
 */
@HiltViewModel
class TeacherCoursesViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    observeTeacherGroups: ObserveTeacherGroupsUseCase,
    private val enrollmentRepository: EnrollmentRepository,
) : ViewModel() {

    private val dialog = MutableStateFlow<CourseDialog?>(null)

    /** El id del grupo recién creado, para abrir su invitación (con el código) una sola vez. */
    private val _groupCreated = Channel<String>(capacity = Channel.CONFLATED)
    val groupCreated = _groupCreated.receiveAsFlow()

    val uiState: StateFlow<TeacherCoursesUiState> = combine(
        observeSession(),
        observeTeacherGroups(),
        dialog,
    ) { _, groups, dialog ->
        TeacherCoursesUiState(
            isLoading = false,
            groups = groups,
            // La sesión no guarda el nombre; el backend lo manda en cada grupo del profesor.
            teacherName = groups.firstOrNull()?.group?.professorName.orEmpty(),
            activeCoursesCount = groups.map { it.group.courseId }.distinct().size,
            totalPendingRequests = groups.sumOf { it.pendingRequestsCount },
            dialog = dialog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TeacherCoursesUiState(isLoading = true)
    )

    /** "Agregar grupo a este curso": abre el formulario con el siguiente número y el periodo del último grupo. */
    fun onAddGroupClick(courseId: String) {
        val groups = uiState.value.groups.map { it.group }.filter { it.courseId == courseId }
        val last = groups.maxByOrNull { it.groupNumber.toIntOrNull() ?: 0 } ?: return
        dialog.value = CourseDialog.AddGroup(
            courseId = courseId,
            courseCode = last.courseCode.ifBlank { last.courseId },
            courseName = last.courseName,
            groupNumber = nextGroupNumber(groups.map { it.groupNumber }),
            period = last.period,
        )
    }

    /** "Eliminar curso": pide confirmación, o explica que no se puede si todavía tiene estudiantes matriculados. */
    fun onDeleteCourseClick(courseId: String) {
        val summaries = uiState.value.groups.filter { it.group.courseId == courseId }
        val group = summaries.firstOrNull()?.group ?: return
        dialog.value = CourseDialog.ConfirmDelete(
            courseId = courseId,
            courseCode = group.courseCode.ifBlank { group.courseId },
            courseName = group.courseName,
            activeStudents = summaries.sumOf { it.activeStudentCount },
        )
    }

    fun onGroupNumberChange(value: String) =
        updateAddGroup { copy(groupNumber = value.filter { it.isDigit() }.take(2), error = null) }

    fun onPeriodChange(value: String) = updateAddGroup { copy(period = value.take(20), error = null) }

    fun onRequiresApprovalChange(value: Boolean) = updateAddGroup { copy(requiresApproval = value) }

    fun onDismissDialog() {
        if (dialog.value?.isLoading != true) dialog.value = null
    }

    fun onConfirmAddGroup() {
        val current = dialog.value as? CourseDialog.AddGroup ?: return
        if (current.isLoading) return
        if (current.groupNumber.isBlank() || current.period.isBlank()) {
            dialog.value = current.copy(error = "El número de grupo y el periodo son obligatorios")
            return
        }
        dialog.value = current.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = enrollmentRepository.addGroup(
                courseId = current.courseId,
                newGroup = NewGroup(
                    groupNumber = current.groupNumber.padStart(2, '0'),
                    period = current.period.trim(),
                    requiresApproval = current.requiresApproval,
                ),
            )
            when (result) {
                is DomainResult.Success -> {
                    dialog.value = null
                    _groupCreated.trySend(result.data.groupId)
                }
                is DomainResult.Failure -> dialog.value = current.copy(isLoading = false, error = result.error.message)
            }
        }
    }

    fun onConfirmDeleteCourse() {
        val current = dialog.value as? CourseDialog.ConfirmDelete ?: return
        if (current.isLoading || current.activeStudents > 0) return
        dialog.value = current.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val result = enrollmentRepository.deleteCourse(current.courseId)) {
                // El curso desaparece solo: el repositorio refresca la lista de grupos del profesor.
                is DomainResult.Success -> dialog.value = null
                is DomainResult.Failure -> dialog.value = current.copy(isLoading = false, error = result.error.message)
            }
        }
    }

    private fun updateAddGroup(change: CourseDialog.AddGroup.() -> CourseDialog.AddGroup) {
        val current = dialog.value as? CourseDialog.AddGroup ?: return
        if (current.isLoading) return
        dialog.value = current.change()
    }

    companion object {
        /** El número que sigue al más alto que ya tiene el curso, con dos dígitos: `["01", "02"]` da `"03"`. */
        fun nextGroupNumber(existing: List<String>): String =
            ((existing.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0) + 1).toString().padStart(2, '0')
    }
}
