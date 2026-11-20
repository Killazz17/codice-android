package una.codice.core.domain.usecase.syllabus

import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.repository.CourseRepository
import una.codice.core.domain.result.DomainResult

/**
 * Lee del servidor el temario que el curso tiene hoy. Sirve para abrir la pantalla con lo que ya hay y para saber,
 * antes de publicar, si el borrador va a quedar encima de un temario existente.
 */
class LoadSyllabusUseCase(
    private val courseRepository: CourseRepository,
) {
    suspend operator fun invoke(courseId: String): DomainResult<Syllabus> = courseRepository.loadSyllabus(courseId)
}
