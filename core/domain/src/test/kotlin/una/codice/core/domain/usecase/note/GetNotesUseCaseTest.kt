package una.codice.core.domain.usecase.note

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.SyncStatus

class GetNotesUseCaseTest {

    private val repository = FakeNoteRepository()
    private val useCase = GetNotesUseCase(repository)

    private fun apunte(id: String) = Note(
        id = id,
        subtopicId = null,
        moduleId = null,
        moduleLabel = null,
        subtopicCode = null,
        subtopicTitle = null,
        confirmedText = "texto de $id",
        pageCount = 1,
        syncStatus = SyncStatus.SYNCED,
        createdAt = 0L,
    )

    // Protege que GetNotesUseCase de verdad reenvíe lo que entrega el repositorio,
    // sin perder ni transformar nada — es solo un punto de entrada al Flow.
    @Test
    fun `reenvia los apuntes que entrega el repositorio`() = runTest {
        val apuntes = listOf(apunte("1"), apunte("2"))
        repository.seedNotes(apuntes)

        val resultado = useCase("estudiante-1").first()

        assertEquals(apuntes, resultado)
    }
}
