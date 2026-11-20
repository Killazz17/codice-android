package una.codice.core.domain.usecase.note

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.model.SyncStatus
import una.codice.core.domain.model.TranscriptionBlock
import una.codice.core.domain.repository.NoteRepository
import una.codice.core.domain.result.DomainResult

/**
 * Implementación falsa de [NoteRepository] para los tests: guarda los
 * apuntes en memoria y simula respuestas de IA para transcripciones ([IA-03]).
 */
class FakeNoteRepository : NoteRepository {

    private val notes = MutableStateFlow<List<Note>>(emptyList())
    var transcribeResult: DomainResult<List<TranscriptionBlock>> = DomainResult.Success(emptyList())
    var transcribeCallCount = 0

    override fun observeNotes(studentId: String, groupId: String?): Flow<List<Note>> = notes

    /** Los nombres de archivo que se pidieron transcribir, en orden. */
    val extractedFilenames = mutableListOf<String>()

    /** Si se fija, decide qué devuelve [extractNoteFromImage] según el nombre del archivo. */
    var extractHandler: ((filename: String) -> DomainResult<Note>)? = null

    override suspend fun extractNoteFromImage(
        groupId: String,
        imageBytes: ByteArray,
        filename: String,
    ): DomainResult<Note> {
        extractedFilenames += filename
        extractHandler?.let { return it(filename) }
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
        transcribeCallCount++
        return transcribeResult
    }

    override suspend fun saveNote(groupId: String, note: Note): DomainResult<Note> {
        notes.value = listOf(note) + notes.value
        return DomainResult.Success(note)
    }

    override suspend fun auditNote(noteId: String): DomainResult<List<SubtopicCoverage>> =
        DomainResult.Success(emptyList())

    /** El id del último apunte que se pidió borrar, o `null` si no se pidió ninguno. */
    var deletedNoteId: String? = null

    /** Si se fija, es lo que devuelve [deleteNote] en vez de un éxito. */
    var deleteResult: DomainResult<Unit>? = null

    override suspend fun deleteNote(noteId: String): DomainResult<Unit> {
        deletedNoteId = noteId
        deleteResult?.let { return it }
        notes.value = notes.value.filterNot { it.id == noteId }
        return DomainResult.Success(Unit)
    }

    /** Precarga los apuntes que va a devolver [observeNotes], sin importar el `studentId` pedido. */
    fun seedNotes(value: List<Note>) {
        notes.value = value
    }
}
