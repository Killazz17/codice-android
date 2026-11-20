package una.codice.feature.notes.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.SyncStatus
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.auth.ObserveSessionUseCase
import una.codice.core.domain.usecase.note.DeleteNoteUseCase
import una.codice.core.domain.usecase.note.GetNotesUseCase
import una.codice.core.domain.usecase.note.SearchNotesUseCase

/**
 * ### `Dispatchers.setMain`
 *
 * `LibraryViewModel` lanza corrutinas en `viewModelScope`, que por defecto
 * corren en `Dispatchers.Main` — inexistente en un test JVM puro. Con
 * `StandardTestDispatcher` + `setMain`/`resetMain` se reemplaza ese
 * dispatcher por uno controlado a mano con `advanceUntilIdle()`, incluyendo
 * el retraso de [kotlinx.coroutines.flow.debounce] del buscador.
 */
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var noteRepository: FakeNoteRepository
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun antesDeCadaTest() {
        // Hay que fijar Dispatchers.Main ANTES de construir el ViewModel:
        // su `init {}` ya lanza una corrutina en viewModelScope al crearse.
        Dispatchers.setMain(testDispatcher)

        noteRepository = FakeNoteRepository()
        val authRepository = FakeAuthRepository(
            seed = Session(token = "t", userId = "estudiante-1", role = UserRole.STUDENT),
        )
        viewModel = LibraryViewModel(
            observeSession = ObserveSessionUseCase(authRepository),
            getNotes = GetNotesUseCase(noteRepository),
            searchNotes = SearchNotesUseCase(),
            courseSelection = FakeCourseSelectionRepository(),
            deleteNote = DeleteNoteUseCase(noteRepository),
        )
    }

    @After
    fun despuesDeCadaTest() {
        Dispatchers.resetMain()
    }

    private fun apunte(id: String, texto: String, moduleId: String? = "modulo-1") = Note(
        id = id,
        subtopicId = "subtema-1",
        moduleId = moduleId,
        moduleLabel = moduleId,
        subtopicCode = "1.1",
        subtopicTitle = "Subtema de prueba",
        confirmedText = texto,
        pageCount = 1,
        syncStatus = SyncStatus.SYNCED,
        createdAt = 0L,
    )

    // Sin apuntes en el repositorio: la Biblioteca está vacía de verdad,
    // no es un "sin resultados" de un filtro.
    @Test
    fun `sin apuntes del estudiante muestra EmptySinApuntes`() = runTest(testDispatcher) {
        noteRepository.seedNotes(emptyList())

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(NotesStatus.EmptySinApuntes, viewModel.state.value.notes)
    }

    @Test
    fun `con apuntes y sin filtro activo muestra todos en Content`() = runTest(testDispatcher) {
        noteRepository.seedNotes(listOf(apunte("1", "uno"), apunte("2", "dos")))

        testDispatcher.scheduler.advanceUntilIdle()

        val estado = viewModel.state.value.notes
        assertTrue(estado is NotesStatus.Content)
        assertEquals(2, (estado as NotesStatus.Content).notes.size)
    }

    // Criterio de aceptación del issue #15: "escribir rápido no dispara una
    // búsqueda por letra" — antes de que el debounce corra, el estado sigue
    // siendo el de la lista completa.
    @Test
    fun `escribir en el buscador no filtra hasta que pasa el debounce`() = runTest(testDispatcher) {
        noteRepository.seedNotes(listOf(apunte("1", "recursividad"), apunte("2", "corrutinas")))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChange("recursividad")
        // Sin avanzar el reloj todavía: el debounce de 300ms no corrió.
        val estadoInmediato = viewModel.state.value.notes
        assertTrue(estadoInmediato is NotesStatus.Content)
        assertEquals(2, (estadoInmediato as NotesStatus.Content).notes.size)

        testDispatcher.scheduler.advanceUntilIdle()

        val estadoFiltrado = viewModel.state.value.notes
        assertTrue(estadoFiltrado is NotesStatus.Content)
        assertEquals(listOf("1"), (estadoFiltrado as NotesStatus.Content).notes.map { it.id })
    }

    // Hay apuntes, pero el filtro no encuentra ninguno — distinto de "sin apuntes todavía".
    @Test
    fun `un filtro que no encuentra nada muestra EmptySinResultados, no EmptySinApuntes`() = runTest(testDispatcher) {
        noteRepository.seedNotes(listOf(apunte("1", "recursividad")))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChange("algo que no existe")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(NotesStatus.EmptySinResultados, viewModel.state.value.notes)
    }

    @Test
    fun `filtrar por modulo combina con el texto buscado`() = runTest(testDispatcher) {
        noteRepository.seedNotes(
            listOf(
                apunte("1", "recursividad", moduleId = "modulo-1"),
                apunte("2", "recursividad", moduleId = "modulo-2"),
            ),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onModuleFilter("modulo-1")
        viewModel.onQueryChange("recursividad")
        testDispatcher.scheduler.advanceUntilIdle()

        val estado = viewModel.state.value.notes
        assertTrue(estado is NotesStatus.Content)
        assertEquals(listOf("1"), (estado as NotesStatus.Content).notes.map { it.id })
    }

    // Los chips de módulo del issue #17 salen de TODOS los apuntes, no de la
    // lista ya filtrada — si no, activar un chip haría desaparecer al resto.
    @Test
    fun `moduleOptions se calcula sobre todos los apuntes, no sobre el filtrado`() = runTest(testDispatcher) {
        noteRepository.seedNotes(
            listOf(
                apunte("1", "recursividad", moduleId = "modulo-1"),
                apunte("2", "corrutinas", moduleId = "modulo-2"),
            ),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onModuleFilter("modulo-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("modulo-1", "modulo-2"), viewModel.state.value.moduleOptions.map { it.moduleId }.toSet())
    }

    // Una lista vacía porque falló la carga no es "sin apuntes": se muestra el motivo, no el vacío de verdad.
    @Test
    fun `si la carga falla y no hay apuntes muestra Error con el motivo`() = runTest(testDispatcher) {
        noteRepository.loadErrorState.value = DomainError.Network()
        noteRepository.seedNotes(emptyList())

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(NotesStatus.Error(DomainError.Network().message), viewModel.state.value.notes)
    }

    // Si el repositorio todavía tiene la última lista buena, la pantalla la sigue mostrando.
    @Test
    fun `si la carga falla pero hay apuntes anteriores sigue mostrando los apuntes`() = runTest(testDispatcher) {
        noteRepository.seedNotes(listOf(apunte("1", "uno")))
        noteRepository.loadErrorState.value = DomainError.Network()

        testDispatcher.scheduler.advanceUntilIdle()

        val estado = viewModel.state.value.notes
        assertTrue(estado is NotesStatus.Content)
        assertEquals(listOf("1"), (estado as NotesStatus.Content).notes.map { it.id })
    }

    @Test
    fun `reintentar vuelve a pedir los apuntes y al llegar la lista sale del error`() = runTest(testDispatcher) {
        noteRepository.loadErrorState.value = DomainError.Network()
        noteRepository.seedNotes(emptyList())
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.notes is NotesStatus.Error)

        viewModel.onRetry()
        assertEquals(1, noteRepository.refreshCount)

        // El servidor ahora sí responde.
        noteRepository.loadErrorState.value = null
        noteRepository.seedNotes(listOf(apunte("1", "uno")))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.notes is NotesStatus.Content)
    }

    @Test
    fun `limpiar filtros no borra el texto buscado`() = runTest(testDispatcher) {
        noteRepository.seedNotes(listOf(apunte("1", "recursividad", moduleId = "modulo-1")))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onModuleFilter("modulo-1")
        viewModel.onQueryChange("recursividad")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onClearFilters()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("recursividad", viewModel.state.value.query)
        assertEquals(null, viewModel.state.value.filter.moduleId)
    }

    // --- Borrar un apunte ---

    @Test
    fun `pedir borrar abre la confirmacion sin borrar nada`() = runTest(testDispatcher) {
        val apunte = apunte("1", "uno")
        noteRepository.seedNotes(listOf(apunte))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDeleteRequested(apunte)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(apunte, viewModel.state.value.delete.pending)
        assertEquals(null, noteRepository.deletedNoteId)
        assertTrue(viewModel.state.value.notes is NotesStatus.Content)
    }

    @Test
    fun `confirmar borra el apunte y lo saca de la lista`() = runTest(testDispatcher) {
        val uno = apunte("1", "uno")
        noteRepository.seedNotes(listOf(uno, apunte("2", "dos")))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDeleteRequested(uno)
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("1", noteRepository.deletedNoteId)
        // El diálogo se cierra solo: sin apunte pendiente, sin error y sin quedarse en "Borrando…".
        assertEquals(DeleteState(), viewModel.state.value.delete)
        val restantes = (viewModel.state.value.notes as NotesStatus.Content).notes
        assertEquals(listOf("2"), restantes.map { it.id })
    }

    @Test
    fun `cancelar no borra`() = runTest(testDispatcher) {
        val apunte = apunte("1", "uno")
        noteRepository.seedNotes(listOf(apunte))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDeleteRequested(apunte)
        viewModel.onDeleteDismissed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, noteRepository.deletedNoteId)
        assertEquals(null, viewModel.state.value.delete.pending)
    }

    // Si falla, el diálogo tiene que quedarse abierto con el motivo: cerrarlo obligaría al estudiante a buscar otra
    // vez el apunte en la lista solo para reintentar.
    @Test
    fun `si el borrado falla el dialogo sigue abierto con el motivo`() = runTest(testDispatcher) {
        val apunte = apunte("1", "uno")
        noteRepository.seedNotes(listOf(apunte))
        noteRepository.deleteResult = DomainResult.Failure(DomainError.Network())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDeleteRequested(apunte)
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        val delete = viewModel.state.value.delete
        assertEquals(apunte, delete.pending)
        assertEquals(DomainError.Network().message, delete.error)
        assertEquals(false, delete.deleting)
        // El apunte sigue en la lista: no se quitó "por adelantado" antes de que el servidor confirmara.
        assertEquals(listOf("1"), (viewModel.state.value.notes as NotesStatus.Content).notes.map { it.id })
    }
}
