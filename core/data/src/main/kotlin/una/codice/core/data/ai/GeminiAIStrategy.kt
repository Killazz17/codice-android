package una.codice.core.data.ai

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import una.codice.core.data.network.apiCall
import una.codice.core.data.network.note.NoteApiService
import una.codice.core.data.network.note.TranscriptionResultDto
import una.codice.core.data.network.note.toBlocks
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.SyncStatus
import una.codice.core.domain.model.TranscriptionBlock
import una.codice.core.domain.result.DomainResult

/**
 * Implementación de [ImageTranscriptionStrategy] que usa la IA del backend de Códice
 * (`POST /api/v1/apuntes/transcripcion`).
 *
 * El proveedor de IA lo decide el backend (variable `AI_PROVIDER`; hoy Gemini): la app solo manda la foto y nunca
 * lleva una llave. Si la IA falla, el error llega tal cual a la pantalla: no se inventa una transcripción.
 */
@Singleton
class GeminiAIStrategy @Inject constructor(
    private val noteApiService: NoteApiService,
) : ImageTranscriptionStrategy {

    override suspend fun transcribe(imageBytes: ByteArray, filename: String): DomainResult<List<TranscriptionBlock>> =
        when (val result = transcribePhoto(imageBytes, filename)) {
            is DomainResult.Success -> DomainResult.Success(result.data.toBlocks())
            is DomainResult.Failure -> DomainResult.Failure(result.error)
        }

    /**
     * El apunte todavía no existe en el servidor: aquí solo se devuelve el texto transcrito y sus bloques para que el
     * estudiante lo revise. El backend no clasifica el subtema, así que queda sin clasificar hasta que lo elija.
     */
    override suspend fun extractNote(groupId: String, imageBytes: ByteArray, filename: String): DomainResult<Note> =
        when (val result = transcribePhoto(imageBytes, filename)) {
            is DomainResult.Success -> DomainResult.Success(
                Note(
                    id = "",
                    subtopicId = null,
                    moduleId = null,
                    moduleLabel = null,
                    subtopicCode = null,
                    subtopicTitle = null,
                    confirmedText = result.data.textoCompleto,
                    pageCount = 1,
                    syncStatus = SyncStatus.QUEUED,
                    createdAt = System.currentTimeMillis(),
                    transcription = result.data.toBlocks(),
                ),
            )
            is DomainResult.Failure -> DomainResult.Failure(result.error)
        }

    private suspend fun transcribePhoto(imageBytes: ByteArray, filename: String): DomainResult<TranscriptionResultDto> {
        val body = imageBytes.toRequestBody(mediaTypeOf(filename, imageBytes))
        val part = MultipartBody.Part.createFormData("file", filename, body)
        return apiCall(onHttpError = ::transcriptionError) { noteApiService.transcribePhoto(part) }
    }

    private fun transcriptionError(code: Int): DomainError? = when (code) {
        400 -> DomainError.Unknown("El archivo no es una foto válida. Usa una imagen JPEG, PNG o WebP (no se aceptan fotos HEIC)")
        413 -> DomainError.Unknown("La foto es muy pesada (máximo 10 MB)")
        503 -> DomainError.Unknown("La IA no está disponible en este momento. Intenta de nuevo o escribe el texto a mano")
        else -> null
    }
}

/**
 * El tipo que se declara al backend. Manda el CONTENIDO: los primeros bytes dicen qué formato es de verdad. El nombre no
 * sirve de fuente única porque el selector de la galería entrega direcciones sin extensión (`.../media/1000000034`), y
 * declarar JPEG para un PNG o un WebP hacía que el servidor rechazara una foto perfectamente buena.
 * Solo si los bytes no se reconocen se mira la extensión; sin ella se asume JPEG (el formato de la cámara).
 */
internal fun mediaTypeOf(filename: String, bytes: ByteArray): MediaType =
    (sniffMediaType(bytes) ?: when (filename.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        else -> "image/jpeg"
    }).toMediaType()

private fun sniffMediaType(bytes: ByteArray): String? = when {
    bytes.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
    bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> "image/png"
    // WebP: "RIFF" + 4 bytes de tamaño + "WEBP".
    bytes.size >= 12 && bytes.startsWith(0x52, 0x49, 0x46, 0x46) && String(bytes, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> "image/webp"
    bytes.startsWith(0x25, 0x50, 0x44, 0x46) -> "application/pdf"
    else -> null
}

private fun ByteArray.startsWith(vararg signature: Int): Boolean =
    size >= signature.size && signature.indices.all { this[it] == signature[it].toByte() }
