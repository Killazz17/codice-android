package una.codice.feature.enrollment.presentation.teacher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Enrollment
import una.codice.core.domain.model.GroupInviteInfo
import una.codice.core.domain.model.GroupStudent
import una.codice.core.domain.model.NewCourse
import una.codice.core.domain.model.NewGroup
import una.codice.core.domain.model.NewUser
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.StudentGroup
import una.codice.core.domain.model.TeacherGroupSummary
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.repository.EnrollmentRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.auth.ObserveSessionUseCase
import una.codice.core.domain.usecase.enrollment.ObserveTeacherGroupsUseCase

/**
 * Tests de [TeacherCoursesViewModel]: agregar un grupo a un curso que ya existe y eliminar un curso.
 *
 * Mismo esquema que `SyllabusViewModelTest`: `setMain` con un despachador de pruebas para que `viewModelScope` avance
 * cuando el test lo decide.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeacherCoursesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun group(id: String, number: String, period: String = "2026-II", courseId: String = "10", code: String = "EQP101") =
        TeacherGroupSummary(
            group = StudentGroup(
                id = id, courseId = courseId, courseName = "Estructuras de Datos", groupNumber = number,
                period = period, professorName = "Profe", courseCode = code,
            ),
            activeStudentCount = 0,
            averageCoverage = 0f,
            pendingRequestsCount = 0,
        )

    private class FakeRepository(groups: List<TeacherGroupSummary>) : EnrollmentRepository {
        val groups = MutableStateFlow(groups)
        var addResult: DomainResult<GroupInviteInfo> = DomainResult.Success(GroupInviteInfo(groupId = "99", inviteCode = "EQP101-03-ABCD"))
        var deleteResult: DomainResult<Unit> = DomainResult.Success(Unit)
        val added = mutableListOf<Pair<String, NewGroup>>()
        val deleted = mutableListOf<String>()

        override suspend fun addGroup(courseId: String, newGroup: NewGroup): DomainResult<GroupInviteInfo> {
            added += courseId to newGroup
            return addResult
        }

        override suspend fun deleteCourse(courseId: String): DomainResult<Unit> {
            deleted += courseId
            return deleteResult
        }

        override fun observeTeacherGroups(teacherId: String): Flow<List<TeacherGroupSummary>> = groups

        override suspend fun resolveInviteCode(code: String): DomainResult<StudentGroup> = error("No lo usa")
        override suspend fun requestJoin(code: String, studentId: String): DomainResult<Unit> = error("No lo usa")
        override fun observeMyEnrollments(): Flow<List<Enrollment>> = error("No lo usa")
        override fun observeGroupInviteInfo(groupId: String): Flow<GroupInviteInfo> = error("No lo usa")
        override suspend fun regenerateInviteCode(groupId: String): DomainResult<GroupInviteInfo> = error("No lo usa")
        override suspend fun updateInviteSettings(groupId: String, requiresApproval: Boolean): DomainResult<Unit> = error("No lo usa")
        override fun observeGroupStudents(groupId: String): Flow<List<GroupStudent>> = error("No lo usa")
        override suspend fun decideEnrollment(enrollmentId: String, approve: Boolean): DomainResult<Unit> = error("No lo usa")
        override suspend fun createCourse(newCourse: NewCourse): DomainResult<GroupInviteInfo> = error("No lo usa")
        override suspend fun leaveEnrollment(enrollmentId: String): DomainResult<Unit> = error("No lo usa")
        override suspend fun removeStudent(groupId: String, enrollmentId: String): DomainResult<Unit> = error("No lo usa")
    }

    private val auth = object : AuthRepository {
        private val session = MutableStateFlow<Session?>(Session(token = "t", userId = "profe-1", role = UserRole.TEACHER))
        override suspend fun login(email: String, password: String): DomainResult<Session> = error("No lo usa")
        override suspend fun register(newUser: NewUser): DomainResult<Session> = error("No lo usa")
        override fun currentSession(): Flow<Session?> = session
        override suspend fun logout(): DomainResult<Unit> = error("No lo usa")
        override suspend fun changePassword(currentPassword: String, newPassword: String): DomainResult<Unit> = error("No lo usa")
    }

    /** Crea el ViewModel y lo deja suscrito, para que el estado combinado se calcule. */
    private fun TestScope.viewModel(repository: FakeRepository): TeacherCoursesViewModel {
        val viewModel = TeacherCoursesViewModel(
            observeSession = ObserveSessionUseCase(auth),
            observeTeacherGroups = ObserveTeacherGroupsUseCase(auth, repository),
            enrollmentRepository = repository,
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `el siguiente numero de grupo es el mas alto mas uno con dos digitos`() {
        assertEquals("01", TeacherCoursesViewModel.nextGroupNumber(emptyList()))
        assertEquals("03", TeacherCoursesViewModel.nextGroupNumber(listOf("01", "02")))
        assertEquals("04", TeacherCoursesViewModel.nextGroupNumber(listOf("01", "03")))
        assertEquals("11", TeacherCoursesViewModel.nextGroupNumber(listOf("10", "abc")))
    }

    @Test
    fun `agregar grupo abre el formulario con el siguiente numero y el periodo del ultimo grupo`() = runTest {
        val repository = FakeRepository(listOf(group("1", "01", "2026-I"), group("2", "02", "2026-II")))
        val viewModel = viewModel(repository)

        viewModel.onAddGroupClick("10")
        advanceUntilIdle()

        val dialog = viewModel.uiState.value.dialog as CourseDialog.AddGroup
        assertEquals("EQP101", dialog.courseCode)
        assertEquals("03", dialog.groupNumber)
        assertEquals("2026-II", dialog.period)
        assertTrue(dialog.requiresApproval)
    }

    @Test
    fun `confirmar agrega el grupo, cierra el formulario y abre su invitacion`() = runTest {
        val repository = FakeRepository(listOf(group("1", "01"), group("2", "02")))
        val viewModel = viewModel(repository)
        viewModel.onAddGroupClick("10")
        viewModel.onGroupNumberChange("3")
        viewModel.onRequiresApprovalChange(false)

        viewModel.onConfirmAddGroup()
        advanceUntilIdle()

        assertEquals(listOf("10" to NewGroup("03", "2026-II", requiresApproval = false)), repository.added)
        assertNull(viewModel.uiState.value.dialog)
        assertEquals("99", viewModel.groupCreated.first())
    }

    @Test
    fun `si el servidor rechaza el grupo el formulario sigue abierto con el motivo`() = runTest {
        val repository = FakeRepository(listOf(group("1", "01"))).apply {
            addResult = DomainResult.Failure(DomainError.Unknown("Ese curso ya tiene un grupo 02"))
        }
        val viewModel = viewModel(repository)
        viewModel.onAddGroupClick("10")

        viewModel.onConfirmAddGroup()
        advanceUntilIdle()

        val dialog = viewModel.uiState.value.dialog as CourseDialog.AddGroup
        assertEquals("Ese curso ya tiene un grupo 02", dialog.error)
        assertEquals(false, dialog.isLoading)
    }

    @Test
    fun `sin numero de grupo no se llama al servidor`() = runTest {
        val repository = FakeRepository(listOf(group("1", "01")))
        val viewModel = viewModel(repository)
        viewModel.onAddGroupClick("10")
        viewModel.onGroupNumberChange("")

        viewModel.onConfirmAddGroup()
        advanceUntilIdle()

        assertTrue(repository.added.isEmpty())
        assertTrue((viewModel.uiState.value.dialog as CourseDialog.AddGroup).error != null)
    }

    @Test
    fun `un curso sin estudiantes se elimina al confirmar`() = runTest {
        val repository = FakeRepository(listOf(group("1", "01"), group("2", "02")))
        val viewModel = viewModel(repository)
        viewModel.onDeleteCourseClick("10")

        viewModel.onConfirmDeleteCourse()
        advanceUntilIdle()

        assertEquals(listOf("10"), repository.deleted)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `un curso con estudiantes matriculados no se ofrece eliminar`() = runTest {
        val enrolled = group("1", "01").copy(activeStudentCount = 3)
        val repository = FakeRepository(listOf(enrolled, group("2", "02").copy(activeStudentCount = 2)))
        val viewModel = viewModel(repository)

        viewModel.onDeleteCourseClick("10")
        viewModel.onConfirmDeleteCourse()
        advanceUntilIdle()

        val dialog = viewModel.uiState.value.dialog as CourseDialog.ConfirmDelete
        assertEquals(5, dialog.activeStudents)
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun `si eliminar falla el aviso sigue abierto con el motivo`() = runTest {
        val repository = FakeRepository(listOf(group("1", "01"))).apply {
            deleteResult = DomainResult.Failure(DomainError.Unknown("No se puede eliminar un curso con estudiantes matriculados"))
        }
        val viewModel = viewModel(repository)
        viewModel.onDeleteCourseClick("10")

        viewModel.onConfirmDeleteCourse()
        advanceUntilIdle()

        val dialog = viewModel.uiState.value.dialog as CourseDialog.ConfirmDelete
        assertEquals("No se puede eliminar un curso con estudiantes matriculados", dialog.error)
    }

    @Test
    fun `cancelar cierra el dialogo sin llamar al servidor`() = runTest {
        val repository = FakeRepository(listOf(group("1", "01")))
        val viewModel = viewModel(repository)
        viewModel.onAddGroupClick("10")

        viewModel.onDismissDialog()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertTrue(repository.added.isEmpty() && repository.deleted.isEmpty())
    }
}
