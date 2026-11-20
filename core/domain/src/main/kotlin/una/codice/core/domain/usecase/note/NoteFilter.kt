package una.codice.core.domain.usecase.note

import una.codice.core.domain.model.SyncStatus

/**
 * Los filtros combinables de la Biblioteca (issue #16 y #17: buscador +
 * chips de módulo/estado).
 *
 * Todos los campos son opcionales y se combinan con Y lógico: un filtro
 * "vacío" (`query` en blanco, `moduleId` nulo, `syncStatus` nulo,
 * `soloSinClasificar` en `false`) no descarta ningún apunte.
 *
 * @property query Texto a buscar dentro de [una.codice.core.domain.model.Note.confirmedText],
 *   sin distinguir mayúsculas ni acentos (criterio de aceptación del issue
 *   #16: "Buscar algoritmo encuentra también Algoritmo y algorítmo").
 * @property moduleId Si no es `null`, solo deja los apuntes cuyo subtema
 *   pertenece a este módulo.
 * @property syncStatus Si no es `null`, solo deja los apuntes con ese estado
 *   de sincronización (chip "Sincronizado" / "En cola" del issue #17).
 * @property soloSinClasificar Si es `true`, deja ver únicamente los apuntes
 *   cuyo [una.codice.core.domain.model.Note.subtopicId] es `null` — el chip
 *   "Sin clasificar".
 */
data class NoteFilter(
    val query: String = "",
    val moduleId: String? = null,
    val syncStatus: SyncStatus? = null,
    val soloSinClasificar: Boolean = false,
)
