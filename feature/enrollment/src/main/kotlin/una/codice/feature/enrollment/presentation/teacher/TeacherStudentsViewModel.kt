package una.codice.feature.enrollment.presentation.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import una.codice.core.domain.repository.EnrollmentRepository
import una.codice.core.domain.usecase.enrollment.ObserveTeacherGroupsUseCase
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import una.codice.core.domain.model.GroupStudent
import una.codice.core.domain.result.DomainResult

/**
 * "Estudiantes del curso" (D5): las solicitudes pendientes y los inscritos del grupo elegido, contra
 * el backend. Aprobar o rechazar refresca la lista sola (el repositorio vuelve a emitir).
 */
@HiltViewModel
class TeacherStudentsViewModel @Inject constructor(
    observeTeacherGroups: ObserveTeacherGroupsUseCase,
    private val enrollmentRepository: EnrollmentRepository,
) : ViewModel() {

    private val _selectedGroupId = MutableStateFlow<String?>(null)

    private val _events = Channel<TeacherStudentsEvent>(Channel.BUFFERED)
    /** Avisos de una sola vez: un estudiante quitado, o por qué no se pudo. */
    val events = _events.receiveAsFlow()

    private val groups = observeTeacherGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** El grupo elegido o, si todavía no elige, el primero de sus grupos. */
    private val currentGroupId = combine(groups, _selectedGroupId) { list, selected ->
        selected ?: list?.firstOrNull()?.group?.id
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val students = currentGroupId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else enrollmentRepository.observeGroupStudents(id)
    }

    val uiState: StateFlow<TeacherStudentsUiState> = combine(groups, currentGroupId, students) { list, id, students ->
        TeacherStudentsUiState(
            isLoading = list == null,
            groups = list.orEmpty(),
            selectedGroupId = id,
            students = students,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TeacherStudentsUiState(isLoading = true)
    )

    fun onGroupSelected(groupId: String) {
        _selectedGroupId.value = groupId
    }

    fun onApproveClick(enrollmentId: String) = decide(enrollmentId, approve = true)

    fun onRejectClick(enrollmentId: String) = decide(enrollmentId, approve = false)

    /** Quita a un estudiante inscrito del grupo que se está viendo. Sus apuntes se conservan. */
    fun onRemoveStudent(student: GroupStudent) {
        val groupId = uiState.value.selectedGroupId ?: return
        viewModelScope.launch {
            when (val result = enrollmentRepository.removeStudent(groupId, student.enrollmentId)) {
                is DomainResult.Success -> _events.trySend(TeacherStudentsEvent.Removed(student.user.fullName))
                is DomainResult.Failure -> _events.trySend(TeacherStudentsEvent.Failed(result.error.message))
            }
        }
    }

    private fun decide(enrollmentId: String, approve: Boolean) {
        viewModelScope.launch { enrollmentRepository.decideEnrollment(enrollmentId, approve) }
    }
}

/** Lo que la pantalla de estudiantes avisa una sola vez. El texto lo arma la pantalla para que salga en el idioma elegido. */
sealed interface TeacherStudentsEvent {
    data class Removed(val studentName: String) : TeacherStudentsEvent
    data class Failed(val message: String) : TeacherStudentsEvent
}
