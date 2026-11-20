package una.codice.core.domain.usecase.note

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.SyncStatus
import una.codice.core.domain.model.TranscriptionBlock
import una.codice.core.domain.result.DomainResult

class ExtractNoteFromPagesUseCaseTest {

    private val repository = FakeNoteRepository()
    private val useCase = ExtractNoteFromPagesUseCase(ExtractNoteFromImageUseCase(repository))

    private fun pagina(texto: String, bloques: List<TranscriptionBlock> = emptyList()) = Note(
        id = "",
        subtopicId = null,
        moduleId = null,
        moduleLabel = null,
        subtopicCode = null,
        subtopicTitle = null,
        confirmedText = texto,
        pageCount = 1,
        syncStatus = SyncStatus.QUEUED,
        createdAt = 100L,
        transcription = bloques,
    )

    private val foto = byteArrayOf(1, 2, 3)

    @Test
    fun `una sola pagina devuelve su apunte tal cual`() = runTest {
        val unica = pagina("Solo una página", listOf(TranscriptionBlock("Solo una página", 0.9f)))
        repository.extractHandler = { DomainResult.Success(unica) }

        val resultado = useCase("g1", listOf(foto))

        assertEquals(DomainResult.Success(unica), resultado)
    }

    @Test
    fun `varias paginas se transcriben en orden con el nombre de cada pagina`() = runTest {
        repository.extractHandler = { DomainResult.Success(pagina("texto de $it")) }

        useCase("g1", listOf(foto, foto, foto))

        assertEquals(listOf("pagina_1.jpg", "pagina_2.jpg", "pagina_3.jpg"), repository.extractedFilenames)
    }

    @Test
    fun `junta el texto de las paginas y sus bloques en el mismo orden`() = runTest {
        val bloquesUno = listOf(TranscriptionBlock("uno", 0.95f), TranscriptionBlock("dudoso", 0.4f))
        val bloquesDos = listOf(TranscriptionBlock("dos", 0.9f))
        repository.extractHandler = { nombre ->
            if (nombre == "pagina_1.jpg") {
                DomainResult.Success(pagina("uno dudoso", bloquesUno))
            } else {
                DomainResult.Success(pagina("dos", bloquesDos))
            }
        }

        val apunte = (useCase("g1", listOf(foto, foto)) as DomainResult.Success).data

        assertEquals("uno dudoso\n\ndos", apunte.confirmedText)
        assertEquals(2, apunte.pageCount)
        assertEquals(bloquesUno + bloquesDos, apunte.transcription)
        // El apunte juntado hereda que hay un bloque dudoso en alguna de sus páginas.
        assertTrue(apunte.requiresReview)
    }

    @Test
    fun `una pagina en blanco no deja lineas vacias en el texto`() = runTest {
        repository.extractHandler = { nombre ->
            DomainResult.Success(pagina(if (nombre == "pagina_1.jpg") "  " else "texto"))
        }

        val apunte = (useCase("g1", listOf(foto, foto)) as DomainResult.Success).data

        assertEquals("texto", apunte.confirmedText)
    }

    @Test
    fun `si una pagina falla se detiene y dice cual fue`() = runTest {
        repository.extractHandler = { nombre ->
            if (nombre == "pagina_2.jpg") {
                DomainResult.Failure(DomainError.Unknown("La IA no está disponible en este momento"))
            } else {
                DomainResult.Success(pagina("texto"))
            }
        }

        val resultado = useCase("g1", listOf(foto, foto, foto))

        assertEquals(
            DomainResult.Failure(DomainError.Unknown("Página 2 de 3: La IA no está disponible en este momento")),
            resultado,
        )
        // La tercera página no se manda a la IA.
        assertEquals(listOf("pagina_1.jpg", "pagina_2.jpg"), repository.extractedFilenames)
    }

    @Test
    fun `con una sola pagina el error llega sin prefijo`() = runTest {
        repository.extractHandler = { DomainResult.Failure(DomainError.Network()) }

        assertEquals(DomainResult.Failure(DomainError.Network()), useCase("g1", listOf(foto)))
    }

    @Test
    fun `sin paginas no llama a la IA`() = runTest {
        val resultado = useCase("g1", emptyList())

        assertEquals(DomainResult.Failure(DomainError.Unknown("No hay páginas para transcribir")), resultado)
        assertTrue(repository.extractedFilenames.isEmpty())
    }

    @Test
    fun `una pagina vacia se rechaza antes de llamar a la IA`() = runTest {
        val resultado = useCase("g1", listOf(byteArrayOf()))

        assertTrue(resultado is DomainResult.Failure)
        assertTrue(repository.extractedFilenames.isEmpty())
    }
}
