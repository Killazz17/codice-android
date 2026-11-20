package una.codice.feature.enrollment.presentation.teacher

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.repository.EnrollmentRepository
import javax.inject.Inject

@HiltViewModel
class TeacherInviteViewModel @Inject constructor(
    private val enrollmentRepository: EnrollmentRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupIdArg: String = savedStateHandle["groupId"] ?: ""
    private val _selectedGroupId = MutableStateFlow(groupIdArg)
    private val _showRegenerateConfirmation = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TeacherInviteUiState> = combine(
        authRepository.currentSession().flatMapLatest { session ->
            enrollmentRepository.observeTeacherGroups(session?.userId ?: "")
        },
        _selectedGroupId.flatMapLatest { id ->
            enrollmentRepository.observeGroupInviteInfo(id)
        },
        _showRegenerateConfirmation
    ) { groups, inviteInfo, showConfirm ->
        TeacherInviteUiState(
            isLoading = false,
            inviteInfo = inviteInfo,
            groups = groups,
            selectedGroupId = _selectedGroupId.value,
            showRegenerateConfirmation = showConfirm
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TeacherInviteUiState(isLoading = true)
    )

    fun onGroupSelected(groupId: String) {
        _selectedGroupId.value = groupId
    }

    fun onRegenerateClick() {
        _showRegenerateConfirmation.value = true
    }

    fun onConfirmRegenerate() {
        viewModelScope.launch {
            _showRegenerateConfirmation.value = false
            enrollmentRepository.regenerateInviteCode(_selectedGroupId.value)
        }
    }

    fun onDismissRegenerate() {
        _showRegenerateConfirmation.value = false
    }

    fun onToggleApproval(required: Boolean) {
        viewModelScope.launch {
            enrollmentRepository.updateInviteSettings(_selectedGroupId.value, required)
        }
    }
}
