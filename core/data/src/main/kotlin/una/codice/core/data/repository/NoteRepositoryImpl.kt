package una.codice.core.data.repository

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import una.codice.core.data.ai.AIProvider
import una.codice.core.data.network.apiCall
import una.codice.core.data.network.coverage.toDomain
import una.codice.core.data.network.enrollment.EnrollmentApiService
import una.codice.core.data.network.note.NoteApiService
import una.codice.core.data.network.note.NoteCreateRequestDto
import una.codice.core.data.network.note.toDomain
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.model.SyncStatus
import una.codice.core.domain.model.TranscriptionBlock
import una.codice.core.domain.repository.NoteRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.result.errorOrNull

/**
 * [NoteRepository] contra el backend de Códice, delegando la IA en [AIProvider] e [ImageTranscriptionStrategy].
 *
 * Los apuntes viven en el servidor: la lista se vuelve a pedir cada vez que se guarda o se audita uno, y no hay una
 * copia aparte en el teléfono que pueda quedar distinta de la del servidor.
 */
@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteApiService: NoteApiService,
    private val enrollmentApiService: EnrollmentApiService,
    private val aiProvider: AIProvider,
) : NoteRepository {

    private val refresh = MutableStateFlow(0)

    private val loadErrorState = MutableStateFlow<DomainError?>(null)

    // La última lista que el servidor entregó completa, por curso (la clave vacía es "todos"). Si una consulta falla se
    // sigue entregando esta, en vez de vaciar la pantalla como si el estudiante no tuviera apuntes.
    private val lastNotes = ConcurrentHashMap<String, List<Note>>()

    override val loadError: Flow<DomainError?> = loadErrorState

    override fun refreshNotes() {
        refresh.value += 1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeNotes(studentId: String, groupId: String?): Flow<List<Note>> =
        refresh.flatMapLatest { flow { emit(fetchNotes(groupId)) } }

    private suspend fun fetchNotes(groupId: String?): List<Note> {
        val key = groupId.orEmpty()
        val notes = mutableListOf<Note>()
        var page = 0
        do {
            val result = when (
                val response = apiCall(onHttpError = ::listError) {
                    noteApiService.getNotes(page = page, size = PAGE_SIZE, groupId = groupId?.toLongOrNull())
                }
            ) {
                is DomainResult.Success -> response.data
                is DomainResult.Failure -> {
                    loadErrorState.value = response.error
                    return lastNotes[key].orEmpty()
                }
            }
            notes += result.content.map { it.toDomain() }
            page++
        } while (page < result.totalPages)

        lastNotes[key] = notes
        loadErrorState.value = null
        return notes
    }

    override suspend fun extractNoteFromImage(
        groupId: String,
        imageBytes: ByteArray,
        filename: String,
    ): DomainResult<Note> {
        val strategy = aiProvider.getStrategy("gemini")
        return strategy.extractNote(groupId, imageBytes, filename)
    }

    override suspend fun transcribeNote(
        noteId: String,
        imageBytes: ByteArray,
    ): DomainResult<List<TranscriptionBlock>> {
        val strategy = aiProvider.getStrategy("gemini")
        return strategy.transcribe(imageBytes, "apunte_$noteId.jpg")
    }

    override suspend fun saveNote(groupId: String, note: Note): DomainResult<Note> {
        val group = groupId.toLongOrNull()
            ?: return DomainResult.Failure(DomainError.Unknown("No se pudo identificar el curso del apunte"))

        // El apunte cuelga de la matrícula, no del grupo: se busca la aprobada del estudiante en ese grupo.
        val enrollments = when (val result = apiCall { enrollmentApiService.getMyEnrollments() }) {
            is DomainResult.Success -> result.data
            is DomainResult.Failure -> return DomainResult.Failure(result.error)
        }
        val enrollment = enrollments.firstOrNull { it.groupId == group && it.isActive }
            ?: return DomainResult.Failure(DomainError.Unknown("No tienes una matrícula aprobada en este curso"))

        val request = NoteCreateRequestDto(
            enrollmentId = enrollment.id,
            subtopicId = note.subtopicId?.toLongOrNull(),
            // El mismo borrador siempre manda el mismo id: un doble toque o un reintento no crea dos apuntes.
            clientRequestId = "app-${note.id}",
            confirmedText = note.confirmedText.takeIf { it.isNotBlank() },
        )
        return when (val result = apiCall(onHttpError = ::saveError) { noteApiService.createNote(request) }) {
            is DomainResult.Success -> {
                refresh.value += 1
                DomainResult.Success(note.copy(id = result.data.id.toString(), syncStatus = SyncStatus.SYNCED))
            }
            is DomainResult.Failure -> DomainResult.Failure(result.error)
        }
    }

    override suspend fun auditNote(noteId: String): DomainResult<List<SubtopicCoverage>> {
        val id = noteId.toLongOrNull()
            ?: return DomainResult.Failure(DomainError.Unknown("El apunte todavía no se guardó en el servidor"))

        return when (val result = apiCall(onHttpError = ::auditError) { noteApiService.auditNote(id) }) {
            is DomainResult.Success -> {
                refresh.value += 1
                DomainResult.Success(result.data.map { it.toDomain() })
            }
            is DomainResult.Failure -> DomainResult.Failure(result.error)
        }
    }

    override suspend fun deleteNote(noteId: String): DomainResult<Unit> {
        val id = noteId.toLongOrNull()
            ?: return DomainResult.Failure(DomainError.Unknown("El apunte todavía no se guardó en el servidor"))

        val result = apiCall(onHttpError = ::deleteError) { noteApiService.deleteNote(id) }

        // Un 404 es "ya no está", que es justo lo que el estudiante pidió: se trata como éxito en vez de hacerle
        // reintentar un borrado sin nada que borrar. Pasa cuando el apunte se borró desde otro dispositivo.
        val borrado = result is DomainResult.Success || result.errorOrNull() == YA_NO_EXISTE
        if (!borrado) return DomainResult.Failure((result as DomainResult.Failure).error)

        // Igual que al guardar o auditar: el servidor recalculó la cobertura, así que la lista se vuelve a pedir en vez
        // de quitar el apunte de `lastNotes` a mano.
        refresh.value += 1
        return DomainResult.Success(Unit)
    }

    private fun listError(code: Int): DomainError? = when (code) {
        401 -> DomainError.Unauthorized()
        else -> null
    }

    private fun saveError(code: Int): DomainError? = when (code) {
        400 -> DomainError.Unknown("El apunte no se pudo guardar: revisa el texto")
        403 -> DomainError.Unknown("No puedes guardar apuntes en este curso: revisa que tu matrícula esté aprobada")
        else -> null
    }

    private fun auditError(code: Int): DomainError? = when (code) {
        403 -> DomainError.Unknown("Solo el dueño del apunte puede auditarlo")
        404 -> DomainError.Unknown("No se encontró el apunte en el servidor")
        422 -> DomainError.Unknown("El apunte no tiene texto confirmado")
        503 -> DomainError.Unknown("La IA no está disponible en este momento")
        else -> null
    }

    private fun deleteError(code: Int): DomainError? = when (code) {
        403 -> DomainError.Unknown("Solo el dueño del apunte puede borrarlo")
        404 -> YA_NO_EXISTE
        else -> null
    }

    private companion object {
        const val PAGE_SIZE = 100

        /**
         * El 404 al borrar. [deleteNote] lo reconoce por valor (`DomainError.Unknown` es una `data class`) para
         * tratarlo como éxito, así que este error nunca llega a la pantalla.
         */
        val YA_NO_EXISTE = DomainError.Unknown("Ese apunte ya no existe")
    }
}
