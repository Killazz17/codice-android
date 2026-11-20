package una.codice.core.domain.model

data class Note(
    val id: String,
    val subtopicId: String?,
    val moduleId: String?,
    val moduleLabel: String?,
    val subtopicCode: String?,
    val subtopicTitle: String?,
    val confirmedText: String,
    val pageCount: Int,
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val transcription: List<TranscriptionBlock> = emptyList(),
    val requiresReview: Boolean = transcription.any { it.requiresReview },
)
