package una.codice.core.data.network.note

import kotlinx.serialization.Serializable
import una.codice.core.data.network.isoInstantToEpochMillis
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.SyncStatus
import una.codice.core.domain.model.TranscriptionBlock

/** `PageResponse` del backend: una página de resultados y los totales para pedir la siguiente. */
@Serializable
data class PageDto<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

/** Un apunte tal como lo lista el backend (`NoteSummaryDTO`). Los ids son numéricos en el backend. */
@Serializable
data class NoteDto(
    val id: Long,
    val subtopicId: Long? = null,
    val syncStatus: String = "SYNCED",
    val audited: Boolean = false,
    /** Fecha ISO-8601 en UTC, por ejemplo `2026-10-12T15:00:00.123456Z`. */
    val createdAt: String,
    val confirmedText: String? = null,
    val pageCount: Int = 0,
    val subtopicCode: String? = null,
    val subtopicTitle: String? = null,
    val moduleId: Long? = null,
    val moduleLabel: String? = null,
)

/** La foto de un apunte transcrita por la IA del backend (`TranscriptionResultDTO`, IA-03). */
@Serializable
data class TranscriptionResultDto(
    val proveedor: String = "",
    val modelo: String = "",
    val promptVersion: String = "",
    val textoCompleto: String = "",
    val paginas: List<TranscriptionPageDto> = emptyList(),
    /** Cuántos bloques quedaron bajo el umbral de confianza (0,70) y el estudiante debe revisar. */
    val bloquesPorRevisar: Int = 0,
)

@Serializable
data class TranscriptionPageDto(
    val indice: Int = 0,
    val bloques: List<TranscriptionBlockDto> = emptyList(),
)

@Serializable
data class TranscriptionBlockDto(
    val texto: String,
    val confianza: Double = 0.0,
    val tipo: String = "parrafo",
    val requiereRevision: Boolean = true,
)

/** `NoteCreateDTO` del backend. Solo `enrollmentId` es obligatorio; sin `confirmedText` el apunte no se puede auditar. */
@Serializable
data class NoteCreateRequestDto(
    val enrollmentId: Long,
    val subtopicId: Long? = null,
    val clientRequestId: String? = null,
    val confirmedText: String? = null,
)

/** De la respuesta de crear un apunte (`NoteDTO`) la app solo necesita el id. */
@Serializable
data class CreatedNoteDto(
    val id: Long,
)

/** Los bloques de todas las páginas, en orden. `requiresReview` del dominio usa el mismo umbral que el backend. */
fun TranscriptionResultDto.toBlocks(): List<TranscriptionBlock> = paginas.flatMap { it.bloques }.map { block ->
    TranscriptionBlock(text = block.texto, confidence = block.confianza.toFloat(), type = block.tipo)
}

/**
 * El backend tiene cuatro estados de sincronización (QUEUED, UPLOADING, SYNCED, FAILED); la
 * Biblioteca solo distingue "ya está en el servidor" de "todavía pendiente".
 */
fun NoteDto.toDomain(): Note = Note(
    id = id.toString(),
    subtopicId = subtopicId?.toString(),
    moduleId = moduleId?.toString(),
    moduleLabel = moduleLabel,
    subtopicCode = subtopicCode,
    subtopicTitle = subtopicTitle,
    confirmedText = confirmedText.orEmpty(),
    pageCount = pageCount,
    syncStatus = if (syncStatus == "SYNCED") SyncStatus.SYNCED else SyncStatus.QUEUED,
    createdAt = isoInstantToEpochMillis(createdAt),
)
