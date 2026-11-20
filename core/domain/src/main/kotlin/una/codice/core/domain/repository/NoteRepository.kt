package una.codice.core.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.model.TranscriptionBlock
import una.codice.core.domain.result.DomainResult

/**
 * El contrato de Biblioteca: entrega los apuntes del estudiante como un
 * `Flow`, y permite transcribir, guardar y auditar apuntes ([IA-03], [IA-04]).
 */
interface NoteRepository {

    /**
     * Los apuntes del estudiante, tal como los tiene el servidor. La lista se vuelve a pedir cada vez que se
     * guarda o se audita un apunte. [studentId] no viaja: el servidor usa el dueño del token.
     */
    fun observeNotes(studentId: String, groupId: String? = null): Flow<List<Note>>

    /**
     * Por qué falló la última consulta de [observeNotes], o `null` si salió bien. Existe para que una pantalla no
     * confunda "no se pudo cargar" con "todavía no hay apuntes": ante un fallo [observeNotes] sigue entregando la
     * última lista buena (vacía si nunca hubo una) y este flujo dice qué pasó.
     */
    val loadError: Flow<DomainError?> get() = flowOf(null)

    /** Vuelve a pedir la lista de apuntes, por ejemplo cuando el estudiante toca "Reintentar" tras un fallo. */
    fun refreshNotes() {}

    /**
     * [IA-03] Envía la foto del apunte al backend y devuelve el texto transcrito con la confianza de cada bloque.
     * Todavía no crea el apunte: el estudiante revisa el texto y lo guarda con [saveNote].
     */
    suspend fun extractNoteFromImage(
        groupId: String,
        imageBytes: ByteArray,
        filename: String,
    ): DomainResult<Note>

    /**
     * [IA-03] Transcribe la foto del apunte en bloques de texto con su confianza (`POST /api/v1/apuntes/transcripcion`).
     */
    suspend fun transcribeNote(
        noteId: String,
        imageBytes: ByteArray,
    ): DomainResult<List<TranscriptionBlock>>

    /**
     * Guarda en el servidor el apunte que el estudiante confirmó, en su matrícula aprobada del grupo [groupId].
     * Devuelve el apunte con su id real. Guardar dos veces el mismo borrador (mismo `note.id`) no lo duplica.
     */
    suspend fun saveNote(groupId: String, note: Note): DomainResult<Note>

    /**
     * [IA-04] Audita el apunte guardado: la IA lo compara con el temario del curso y devuelve los subtemas que toca,
     * con su estado de cobertura. Solo se audita texto confirmado.
     */
    suspend fun auditNote(noteId: String): DomainResult<List<SubtopicCoverage>>

    /**
     * Borra del servidor un apunte del estudiante. El borrado es definitivo: no hay papelera ni deshacer, así que la
     * pantalla tiene que confirmar antes de llamar aquí.
     *
     * Borrar también deshace lo que el apunte aportaba al mapa de cobertura — el servidor recalcula los subtemas que
     * tocaba —, por eso la lista se vuelve a pedir igual que tras [saveNote] y [auditNote].
     */
    suspend fun deleteNote(noteId: String): DomainResult<Unit>
}
