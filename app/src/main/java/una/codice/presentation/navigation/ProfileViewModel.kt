package una.codice.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.repository.CourseSelectionRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.auth.LogoutUseCase
import una.codice.core.domain.usecase.auth.ObserveSessionUseCase
import una.codice.core.domain.usecase.enrollment.ObserveTeacherGroupsUseCase
import una.codice.core.domain.usecase.note.GetNotesUseCase
import una.codice.core.domain.usecase.review.GetPendingReviewsUseCase
import androidx.annotation.StringRes
import una.codice.R

data class ProfileUiState(
    val userName: String = "",
    val email: String = "",
    val role: UserRole = UserRole.STUDENT,
    val activity: ProfileActivity = ProfileActivity(),
) {
    val initials: String
        get() = userName.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
            .ifEmpty { "?" }

    @get:StringRes
    val roleLabelRes: Int
        get() = if (role == UserRole.TEACHER) R.string.app_teacher else R.string.app_student
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase,
    private val observeSession: ObserveSessionUseCase,
    private val observeTeacherGroups: ObserveTeacherGroupsUseCase,
    private val courseSelection: CourseSelectionRepository,
    private val getNotes: GetNotesUseCase,
    private val getPendingReviews: GetPendingReviewsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var session: Session? = null
    private var activityJob: Job? = null

    init {
        viewModelScope.launch {
            observeSession().collect { current ->
                session = current
                if (current != null) {
                    _state.update {
                        it.copy(
                            userName = current.userName,
                            email = current.email,
                            role = current.role
                        )
                    }
                    refresh()
                }
            }
        }
    }

    /**
     * Vuelve a pedir las cifras de "Mi actividad". El ViewModel sobrevive al cambio de pestaña y los datos viven en el
     * servidor, así que la pantalla lo llama cada vez que se abre. Una consulta nueva cancela la anterior.
     */
    fun refresh() {
        val current = session ?: return
        activityJob?.cancel()
        activityJob = viewModelScope.launch {
            val activity = if (current.role == UserRole.TEACHER) loadTeacherActivity() else loadStudentActivity(current.userId)
            _state.update { it.copy(activity = activity) }
        }
    }

    private suspend fun loadTeacherActivity(): ProfileActivity =
        teacherActivity(orNull { observeTeacherGroups().first() })

    private suspend fun loadStudentActivity(studentId: String): ProfileActivity = coroutineScope {
        val courses = async { orNull { courseSelection.observeCourses().first().size } }
        val notes = async { orNull { getNotes(studentId).first().size } }
        val dueReviews = async { (getPendingReviews() as? DomainResult.Success)?.data?.size }
        studentActivity(courses.await(), notes.await(), dueReviews.await())
    }

    // Una cifra que no se pudo pedir se muestra sin conocer; no es un error de la pantalla. La cancelación sí se propaga.
    private suspend fun <T> orNull(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    fun onLogout() {
        viewModelScope.launch { logoutUseCase() }
    }
}
