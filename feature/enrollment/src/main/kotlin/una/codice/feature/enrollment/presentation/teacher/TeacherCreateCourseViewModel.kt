package una.codice.feature.enrollment.presentation.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import una.codice.core.domain.model.NewCourse
import una.codice.core.domain.repository.EnrollmentRepository
import una.codice.core.domain.result.DomainResult
import javax.inject.Inject

/**
 * "Crear curso" (D2). Valida que el formulario esté completo antes de llamar al backend, que crea el
 * curso y su primer grupo y genera el código de invitación. Al terminar navega a "Invitar" con ese grupo.
 */
@HiltViewModel
class TeacherCreateCourseViewModel @Inject constructor(
    private val enrollmentRepository: EnrollmentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TeacherCreateCourseUiState())
    val state: StateFlow<TeacherCreateCourseUiState> = _state.asStateFlow()

    /** El id del grupo recién creado, para abrir su pantalla de invitación una sola vez. */
    private val _created = Channel<String>(capacity = Channel.CONFLATED)
    val created = _created.receiveAsFlow()

    fun onCodeChange(value: String) =
        update(CourseField.CODE) { copy(code = value.uppercase().filter { !it.isWhitespace() }.take(10)) }

    fun onNameChange(value: String) = update(CourseField.NAME) { copy(name = value.take(120)) }

    fun onGroupNumberChange(value: String) =
        update(CourseField.GROUP_NUMBER) { copy(groupNumber = value.filter { it.isDigit() }.take(2)) }

    fun onPeriodChange(value: String) = update(CourseField.PERIOD) { copy(period = value.take(20)) }

    fun onRequiresApprovalChange(value: Boolean) {
        _state.value = _state.value.copy(requiresApproval = value)
    }

    fun onSubmit() {
        val current = _state.value
        if (current.isLoading) return
        val errors = validate(current)
        if (errors.isNotEmpty()) {
            _state.value = current.copy(fieldErrors = errors)
            return
        }
        _state.value = current.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = enrollmentRepository.createCourse(
                NewCourse(
                    code = current.code,
                    name = current.name.trim(),
                    groupNumber = current.groupNumber.padStart(2, '0'),
                    period = current.period.trim(),
                    requiresApproval = current.requiresApproval,
                ),
            )
            when (result) {
                is DomainResult.Success -> {
                    _state.value = TeacherCreateCourseUiState()
                    _created.trySend(result.data.groupId)
                }
                is DomainResult.Failure ->
                    _state.value = _state.value.copy(isLoading = false, errorMessage = result.error.message)
            }
        }
    }

    private fun validate(state: TeacherCreateCourseUiState): Map<CourseField, String> = buildMap {
        // El backend arma el código de invitación como CODIGO-NN-XXXX y la app del estudiante acepta 6-2-4.
        if (!CODE_REGEX.matches(state.code)) put(CourseField.CODE, "Usá 6 letras o números, p. ej. EIF411")
        if (state.name.isBlank()) put(CourseField.NAME, "El nombre del curso es obligatorio")
        if (state.groupNumber.isBlank()) put(CourseField.GROUP_NUMBER, "El número de grupo es obligatorio")
        if (state.period.isBlank()) put(CourseField.PERIOD, "El periodo es obligatorio, p. ej. 2026-II")
    }

    private fun update(field: CourseField, change: TeacherCreateCourseUiState.() -> TeacherCreateCourseUiState) {
        val current = _state.value
        _state.value = current.change().copy(fieldErrors = current.fieldErrors - field, errorMessage = null)
    }

    private companion object {
        val CODE_REGEX = Regex("^[A-Z0-9]{6}$")
    }
}
