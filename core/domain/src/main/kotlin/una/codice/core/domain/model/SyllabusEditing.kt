package una.codice.core.domain.model

import java.util.Collections

/**
 * Bajo esta confianza la IA dudó de un módulo o subtema del temario que extrajo del PDF, y la pantalla de revisión
 * lo marca para que el profesor lo vea antes de publicar. Es el mismo umbral que usa la transcripción de apuntes.
 */
const val SYLLABUS_REVIEW_THRESHOLD = 0.70f

/** Hacia dónde se mueve un módulo o un subtema dentro de su lista. */
enum class MoveDirection { UP, DOWN }

/**
 * Mueve un módulo una posición, hacia arriba o hacia abajo. Un módulo en el borde de la lista o un id que no existe
 * dejan el temario como estaba.
 *
 * El orden del temario es el de `number`, así que mover un módulo es repartir los mismos números entre los módulos en
 * su orden nuevo. Para que los códigos de sus subtemas sigan contando lo mismo, el prefijo `<número viejo>.` pasa a
 * `<número nuevo>.`, pero solo en los códigos que lo llevan: uno como `"A"` no se toca.
 */
fun Syllabus.moveModule(moduleId: String, direction: MoveDirection): Syllabus {
    val ordered = modules.sortedBy { it.number }
    val from = ordered.indexOfFirst { it.id == moduleId }
    val to = if (direction == MoveDirection.UP) from - 1 else from + 1
    if (from < 0 || to !in ordered.indices) return this

    val numbers = ordered.map { it.number }
    val reordered = ordered.toMutableList().also { Collections.swap(it, from, to) }
    return copy(modules = reordered.mapIndexed { index, module -> module.renumbered(numbers[index]) })
}

/**
 * Mueve un subtema una posición dentro de su módulo. Solo cambia `position`: el código es el identificador del
 * subtema en el programa del curso (`4.4`) y no depende de su lugar en la lista.
 */
fun CourseModule.moveSubtopic(subtopicId: String, direction: MoveDirection): CourseModule {
    val ordered = subtopics.sortedBy { it.position }
    val from = ordered.indexOfFirst { it.id == subtopicId }
    val to = if (direction == MoveDirection.UP) from - 1 else from + 1
    if (from < 0 || to !in ordered.indices) return this

    val reordered = ordered.toMutableList().also { Collections.swap(it, from, to) }
    return copy(subtopics = reordered.mapIndexed { index, subtopic -> subtopic.copy(position = index) })
}

private fun CourseModule.renumbered(newNumber: Int): CourseModule {
    if (newNumber == number) return this
    val oldPrefix = "$number."
    return copy(
        number = newNumber,
        subtopics = subtopics.map { subtopic ->
            if (subtopic.code.startsWith(oldPrefix)) {
                subtopic.copy(code = "$newNumber.${subtopic.code.removePrefix(oldPrefix)}")
            } else {
                subtopic
            }
        },
    )
}
