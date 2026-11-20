package una.codice.core.domain.usecase.note

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Note
import una.codice.core.domain.repository.NoteRepository
import una.codice.core.domain.result.DomainResult

/**
 * [IA-01] Envía la imagen o documento del apunte/tarea a Google Cloud AI para realizar
 * el escaneo OCR real y transcripción de texto, código o ecuaciones matemáticas.
 */
class ExtractNoteFromImageUseCase(
    private val noteRepository: NoteRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        imageBytes: ByteArray,
        filename: String = "apunte.jpg",
    ): DomainResult<Note> {
        if (imageBytes.isEmpty()) {
            return DomainResult.Failure(DomainError.Validation(DomainError.Field.PASSWORD, "El archivo o foto está vacío"))
        }
        return noteRepository.extractNoteFromImage(groupId, imageBytes, filename)
    }
}
