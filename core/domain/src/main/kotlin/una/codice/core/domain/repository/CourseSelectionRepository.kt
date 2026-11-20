package una.codice.core.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import una.codice.core.domain.model.StudentGroup

/**
 * El curso que el estudiante eligió ver, compartido entre la Biblioteca y el Mapa de cobertura:
 * si cambia de curso en una pantalla, la otra muestra el mismo.
 */
interface CourseSelectionRepository {

    /** Los grupos donde el estudiante tiene la matrícula aprobada: los cursos entre los que puede elegir. */
    fun observeCourses(): Flow<List<StudentGroup>>

    /** El id del grupo elegido, o `null` si no eligió ninguno ("Todos" en la Biblioteca). */
    val selectedGroupId: StateFlow<String?>

    fun select(groupId: String?)
}
