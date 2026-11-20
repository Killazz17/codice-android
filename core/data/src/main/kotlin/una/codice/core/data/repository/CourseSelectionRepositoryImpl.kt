package una.codice.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import una.codice.core.domain.model.EnrollmentStatus
import una.codice.core.domain.model.StudentGroup
import una.codice.core.domain.repository.CourseSelectionRepository
import una.codice.core.domain.repository.EnrollmentRepository

/**
 * [CourseSelectionRepository] en memoria: un `@Singleton` que guarda el curso elegido mientras la app
 * está abierta, para que la Biblioteca y el Mapa muestren el mismo. Los cursos salen de las
 * matrículas aprobadas del estudiante.
 */
@Singleton
class CourseSelectionRepositoryImpl @Inject constructor(
    private val enrollmentRepository: EnrollmentRepository,
) : CourseSelectionRepository {

    private val selected = MutableStateFlow<String?>(null)

    override fun observeCourses(): Flow<List<StudentGroup>> =
        enrollmentRepository.observeMyEnrollments().map { enrollments ->
            enrollments.filter { it.status == EnrollmentStatus.ACTIVE }.map { it.group }
        }

    override val selectedGroupId: StateFlow<String?> = selected.asStateFlow()

    override fun select(groupId: String?) {
        selected.value = groupId
    }
}
