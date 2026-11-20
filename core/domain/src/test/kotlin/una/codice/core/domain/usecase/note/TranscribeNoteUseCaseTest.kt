package una.codice.core.domain.usecase.note

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.TranscriptionBlock
import una.codice.core.domain.result.DomainResult

class TranscribeNoteUseCaseTest {

    private val repository = FakeNoteRepository()
    private val useCase = TranscribeNoteUseCase(repository)

    @Test
    fun `imagen invalida o vacia devuelve error de validacion`() = runTest {
        val resultado = useCase("note-1", byteArrayOf())
        assertTrue(resultado is DomainResult.Failure)
        assertTrue((resultado as DomainResult.Failure).error is DomainError.Validation)
    }

    @Test
    fun `bloques con confianza sobre y bajo el umbral son procesados correctamente`() = runTest {
        val bloques = listOf(
            TranscriptionBlock("Texto claro", 0.95f, "text"),
            TranscriptionBlock("Texto dudoso o manuscrito", 0.55f, "text") // < 0.70 (requiresReview = true)
        )
        repository.transcribeResult = DomainResult.Success(bloques)

        val resultado = useCase("note-1", byteArrayOf(1, 2, 3))
        assertTrue(resultado is DomainResult.Success)
        val data = (resultado as DomainResult.Success).data
        assertEquals(2, data.size)
        assertTrue(data[0].confidence >= 0.70f)
        assertTrue(data[1].requiresReview)
    }

    @Test
    fun `si la IA falla en primer intento reintenta una vez y si falla aplica respaldo manual`() = runTest {
        repository.transcribeResult = DomainResult.Failure(DomainError.Unknown("AI Service Unavailable"))

        val resultado = useCase("note-1", byteArrayOf(1, 2, 3))
        // Debe haber intentado 2 veces (1 intento + 1 reintento)
        assertEquals(2, repository.transcribeCallCount)

        assertTrue(resultado is DomainResult.Success)
        val data = (resultado as DomainResult.Success).data
        assertEquals(1, data.size)
        assertEquals("manual_fallback", data[0].type)
    }
}
