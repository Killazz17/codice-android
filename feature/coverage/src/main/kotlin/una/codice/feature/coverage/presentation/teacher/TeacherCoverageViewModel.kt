package una.codice.feature.coverage.presentation.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import una.codice.core.domain.model.GroupCoverage
import una.codice.core.domain.model.TeacherGroupSummary
import una.codice.core.domain.repository.CoverageRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.enrollment.ObserveTeacherGroupsUseCase
import javax.inject.Inject

/**
 * "Cobertura del grupo" (D7): la cobertura agregada y anónima del grupo elegido. Por defecto el
 * primero de los grupos del profesor; [onRetry] vuelve a pedirla después de un error.
 */
@HiltViewModel
class TeacherCoverageViewModel @Inject constructor(
    observeTeacherGroups: ObserveTeacherGroupsUseCase,
    private val coverageRepository: CoverageRepository,
) : ViewModel() {

    private val selectedGroupId = MutableStateFlow<String?>(null)
    private val retries = MutableStateFlow(0)

    private val groups = observeTeacherGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val currentGroupId = combine(groups, selectedGroupId) { list, selected ->
        selected ?: list?.firstOrNull()?.group?.id
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val coverage = combine(currentGroupId, retries) { id, _ -> id }
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                flow<DomainResult<GroupCoverage>?> {
                    emit(null)
                    emit(coverageRepository.getGroupCoverage(id))
                }
            }
        }

    val state: StateFlow<TeacherCoverageUiState> =
        combine(groups, currentGroupId, coverage) { list, id, result -> toUiState(list, id, result) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TeacherCoverageUiState.Loading)

    fun onGroupSelected(groupId: String) {
        selectedGroupId.value = groupId
    }

    fun onRetry() {
        retries.value++
    }

    private fun toUiState(
        groups: List<TeacherGroupSummary>?,
        groupId: String?,
        result: DomainResult<GroupCoverage>?,
    ): TeacherCoverageUiState = when {
        groups == null -> TeacherCoverageUiState.Loading
        groups.isEmpty() || groupId == null -> TeacherCoverageUiState.NoGroups
        result == null -> TeacherCoverageUiState.Loading
        result is DomainResult.Failure -> TeacherCoverageUiState.Error(result.error.message)
        else -> TeacherCoverageUiState.Content(
            groups = groups,
            selectedGroupId = groupId,
            coverage = (result as DomainResult.Success).data,
        )
    }
}
