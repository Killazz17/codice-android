package una.codice.feature.notes.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.model.SyncStatus
import una.codice.core.domain.model.TranscriptionBlock
import una.codice.core.domain.repository.NoteRepository
import una.codice.core.domain.result.DomainResult

class FakeNoteRepository : NoteRepository {

    private val notes = MutableStateFlow<List<Note>>(emptyList())

    override fun observeNotes(studentId: String, groupId: String?): Flow<List<Note>> = notes

    /** Lo que dice [loadError]: `null` si la última carga salió bien. */
    val loadErrorState = MutableStateFlow<DomainError?>(null)
    override val loadError: Flow<DomainError?> = loadErrorState

    var refreshCount = 0

    override fun refreshNotes() {
        refreshCount++
    }

    /** Si se fija, es lo que devuelve [extractNoteFromImage] en vez de la nota de ejemplo. */
    var extractResult: DomainResult<Note>? = null

    /** Si se fija, decide qué devuelve [extractNoteFromImage] según el nombre del archivo (una respuesta por página). */
    var extractHandler: ((filename: String) -> DomainResult<Note>)? = null

    /** Los nombres de archivo que se pidieron transcribir, en orden. */
    val extractedFilenames = mutableListOf<String>()

    override suspend fun extractNoteFromImage(
        groupId: String,
        imageBytes: ByteArray,
        filename: String,
    ): DomainResult<Note> {
        extractedFilenames += filename
        extractHandler?.let { return it(filename) }
        extractResult?.let { return it }
        val note = Note(
            id = "1",
            subtopicId = "sub-1",
            moduleId = "mod-1",
            moduleLabel = "M1",
            subtopicCode = "4.3",
            subtopicTitle = "Manejo de errores",
            confirmedText = "Texto OCR real",
            pageCount = 1,
            syncStatus = SyncStatus.SYNCED,
            createdAt = System.currentTimeMillis()
        )
        return DomainResult.Success(note)
    }

    override suspend fun transcribeNote(
        noteId: String,
        imageBytes: ByteArray,
    ): DomainResult<List<TranscriptionBlock>> {
        return DomainResult.Success(emptyList())
    }

    /** Lo último que pidió guardar: el grupo y el borrador. */
    var savedGroupId: String? = null
    var savedDraft: Note? = null
    var saveResult: DomainResult<Note>? = null
    var saveCallCount = 0

    override suspend fun saveNote(groupId: String, note: Note): DomainResult<Note> {
        saveCallCount++
        savedGroupId = groupId
        savedDraft = note
        val resultado = saveResult ?: DomainResult.Success(note.copy(id = "77", syncStatus = SyncStatus.SYNCED))
        if (resultado is DomainResult.Success) notes.value = listOf(resultado.data) + notes.value
        return resultado
    }

    var auditResult: DomainResult<List<SubtopicCoverage>> = DomainResult.Success(emptyList())
    var auditedNoteId: String? = null

    override suspend fun auditNote(noteId: String): DomainResult<List<SubtopicCoverage>> {
        auditedNoteId = noteId
        return auditResult
    }

    /** El id del último apunte que se pidió borrar, o `null` si no se pidió ninguno. */
    var deletedNoteId: String? = null

    /** Si se fija, es lo que devuelve [deleteNote] en vez de un éxito. */
    var deleteResult: DomainResult<Unit>? = null

    override suspend fun deleteNote(noteId: String): DomainResult<Unit> {
        deletedNoteId = noteId
        deleteResult?.let { return it }
        // El repositorio real vuelve a pedir la lista al servidor; aquí basta con quitarlo de la que se está emitiendo.
        notes.value = notes.value.filterNot { it.id == noteId }
        return DomainResult.Success(Unit)
    }

    fun seedNotes(value: List<Note>) {
        notes.value = value
    }
}
