package una.codice.core.domain.usecase.syllabus

import una.codice.core.domain.model.Syllabus

/**
 * Revisa un temario y devuelve **todos** los problemas que tiene, no solo el
 * primero.
 *
 * ### Por qué devuelve una lista y no un `DomainResult`
 *
 * Un `DomainResult.Failure` lleva un solo error, y eso obligaría al profesor
 * a arreglar-guardar-arreglar-guardar hasta agotar los problemas de uno en
 * uno. Devolviendo la lista completa, la pantalla los muestra todos juntos y
 * el profesor decide en qué orden atacarlos. Es el mismo motivo por el que un
 * compilador no se detiene en el primer error.
 *
 * ### Qué NO valida
 *
 * Nada que dependa del servidor (que el curso exista, que el profesor sea el
 * dueño). Esto es validación local y barata: sirve para no gastar una
 * publicación que la base va a rechazar, no para reemplazar las reglas del
 * backend.
 */
class ValidateSyllabusUseCase {

    operator fun invoke(syllabus: Syllabus): List<SyllabusIssue> {
        if (syllabus.modules.isEmpty()) {
            return listOf(SyllabusIssue.EmptySyllabus)
        }

        val issues = mutableListOf<SyllabusIssue>()

        // Números de módulo repetidos: `UNIQUE (course_id, number)` del DDL.
        // Se reporta el número una sola vez aunque esté tres veces repetido —
        // al profesor le sirve saber cuál, no cuántas.
        syllabus.modules
            .groupBy { it.number }
            .filter { (_, repetidos) -> repetidos.size > 1 }
            .keys
            .sorted()
            .forEach { numero -> issues += SyllabusIssue.DuplicateModuleNumber(numero) }

        syllabus.modules.sortedBy { it.number }.forEach { modulo ->
            if (modulo.title.isBlank()) {
                issues += SyllabusIssue.BlankModuleTitle(modulo.number)
            }

            if (modulo.subtopics.isEmpty()) {
                issues += SyllabusIssue.ModuleWithoutSubtopics(modulo.number)
                return@forEach
            }

            // Códigos repetidos dentro del módulo: `UNIQUE (module_id, code)`.
            // Se comparan normalizados (sin espacios alrededor) porque "4.4 "
            // y "4.4" son el mismo código para cualquier lector humano, y la
            // base los aceptaría como distintos.
            modulo.subtopics
                .groupBy { it.code.trim() }
                .filter { (_, repetidos) -> repetidos.size > 1 }
                .keys
                .sorted()
                .forEach { codigo ->
                    issues += SyllabusIssue.DuplicateSubtopicCode(modulo.number, codigo)
                }

            modulo.subtopics.sortedBy { it.position }.forEach { subtema ->
                if (subtema.title.isBlank()) {
                    issues += SyllabusIssue.BlankSubtopicTitle(modulo.number, subtema.code)
                }
                if (subtema.objectives.none { it.isNotBlank() }) {
                    issues += SyllabusIssue.SubtopicWithoutObjectives(subtema.code)
                }
            }
        }

        return issues
    }
}

/** Si hay al menos un problema que impide publicar. */
fun List<SyllabusIssue>.hasBlockingIssues(): Boolean = any { it.isBlocking }
