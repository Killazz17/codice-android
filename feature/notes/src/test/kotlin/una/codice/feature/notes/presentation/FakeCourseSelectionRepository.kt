package una.codice.feature.notes.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import una.codice.core.domain.model.StudentGroup
import una.codice.core.domain.repository.CourseSelectionRepository

/** [CourseSelectionRepository] en memoria para los tests: sin cursos, a menos que se pasen. */
class FakeCourseSelectionRepository(
    courses: List<StudentGroup> = emptyList(),
) : CourseSelectionRepository {

    val courses = MutableStateFlow(courses)
    private val selected = MutableStateFlow<String?>(null)

    override fun observeCourses(): Flow<List<StudentGroup>> = courses

    override val selectedGroupId: StateFlow<String?> = selected

    override fun select(groupId: String?) {
        selected.value = groupId
    }
}
