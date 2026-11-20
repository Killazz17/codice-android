package una.codice.feature.notes.presentation

import una.codice.core.designsystem.component.CourseOption
import una.codice.core.domain.model.Note
import una.codice.core.domain.usecase.note.NoteFilter

/**
 * Todo lo que la pantalla de Biblioteca necesita para dibujarse — mismo
 * criterio que `CoverageUiState`: el composable no guarda estado propio.
 *
 * ### Por qué [query] y [filter] viven en el nivel de arriba, y no dentro de [notes]
 *
 * El criterio de aceptación del issue #17 dice "el buscador no se pierde al
 * desplazar la lista", y el del #15 pide que "rotar el dispositivo conserva
 * el texto buscado y los filtros". Si [query]/[filter] estuvieran solo
 * adentro de [NotesStatus.Content], el buscador y los chips desaparecerían
 * de la pantalla en cuanto [notes] pasara a [NotesStatus.Loading] o a
 * cualquiera de los dos estados vacíos — justo lo que esos criterios piden
 * evitar. Separarlos dejar que el buscador/chips se dibujen siempre, sin
 * importar en qué estado esté la lista de abajo.
 *
 * @property query El texto que el estudiante escribió en el buscador (issue #17).
 * @property filter Los filtros de módulo/estado/"sin clasificar" activos.
 * @property notes El estado de la lista filtrada — ver [NotesStatus].
 * @property moduleOptions Los módulos disponibles para el chip de filtro
 *   (issue #17: "un chip por módulo del curso"), calculados sobre TODOS los
 *   apuntes del estudiante, no sobre [notes] ya filtrada — si se calcularan
 *   sobre la lista filtrada, activar un chip de módulo haría desaparecer a
 *   los demás chips de la fila.
 */
data class LibraryUiState(
    val query: String = "",
    val filter: NoteFilter = NoteFilter(),
    val notes: NotesStatus = NotesStatus.Loading,
    val moduleOptions: List<ModuleOption> = emptyList(),
    /** Los cursos del estudiante para el selector de arriba; con menos de dos no se muestra. */
    val courseOptions: List<CourseOption> = emptyList(),
    /** El curso elegido, o `null` para ver los apuntes de todos. */
    val selectedGroupId: String? = null,
    /** El borrado en curso, si el estudiante tocó el botón de la papelera — ver [DeleteState]. */
    val delete: DeleteState = DeleteState(),
)

/**
 * El borrado de un apunte, desde que se pide hasta que se confirma o falla.
 *
 * Vive aparte de [NotesStatus] porque no es un estado de la lista: mientras se borra, la lista se sigue viendo y
 * usando con normalidad. Y vive en [LibraryUiState] y no dentro del composable porque rotar el teléfono con el
 * diálogo abierto no debe perderlo, igual que no se pierden el buscador ni los filtros.
 *
 * @property pending El apunte que el estudiante quiere borrar; mientras no sea `null` el diálogo está abierto.
 * @property deleting La llamada al servidor está en curso: el diálogo bloquea sus botones para que un doble toque no
 *   mande dos borrados.
 * @property error Por qué falló el último intento, o `null`. Se muestra dentro del mismo diálogo, que queda abierto
 *   para que el estudiante pueda reintentar sin volver a buscar el apunte en la lista.
 */
data class DeleteState(
    val pending: Note? = null,
    val deleting: Boolean = false,
    val error: String? = null,
)

/** Un módulo disponible para filtrar, tal como lo necesita el chip del issue #17. */
data class ModuleOption(val moduleId: String, val label: String)

/**
 * El estado de la lista de apuntes ya filtrada, separado de [LibraryUiState]
 * para poder distinguir los dos "vacíos" que pide el issue #16:
 *
 * > "Se distingue sin resultados de sin apuntes todavía"
 *
 * Son mensajes distintos porque la causa es distinta: [EmptySinApuntes] es
 * "el estudiante no ha fotografiado nada todavía" (nada que ver con el
 * buscador); [EmptySinResultados] es "sí hay apuntes, pero ninguno pasa el
 * filtro actual" (el estudiante debería probar limpiar el filtro).
 */
sealed interface NotesStatus {

    /** [una.codice.core.domain.usecase.note.GetNotesUseCase] todavía no emitió su primer valor. */
    data object Loading : NotesStatus

    /** La lista ya filtrada, con al menos un apunte. */
    data class Content(val notes: List<Note>) : NotesStatus

    /** El repositorio no tiene ningún apunte para este estudiante — la Biblioteca está vacía de verdad. */
    data object EmptySinApuntes : NotesStatus

    /** Hay apuntes, pero ninguno coincide con [LibraryUiState.query]/[LibraryUiState.filter] actuales. */
    data object EmptySinResultados : NotesStatus

    /**
     * No se pudo pedir la lista y no hay ninguna anterior que mostrar. No es lo mismo que [EmptySinApuntes]: aquí el
     * estudiante sí puede tener apuntes, solo que no llegaron.
     */
    data class Error(val message: String) : NotesStatus
}
