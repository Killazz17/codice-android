package una.codice.core.domain.usecase.syllabus

import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.repository.CourseRepository
import una.codice.core.domain.result.DomainResult

/**
 * Publica el temario: **valida primero, guarda después, publica al final**.
 *
 * ### Por qué la validación vive aquí y no en la pantalla
 *
 * Mismo criterio que `LoginUseCase` con el correo: si la comprobación
 * estuviera en el ViewModel, cualquier otra entrada a "publicar" —un atajo,
 * una publicación automática tras extraer el temario con IA, una pantalla que
 * alguien agregue el próximo mes— tendría que acordarse de repetirla. Estando
 * aquí, todas la heredan gratis.
 *
 * ### Por qué guarda antes de publicar
 *
 * Publicar sin guardar dejaría visible para los estudiantes una versión
 * distinta de la que el profesor tiene en pantalla. Guardar primero es lo que
 * hace que "lo que veo es lo que se publica" sea cierto. Si el guardado
 * falla, no se publica nada — el criterio de aceptación del issue #32 pide
 * exactamente eso: *"un fallo deja el curso sin temario, no a medias"*.
 */
class PublishSyllabusUseCase(
    private val courseRepository: CourseRepository,
    private val validateSyllabus: ValidateSyllabusUseCase,
) {

    /**
     * @return los problemas encontrados si no se pudo publicar, o un
     *   [DomainResult.Success] con la lista de advertencias no bloqueantes
     *   que el profesor decidió ignorar (puede venir vacía).
     */
    suspend operator fun invoke(syllabus: Syllabus): PublishResult {
        val issues = validateSyllabus(syllabus)
        if (issues.hasBlockingIssues()) {
            return PublishResult.Rejected(issues)
        }

        // Guardar y publicar son dos llamadas al repositorio, y entre ellas
        // el guardado puede fallar. Se corta ahí: mejor un temario guardado
        // sin publicar que uno publicado a medias.
        return when (val guardado = courseRepository.saveDraft(syllabus)) {
            is DomainResult.Failure -> PublishResult.Failed(guardado.error.message)
            is DomainResult.Success -> when (val publicado = courseRepository.publish(syllabus.courseId)) {
                is DomainResult.Failure -> PublishResult.Failed(publicado.error.message)
                is DomainResult.Success -> PublishResult.Published(issues)
            }
        }
    }
}

/**
 * Cómo terminó un intento de publicación.
 *
 * Es un tipo propio y no un `DomainResult` porque el rechazo por validación
 * no es un error de infraestructura: trae una lista de problemas concretos
 * que la pantalla debe pintar uno por uno, no un mensaje suelto.
 */
sealed interface PublishResult {

    /** Se publicó. [warnings] trae las advertencias no bloqueantes, si las había. */
    data class Published(val warnings: List<SyllabusIssue>) : PublishResult

    /** No se publicó porque el temario tiene problemas que lo impiden. */
    data class Rejected(val issues: List<SyllabusIssue>) : PublishResult

    /** El temario estaba bien, pero el guardado o la publicación fallaron. */
    data class Failed(val message: String) : PublishResult
}
