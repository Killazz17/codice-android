package una.codice.core.domain.usecase.note

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.TranscriptionBlock
import una.codice.core.domain.repository.NoteRepository
import una.codice.core.domain.result.DomainResult

/**
 * [IA-03] Caso de uso para la transcripción de apuntes con IA.
 * Incluye validación de imagen, reintento único en caso de fallo de IA,
 * y respaldo a transcripción manual si el reintento también falla.
 */
class TranscribeNoteUseCase(
    private val noteRepository: NoteRepository,
) {
    suspend operator fun invoke(
        noteId: String,
        imageBytes: ByteArray,
    ): DomainResult<List<TranscriptionBlock>> {
        if (noteId.isBlank()) {
            return DomainResult.Failure(DomainError.Validation(DomainError.Field.PASSWORD, "El ID del apunte es inválido"))
        }
        if (imageBytes.isEmpty()) {
            return DomainResult.Failure(DomainError.Validation(DomainError.Field.PASSWORD, "La imagen del apunte es inválida o está vacía"))
        }

        // Intento 1
        val firstAttempt = noteRepository.transcribeNote(noteId, imageBytes)
        if (firstAttempt is DomainResult.Success) {
            return firstAttempt
        }

        // Reintento único (1 retry)
        val retryAttempt = noteRepository.transcribeNote(noteId, imageBytes)
        if (retryAttempt is DomainResult.Success) {
            return retryAttempt
        }

        // Fallback a transcripción manual si la IA falla tras el reintento
        val manualFallback = listOf(
            TranscriptionBlock(
                text = "[Transcripción manual requerida: IA no disponible]",
                confidence = 0.0f,
                type = "manual_fallback"
            )
        )
        return DomainResult.Success(manualFallback)
    }
}
