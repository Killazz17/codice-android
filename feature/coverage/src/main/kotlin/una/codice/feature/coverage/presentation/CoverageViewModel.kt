package una.codice.feature.coverage.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import una.codice.core.designsystem.component.CourseOption
import una.codice.core.domain.repository.CourseSelectionRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.auth.ObserveSessionUseCase
import una.codice.core.domain.usecase.coverage.GetCoverageMapUseCase

/**
 * Orquesta la pantalla de Mapa de cobertura (issues #20 y #21, que todavía
 * no existen — este ViewModel se prueba y se compila solo, sin composable
 * encima, igual que pide la "Definición de terminado" separar Presentation
 * de UI en tarjetas distintas).
 *
 * ### Por qué depende de [ObserveSessionUseCase] además de [GetCoverageMapUseCase]
 *
 * [GetCoverageMapUseCase] pide un `studentId`, y este ViewModel no lo recibe
 * como argumento de navegación (a diferencia de, por ejemplo, abrir un
 * apunte por id): el mapa de cobertura es siempre el del estudiante que tiene
 * la sesión abierta. [ObserveSessionUseCase] es la misma pieza que ya usa
 * `MainViewModel` en `:app` para decidir el grafo de navegación — aquí se
 * reutiliza para obtener el [Session.userId] una sola vez al entrar a la
 * pantalla, no para reaccionar a cada cambio de sesión (si la sesión se
 * cierra, el `NavHost` ya saca al usuario de este grafo por completo).
 */
@HiltViewModel
class CoverageViewModel @Inject constructor(
    private val observeSession: ObserveSessionUseCase,
    private val getCoverageMap: GetCoverageMapUseCase,
    private val courseSelection: CourseSelectionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<CoverageUiState>(CoverageUiState.Loading)
    val state: StateFlow<CoverageUiState> = _state.asStateFlow()

    // Guardado para que onRetry() no dependa de volver a leer la sesión —
    // si el problema fue de red, reintentar no debería fallar de nuevo solo
    // porque observeSession() tardó en reemitir.
    private var studentId: String? = null

    /** El grupo del mapa que se muestra; `null` deja que el repositorio use el primero aprobado. */
    private var groupId: String? = null

    /**
     * Los cursos para el selector y el elegido, compartido con la Biblioteca. El Mapa no tiene
     * "Todos": sin elección (o con una que ya no existe) muestra el primer curso.
     */
    private val courseBarUpdates =
        combine(courseSelection.observeCourses(), courseSelection.selectedGroupId) { list, selected ->
            CourseBarState(
                options = list.map { CourseOption(it.id, it.courseCode.ifBlank { it.courseName }) },
                selectedGroupId = selected?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id,
            )
        }

    val courseBar: StateFlow<CourseBarState> =
        courseBarUpdates.stateIn(viewModelScope, SharingStarted.Eagerly, CourseBarState())

    init {
        viewModelScope.launch {
            // `filterNotNull().first()`: esta pantalla solo se alcanza ya con
            // sesión iniciada (el NavHost la gatea), así que un `null` aquí
            // es siempre el instante de arranque de ObserveSessionUseCase
            // (ver el KDoc de SessionState en :app), nunca un "no hay
            // sesión" real que haya que mostrar como error.
            studentId = observeSession().filterNotNull().first().userId
            // Se recarga cada vez que cambia el curso elegido, aquí o en la Biblioteca.
            // Sobre el flujo y no sobre courseBar: su valor inicial vacío dispararía una carga de más.
            courseBarUpdates.map { it.selectedGroupId }.distinctUntilChanged().collect { id ->
                groupId = id
                cargarCobertura()
            }
        }
    }

    /** Reintenta después de un [CoverageUiState.Error] — el botón "Reintentar" de la pantalla. */
    fun onCourseSelected(groupId: String?) {
        courseSelection.select(groupId)
    }

    fun onRetry() {
        if (_state.value !is CoverageUiState.Error) return
        cargarCobertura()
    }

    private fun cargarCobertura() {
        val id = studentId ?: return
        _state.value = CoverageUiState.Loading

        viewModelScope.launch {
            when (val result = getCoverageMap(id, groupId)) {
                is DomainResult.Success -> {
                    _state.value = if (result.data.modules.isEmpty()) {
                        CoverageUiState.Empty
                    } else {
                        CoverageUiState.Content(result.data)
                    }
                }
                is DomainResult.Failure -> {
                    _state.value = CoverageUiState.Error(result.error.message)
                }
            }
        }
    }
}

/** El selector de curso del Mapa. */
data class CourseBarState(
    val options: List<CourseOption> = emptyList(),
    val selectedGroupId: String? = null,
)
