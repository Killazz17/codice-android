package una.codice.feature.syllabus.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.repository.CourseRepository
import una.codice.core.domain.result.DomainResult

/**
 * Doble de [CourseRepository] para probar [SyllabusViewModel].
 *
 * Copia local a propósito de la que vive en `:core:domain/src/test`: no hay
 * `testFixtures` configurado en el proyecto — mismo criterio que ya siguen
 * los fakes de `:feature:auth`, `:feature:coverage` y `:feature:enrollment`.
 */
class FakeCourseRepository(
    initialSyllabus: Syllabus = Syllabus(courseId = "EIF411"),
    private val saveFailure: DomainError? = null,
) : CourseRepository {

    private val syllabus = MutableStateFlow(initialSyllabus)

    val savedDrafts = mutableListOf<Syllabus>()
    val publishedCourses = mutableListOf<String>()

    override fun observeSyllabus(courseId: String): Flow<Syllabus> = syllabus

    /** Lo que el servidor tiene hoy para el curso; es lo que devuelve [loadSyllabus]. */
    var serverSyllabus: DomainResult<Syllabus> = DomainResult.Success(Syllabus(courseId = "EIF411"))

    val loadedCourses = mutableListOf<String>()

    override suspend fun loadSyllabus(courseId: String): DomainResult<Syllabus> {
        loadedCourses += courseId
        return serverSyllabus
    }

    override suspend fun saveDraft(syllabus: Syllabus): DomainResult<Unit> {
        savedDrafts += syllabus
        saveFailure?.let { return DomainResult.Failure(it) }
        this.syllabus.value = syllabus
        return DomainResult.Success(Unit)
    }

    override suspend fun publish(courseId: String): DomainResult<Unit> {
        publishedCourses += courseId
        syllabus.value = syllabus.value.copy(isPublished = true)
        return DomainResult.Success(Unit)
    }

    /** Los cursos para los que se pidió extraer el temario de un PDF. */
    val extractedCourses = mutableListOf<String>()

    override suspend fun extractSyllabusFromPdf(
        courseId: String,
        pdfBytes: ByteArray,
        filename: String,
    ): DomainResult<Syllabus> {
        extractedCourses += courseId
        return DomainResult.Success(syllabus.value.copy(courseId = courseId))
    }
}
