package una.codice.core.domain.usecase.syllabus

import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.repository.CourseRepository
import una.codice.core.domain.result.DomainResult

/**
 * Guarda el temario como borrador, sin publicarlo y **sin validarlo**.
 *
 * ### Por qué no valida, a diferencia de [PublishSyllabusUseCase]
 *
 * Un borrador a medias es el estado normal mientras se escribe: el profesor
 * agrega el módulo 4, se va a buscar el programa, vuelve y le pone título.
 * Bloquear el guardado hasta que el árbol esté perfecto haría perder ese
 * trabajo intermedio, que es justo lo contrario de para qué existe un
 * borrador. La validación se aplica al publicar, que es cuando el temario
 * pasa a ser visible para los estudiantes.
 */
class SaveSyllabusDraftUseCase(
    private val courseRepository: CourseRepository,
) {
    suspend operator fun invoke(syllabus: Syllabus): DomainResult<Unit> =
        courseRepository.saveDraft(syllabus)
}
