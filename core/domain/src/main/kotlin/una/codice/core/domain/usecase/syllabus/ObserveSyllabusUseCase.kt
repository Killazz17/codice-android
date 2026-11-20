package una.codice.core.domain.usecase.syllabus

import kotlinx.coroutines.flow.Flow
import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.repository.CourseRepository

/**
 * Observa el temario de un curso.
 *
 * Igual que `ObserveSessionUseCase`, no tiene lógica propia: su valor está en
 * la dirección de la dependencia que impone. Gracias a él, el ViewModel de
 * `:feature:syllabus` depende de este caso de uso y no de [CourseRepository],
 * que es lo que mantiene la regla "los features solo conocen casos de uso".
 */
class ObserveSyllabusUseCase(
    private val courseRepository: CourseRepository,
) {
    operator fun invoke(courseId: String): Flow<Syllabus> =
        courseRepository.observeSyllabus(courseId)
}
