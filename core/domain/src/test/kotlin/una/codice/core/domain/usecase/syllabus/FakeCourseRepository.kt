package una.codice.core.domain.usecase.syllabus

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.repository.CourseRepository
import una.codice.core.domain.result.DomainResult

/**
 * Implementación falsa de [CourseRepository] para los tests: guarda el
 * temario en memoria, sin red ni base de datos.
 *
 * Registra en [savedDrafts] y [publishedCourses] lo que recibió, que es lo
 * que permite verificar el criterio de aceptación del issue #32 — *"un fallo
 * deja el curso sin temario, no a medias"*: para probarlo no basta con mirar
 * el resultado, hay que demostrar que **la publicación ni siquiera se
 * intentó** cuando el guardado falló.
 *
 * @param saveFailure Si no es `null`, [saveDraft] falla con este error.
 * @param publishFailure Si no es `null`, [publish] falla con este error.
 */
class FakeCourseRepository(
    initialSyllabus: Syllabus = Syllabus(courseId = "curso-1"),
    private val saveFailure: DomainError? = null,
    private val publishFailure: DomainError? = null,
) : CourseRepository {

    private val syllabus = MutableStateFlow(initialSyllabus)

    /** Cada temario con el que se invocó a [saveDraft], en orden. */
    val savedDrafts = mutableListOf<Syllabus>()

    /** Cada `courseId` con el que se invocó a [publish]. */
    val publishedCourses = mutableListOf<String>()

    override fun observeSyllabus(courseId: String): Flow<Syllabus> = syllabus

    /** Si se fija, es lo que devuelve [loadSyllabus] en vez del temario en memoria. */
    var loadResult: DomainResult<Syllabus>? = null

    /** Cada `courseId` con el que se invocó a [loadSyllabus]. */
    val loadedCourses = mutableListOf<String>()

    override suspend fun loadSyllabus(courseId: String): DomainResult<Syllabus> {
        loadedCourses += courseId
        return loadResult ?: DomainResult.Success(syllabus.value)
    }

    override suspend fun saveDraft(syllabus: Syllabus): DomainResult<Unit> {
        // Se registra ANTES de comprobar el fallo: interesa saber que la
        // llamada ocurrió, haya salido bien o mal.
        savedDrafts += syllabus

        saveFailure?.let { return DomainResult.Failure(it) }

        this.syllabus.value = syllabus
        return DomainResult.Success(Unit)
    }

    override suspend fun publish(courseId: String): DomainResult<Unit> {
        publishedCourses += courseId

        publishFailure?.let { return DomainResult.Failure(it) }

        syllabus.value = syllabus.value.copy(isPublished = true)
        return DomainResult.Success(Unit)
    }

    override suspend fun extractSyllabusFromPdf(
        courseId: String,
        pdfBytes: ByteArray,
        filename: String,
    ): DomainResult<Syllabus> {
        return DomainResult.Success(syllabus.value.copy(courseId = courseId))
    }
}
