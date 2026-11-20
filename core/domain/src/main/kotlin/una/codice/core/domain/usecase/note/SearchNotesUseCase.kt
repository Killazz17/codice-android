package una.codice.core.domain.usecase.note

import java.text.Normalizer
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.SyncStatus

/** Los acentos combinables (categoría Unicode "Mn") que [SearchNotesUseCase] elimina al normalizar. */
private val ACENTOS = Regex("\\p{Mn}+")

/**
 * Filtra una lista de apuntes ya cargada según un [NoteFilter], en Kotlin
 * puro — sin Android, sin Room, sin `LibraryViewModel` de por medio.
 *
 * ### Por qué el filtrado vive aquí y no en el ViewModel ni en una consulta SQL
 *
 * Mismo criterio que [una.codice.core.domain.usecase.coverage.GetCoverageMapUseCase]:
 * una regla de negocio ("qué cuenta como coincidencia de búsqueda", "los
 * filtros se combinan con Y lógico") se prueba con JUnit puro solo si vive en
 * el dominio. El issue #16 lo pide explícito: "la búsqueda ocurre aquí, no
 * dentro del composable".
 *
 * Todos los filtros de [NoteFilter] se combinan con **Y lógico**: un apunte
 * tiene que pasar todos los filtros activos para quedar en el resultado.
 */
class SearchNotesUseCase {

    operator fun invoke(notes: List<Note>, filter: NoteFilter): List<Note> {
        // Se normaliza UNA sola vez por invocación, no una vez por nota
        // dentro del `filter { }` — evita repetir Normalizer+Regex sobre el
        // mismo texto de búsqueda en cada elemento de la lista (sugerencia
        // de revisión de código sobre la primera versión de esta clase).
        val queryNormalizada = if (filter.query.isBlank()) null else normalizar(filter.query)

        return notes.filter { note ->
            coincideConLaBusqueda(note, queryNormalizada) &&
                coincideConElModulo(note, filter.moduleId) &&
                coincideConElEstado(note, filter.syncStatus) &&
                coincideConSinClasificar(note, filter.soloSinClasificar)
        }
    }

    private fun coincideConLaBusqueda(note: Note, queryNormalizada: String?): Boolean {
        if (queryNormalizada == null) return true
        return normalizar(note.confirmedText).contains(queryNormalizada)
    }

    private fun coincideConElModulo(note: Note, moduleId: String?): Boolean {
        if (moduleId == null) return true
        return note.moduleId == moduleId
    }

    private fun coincideConElEstado(note: Note, syncStatus: SyncStatus?): Boolean {
        if (syncStatus == null) return true
        return note.syncStatus == syncStatus
    }

    private fun coincideConSinClasificar(note: Note, soloSinClasificar: Boolean): Boolean {
        if (!soloSinClasificar) return true
        return note.subtopicId == null
    }

    /**
     * Pasa a minúsculas y quita los acentos, para que "algorítmo", "Algoritmo"
     * y "algoritmo" comparen iguales (criterio de aceptación del issue #16).
     *
     * `Normalizer.normalize(NFD, ...)` separa cada letra acentuada en la letra
     * base + su acento como caracter Unicode aparte (categoría "Mn" = marca
     * diacrítica combinable); el `replace` con esa expresión regular los
     * elimina y deja solo la letra base.
     */
    private fun normalizar(texto: String): String {
        val sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replace(ACENTOS, "")
        return sinAcentos.lowercase()
    }
}
