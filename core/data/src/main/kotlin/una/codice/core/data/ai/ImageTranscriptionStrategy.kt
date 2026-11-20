package una.codice.core.data.ai

import una.codice.core.domain.model.Note
import una.codice.core.domain.model.TranscriptionBlock
import una.codice.core.domain.result.DomainResult

/**
 * [IA-03 / Strategy Pattern] Interfaz estratégica para la transcripción y escaneo de imágenes de apuntes por IA.
 * Permite intercambiar o soportar múltiples proveedores de IA (Gemini, Google Cloud Vision, etc.).
 */
interface ImageTranscriptionStrategy {
    suspend fun transcribe(imageBytes: ByteArray, filename: String): DomainResult<List<TranscriptionBlock>>
    suspend fun extractNote(groupId: String, imageBytes: ByteArray, filename: String): DomainResult<Note>
}
