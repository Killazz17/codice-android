package una.codice.core.domain.usecase.note

import org.junit.Assert.assertEquals
import org.junit.Test
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.SyncStatus

class SearchNotesUseCaseTest {

    private val useCase = SearchNotesUseCase()

    private fun apunte(
        id: String,
        texto: String,
        moduleId: String? = "modulo-1",
        subtopicId: String? = "subtema-1",
        syncStatus: SyncStatus = SyncStatus.SYNCED,
    ) = Note(
        id = id,
        subtopicId = subtopicId,
        moduleId = moduleId,
        moduleLabel = moduleId,
        subtopicCode = subtopicId,
        subtopicTitle = subtopicId,
        confirmedText = texto,
        pageCount = 1,
        syncStatus = syncStatus,
        createdAt = 0L,
    )

    // Criterio de aceptación del issue #16: "Buscar algoritmo encuentra
    // también Algoritmo y algorítmo" — la búsqueda ignora mayúsculas y acentos.
    @Test
    fun `la busqueda ignora mayusculas y acentos`() {
        val apuntes = listOf(apunte("1", "El algorítmo de Dijkstra"))

        val resultado = useCase(apuntes, NoteFilter(query = "algoritmo"))

        assertEquals(listOf("1"), resultado.map { it.id })
    }

    @Test
    fun `un filtro vacio no descarta ningun apunte`() {
        val apuntes = listOf(apunte("1", "uno"), apunte("2", "dos"))

        val resultado = useCase(apuntes, NoteFilter())

        assertEquals(apuntes, resultado)
    }

    // Criterio de aceptación del issue #16: "Combinar búsqueda con filtro de
    // módulo devuelve la intersección" — AND lógico, no OR.
    @Test
    fun `combina busqueda de texto con filtro de modulo`() {
        val apuntes = listOf(
            apunte("1", "recursividad", moduleId = "modulo-1"),
            apunte("2", "recursividad", moduleId = "modulo-2"),
            apunte("3", "corrutinas", moduleId = "modulo-1"),
        )

        val resultado = useCase(apuntes, NoteFilter(query = "recursividad", moduleId = "modulo-1"))

        assertEquals(listOf("1"), resultado.map { it.id })
    }

    @Test
    fun `filtra por estado de sincronizacion`() {
        val apuntes = listOf(
            apunte("1", "a", syncStatus = SyncStatus.SYNCED),
            apunte("2", "b", syncStatus = SyncStatus.QUEUED),
        )

        val resultado = useCase(apuntes, NoteFilter(syncStatus = SyncStatus.QUEUED))

        assertEquals(listOf("2"), resultado.map { it.id })
    }

    // Criterio de aceptación del issue #16: "Un apunte sin subtema aparece
    // solo cuando se activa soloSinClasificar".
    @Test
    fun `solo sin clasificar deja ver unicamente los apuntes sin subtema`() {
        val apuntes = listOf(
            apunte("1", "a", subtopicId = "subtema-1", moduleId = "modulo-1"),
            apunte("2", "b", subtopicId = null, moduleId = null),
        )

        val resultado = useCase(apuntes, NoteFilter(soloSinClasificar = true))

        assertEquals(listOf("2"), resultado.map { it.id })
    }

    @Test
    fun `sin soloSinClasificar activado no descarta los apuntes clasificados ni los sin clasificar`() {
        val apuntes = listOf(
            apunte("1", "a", subtopicId = "subtema-1", moduleId = "modulo-1"),
            apunte("2", "b", subtopicId = null, moduleId = null),
        )

        val resultado = useCase(apuntes, NoteFilter(soloSinClasificar = false))

        assertEquals(apuntes, resultado)
    }
}
