package una.codice.core.domain.model

/**
 * El temario completo de un curso: el árbol `módulo → subtema → objetivos`
 * que el profesor publica y contra el que se audita cada apunte.
 *
 * ### Publicado o borrador
 *
 * [isPublished] distingue el temario que los estudiantes ya pueden ver del
 * que el profesor todavía está armando. La spec es explícita en esto: *"Nada
 * llega al curso hasta que el profesor lo revisa y publica"* — y también es
 * lo que evita que el mapa de cobertura de un estudiante se llene de
 * subtemas a medio escribir mientras el profesor teclea.
 *
 * ### El origen importa para la entrega
 *
 * [source] deja registrado si el temario se escribió a mano o si salió de la
 * llamada de IA 1 (extracción del PDF). Hoy solo se usa
 * [SyllabusSource.MANUAL], porque esa llamada todavía no existe; cuando
 * exista, la pantalla podrá mostrar "extraído automáticamente, revisá antes
 * de publicar" sin cambiar el modelo.
 */
data class Syllabus(
    val courseId: String,
    val modules: List<CourseModule> = emptyList(),
    val isPublished: Boolean = false,
    val source: SyllabusSource = SyllabusSource.MANUAL,
) {
    /** Todos los subtemas de todos los módulos, en orden de módulo y posición. */
    val allSubtopics: List<Subtopic>
        get() = modules.sortedBy { it.number }.flatMap { modulo ->
            modulo.subtopics.sortedBy { it.position }
        }

    /**
     * Cuántos subtemas quedaron sin un solo objetivo.
     *
     * Es el número que la pantalla del profesor muestra en ámbar antes de
     * publicar: un subtema sin objetivos no se puede auditar, así que
     * publicarlo así deja un hueco silencioso en el mapa de cobertura de
     * todos sus estudiantes.
     */
    val subtopicsWithoutObjectives: Int
        get() = allSubtopics.count { it.objectives.isEmpty() }
}

/** De dónde salió el temario. Ver el KDoc de [Syllabus]. */
enum class SyllabusSource {
    /** El profesor lo escribió a mano en la app. */
    MANUAL,

    /** Lo propuso la llamada de IA 1 a partir del PDF del programa. */
    AI_EXTRACTED,
}
