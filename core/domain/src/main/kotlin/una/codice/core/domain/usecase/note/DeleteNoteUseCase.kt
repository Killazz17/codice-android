package una.codice.core.domain.usecase.note

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.repository.NoteRepository
import una.codice.core.domain.result.DomainResult

/**
 * Borra un apunte del estudiante.
 *
 * ### Por qué la confirmación no vive aquí
 *
 * El borrado es definitivo y el servidor recalcula la cobertura del subtema que el apunte tocaba, así que hay que
 * preguntar antes. Pero preguntar es una decisión de interfaz — un diálogo, un deslizamiento con deshacer, un menú
 * largo —, y por eso vive en `LibraryViewModel`/`LibraryScreen`. Este caso de uso asume que ya se confirmó: si lo
 * invocas, se borra.
 *
 * Lo único que sí es regla de negocio, y por eso está aquí, es que un apunte sin guardar no se puede borrar: todavía
 * no existe en el servidor y no hay nada que pedirle.
 */
class DeleteNoteUseCase(
    private val noteRepository: NoteRepository,
) {
    suspend operator fun invoke(noteId: String): DomainResult<Unit> {
        if (noteId.isBlank()) {
            return DomainResult.Failure(DomainError.Unknown("El apunte no se puede borrar: no tiene identificador"))
        }
        return noteRepository.deleteNote(noteId)
    }
}
