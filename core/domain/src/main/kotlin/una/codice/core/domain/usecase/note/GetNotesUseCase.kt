package una.codice.core.domain.usecase.note

import kotlinx.coroutines.flow.Flow
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Note
import una.codice.core.domain.repository.NoteRepository

/**
 * Punto de entrada de dominio para observar los apuntes de un estudiante.
 *
 * Es una envoltura delgada sobre [NoteRepository.observeNotes] — el
 * ordenamiento y el filtrado NO viven aquí, sino en
 * [SearchNotesUseCase], para que `LibraryViewModel` (issue #15) pueda
 * combinar este `Flow` con el de la búsqueda/filtros activos usando
 * `combine()`, sin que este caso de uso le imponga ya un orden fijo.
 */
class GetNotesUseCase(
    private val noteRepository: NoteRepository,
) {
    operator fun invoke(studentId: String, groupId: String? = null): Flow<List<Note>> =
        noteRepository.observeNotes(studentId, groupId)

    /** Por qué falló la última carga de apuntes, o `null` si salió bien (ver [NoteRepository.loadError]). */
    val loadError: Flow<DomainError?> get() = noteRepository.loadError

    /** Vuelve a pedir los apuntes al servidor. */
    fun refresh() = noteRepository.refreshNotes()
}
