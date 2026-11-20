package una.codice.feature.notes.presentation

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import una.codice.core.designsystem.component.CourseOption
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Note
import una.codice.core.domain.repository.CourseSelectionRepository
import una.codice.core.domain.model.SyncStatus
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.auth.ObserveSessionUseCase
import una.codice.core.domain.usecase.note.DeleteNoteUseCase
import una.codice.core.domain.usecase.note.GetNotesUseCase
import una.codice.core.domain.usecase.note.NoteFilter
import una.codice.core.domain.usecase.note.SearchNotesUseCase

/**
 * Orquesta la pantalla de Biblioteca (issue #15): combina la lista de
 * apuntes del estudiante con el texto buscado y los filtros activos, en un
 * solo [LibraryUiState].
 *
 * Igual que `CoverageViewModel`, depende de [ObserveSessionUseCase] solo para
 * resolver el `studentId` una vez al entrar a la pantalla — no para
 * reaccionar a cada cambio de sesión (si la sesión se cierra, el `NavHost` ya
 * saca al usuario de este grafo por completo).
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val observeSession: ObserveSessionUseCase,
    private val getNotes: GetNotesUseCase,
    private val searchNotes: SearchNotesUseCase,
    private val courseSelection: CourseSelectionRepository,
    private val deleteNote: DeleteNoteUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /**
     * El texto tal cual lo escribe el estudiante. Se combina con
     * [debounce] para no repetir el filtrado en cada tecla (criterio de
     * aceptación del issue #15: "escribir rápido no dispara una búsqueda por
     * letra"), pero [onQueryChange] igual actualiza [_state] de inmediato
     * para que el campo de texto no sienta ese retraso — el debounce es sobre
     * CUÁNDO se vuelve a filtrar, no sobre qué tan rápido aparece lo tecleado.
     */
    private val query = MutableStateFlow("")

    /** Los filtros de chip (módulo, estado de sincronización, sin clasificar) — el texto de búsqueda vive aparte, en [query]. */
    private val filterOptions = MutableStateFlow(NoteFilter())

    /**
     * El borrado pendiente de confirmación. Es un flujo aparte y no un `copy` sobre [_state] porque el `combine` de
     * abajo reconstruye el estado entero cada vez que llega una lista nueva: escrito directo sobre [_state], el
     * diálogo se cerraría solo en cuanto el servidor respondiera cualquier otra cosa.
     */
    private val deleteState = MutableStateFlow(DeleteState())

    init {
        viewModelScope.launch {
            // Mismo criterio que CoverageViewModel: esta pantalla solo se
            // alcanza ya con sesión iniciada, así que un `null` aquí es
            // siempre el instante de arranque de ObserveSessionUseCase.
            val studentId = observeSession().filterNotNull().first().userId

            // El curso elegido se comparte con el Mapa. Si ya no está entre sus cursos (p. ej. otra
            // sesión), se ve "Todos".
            val courses = combine(courseSelection.observeCourses(), courseSelection.selectedGroupId) { list, selected ->
                list to selected?.takeIf { id -> list.any { it.id == id } }
            }.distinctUntilChanged()

            @OptIn(ExperimentalCoroutinesApi::class)
            val notesOfCourse = courses.flatMapLatest { (list, groupId) ->
                getNotes(studentId, groupId).map { notes -> Triple(list, groupId, notes) }
            }

            combine(
                notesOfCourse,
                query.debounce(300).distinctUntilChanged(),
                filterOptions,
                getNotes.loadError,
                deleteState,
            ) { (list, groupId, notes), debouncedQuery, filterOpts, loadError, delete ->
                buildState(notes, debouncedQuery, filterOpts, loadError).copy(
                    courseOptions = list.map { CourseOption(it.id, it.courseCode.ifBlank { it.courseName }) },
                    selectedGroupId = groupId,
                    delete = delete,
                )
            }.collect { _state.value = it }
        }
    }

    /** Cambia el curso de la Biblioteca (y del Mapa); `null` es "Todos". Limpia el filtro de módulo, que era de otro curso. */
    fun onCourseSelected(groupId: String?) {
        filterOptions.value = filterOptions.value.copy(moduleId = null)
        courseSelection.select(groupId)
    }

    /** "Reintentar" tras un fallo al cargar: vuelve a pedir los apuntes al servidor. */
    fun onRetry() {
        getNotes.refresh()
    }

    fun onQueryChange(text: String) {
        _state.value = _state.value.copy(query = text)
        query.value = text
    }

    fun onModuleFilter(moduleId: String?) {
        filterOptions.value = filterOptions.value.copy(moduleId = moduleId)
    }

    fun onSyncFilter(status: SyncStatus?) {
        filterOptions.value = filterOptions.value.copy(syncStatus = status)
    }

    /** El toggle del chip "Sin clasificar" — parte de [NoteFilter], aunque el issue #15 no lo nombra explícitamente entre sus eventos. */
    fun onToggleSinClasificar(activo: Boolean) {
        filterOptions.value = filterOptions.value.copy(soloSinClasificar = activo)
    }

    /** El chip "Todos": limpia módulo/estado/sin-clasificar, sin tocar el texto buscado — son dos controles distintos en el issue #17. */
    fun onClearFilters() {
        filterOptions.value = NoteFilter()
    }

    /** La papelera de una tarjeta: abre el diálogo de confirmación. Todavía no borra nada. */
    fun onDeleteRequested(note: Note) {
        deleteState.value = DeleteState(pending = note)
    }

    /** "Cancelar" en el diálogo, o tocar fuera de él. */
    fun onDeleteDismissed() {
        deleteState.value = DeleteState()
    }

    /**
     * "Borrar" en el diálogo. Si sale bien no hace falta quitar el apunte de la lista a mano: el repositorio vuelve a
     * pedirla y el apunte deja de venir. Si falla, el diálogo queda abierto con el motivo para poder reintentar.
     */
    fun onDeleteConfirmed() {
        val note = deleteState.value.pending ?: return
        // Un segundo toque mientras la primera llamada sigue en vuelo mandaría dos borrados; el diálogo ya deshabilita
        // el botón, pero la guarda va aquí también porque el estado es de este ViewModel, no del composable.
        if (deleteState.value.deleting) return

        deleteState.value = DeleteState(pending = note, deleting = true)
        viewModelScope.launch {
            deleteState.value = when (val result = deleteNote(note.id)) {
                is DomainResult.Success -> DeleteState()
                is DomainResult.Failure -> DeleteState(pending = note, error = result.error.message)
            }
        }
    }

    private fun buildState(
        notes: List<Note>,
        debouncedQuery: String,
        filterOpts: NoteFilter,
        loadError: DomainError?,
    ): LibraryUiState {
        val filter = filterOpts.copy(query = debouncedQuery)
        // TODO: hallazgo H1.1 de la evaluación heurística (docs/usabilidad/informes):
        // agregar un indicador de carga al buscador cuando searchNotes deje de ser
        // en memoria (backend real) — hoy el filtrado es instantáneo, así que un
        // spinner falso sería peor UX que no mostrar nada.
        val filtered = searchNotes(notes, filter)

        // Criterio de aceptación del issue #16: "se distingue sin resultados
        // de sin apuntes todavía" — la causa (nada fotografiado vs. filtro
        // demasiado estricto) importa para el mensaje que ve el estudiante.
        // Una lista vacía porque la consulta falló no es "sin apuntes": se dice el motivo. Si ya había una lista
        // buena, el repositorio la sigue entregando y se muestra tal cual.
        val status = when {
            notes.isEmpty() && loadError != null -> NotesStatus.Error(loadError.message)
            notes.isEmpty() -> NotesStatus.EmptySinApuntes
            filtered.isEmpty() -> NotesStatus.EmptySinResultados
            else -> NotesStatus.Content(filtered)
        }

        val moduleOptions = notes
            .filter { it.moduleId != null && it.moduleLabel != null }
            .distinctBy { it.moduleId }
            .map { ModuleOption(moduleId = it.moduleId!!, label = it.moduleLabel!!) }
            .sortedBy { it.label }

        return LibraryUiState(query = debouncedQuery, filter = filter, notes = status, moduleOptions = moduleOptions)
    }
}
