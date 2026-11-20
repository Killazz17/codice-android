package una.codice.presentation.navigation

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.StudentGroup
import una.codice.core.domain.model.TeacherGroupSummary
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.repository.CourseSelectionRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.auth.LogoutUseCase
import una.codice.core.domain.usecase.auth.ObserveSessionUseCase
import una.codice.core.domain.usecase.enrollment.ObserveTeacherGroupsUseCase
import una.codice.core.domain.usecase.note.GetNotesUseCase
import una.codice.core.domain.usecase.review.GetPendingReviewsUseCase

/**
 * Tests de [ProfileViewModel]: que las cifras de "Mi actividad" salgan de los casos de uso según el rol y que una cifra
 * que falla no rompa las demás. Los casos de uso se simulan con MockK; `Dispatchers.Main` se reemplaza porque el `init {}`
 * ya empieza a pedir datos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val logout = mockk<LogoutUseCase>(relaxed = true)
    private val observeSession = mockk<ObserveSessionUseCase>()
    private val observeTeacherGroups = mockk<ObserveTeacherGroupsUseCase>()
    private val courseSelection = mockk<CourseSelectionRepository>()
    private val getNotes = mockk<GetNotesUseCase>()
    private val getPendingReviews = mockk<GetPendingReviewsUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crear(sesion: Session): ProfileViewModel {
        every { observeSession() } returns flowOf(sesion)
        val viewModel = ProfileViewModel(logout, observeSession, observeTeacherGroups, courseSelection, getNotes, getPendingReviews)
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private fun grupo(alumnos: Int, cobertura: Float, solicitudes: Int) = TeacherGroupSummary(
        group = mockk<StudentGroup>(),
        activeStudentCount = alumnos,
        averageCoverage = cobertura,
        pendingRequestsCount = solicitudes,
    )

    private val estudiante = Session(token = "t", userId = "7", role = UserRole.STUDENT, userName = "Ana Rojas", email = "ana@est.una.ac.cr")
    private val profesor = Session(token = "t", userId = "3", role = UserRole.TEACHER, userName = "Luis Mora", email = "luis@una.ac.cr")

    private fun estudianteConDatos(cursos: Int, apuntes: Int, repasos: DomainResult<List<PendingReview>>) {
        every { courseSelection.observeCourses() } returns flowOf(List(cursos) { mockk<StudentGroup>() })
        every { getNotes(any(), any()) } returns flowOf(List(apuntes) { mockk<Note>() })
        coEvery { getPendingReviews() } returns repasos
    }

    @Test
    fun `muestra el nombre y el correo de la sesion`() = runTest(testDispatcher) {
        estudianteConDatos(cursos = 1, apuntes = 0, repasos = DomainResult.Success(emptyList()))

        val estado = crear(estudiante).state.value

        assertEquals("Ana Rojas", estado.userName)
        assertEquals("ana@est.una.ac.cr", estado.email)
        assertEquals(UserRole.STUDENT, estado.role)
    }

    @Test
    fun `el profesor ve sus grupos, alumnos y solicitudes y la cobertura promedio`() = runTest(testDispatcher) {
        every { observeTeacherGroups() } returns flowOf(listOf(grupo(10, 0.5f, 1), grupo(4, 0.7f, 2)))

        val actividad = crear(profesor).state.value.activity

        assertEquals(listOf("2", "14", "3"), actividad.stats.map { it.value })
        assertEquals(0.6f, actividad.coverage!!, 0.001f)
    }

    @Test
    fun `el estudiante ve sus cursos, apuntes y repasos de hoy`() = runTest(testDispatcher) {
        estudianteConDatos(cursos = 2, apuntes = 3, repasos = DomainResult.Success(listOf(mockk<PendingReview>())))

        val actividad = crear(estudiante).state.value.activity

        assertEquals(listOf("2", "3", "1"), actividad.stats.map { it.value })
    }

    @Test
    fun `si fallan los repasos solo esa cifra queda sin conocer`() = runTest(testDispatcher) {
        estudianteConDatos(cursos = 2, apuntes = 3, repasos = DomainResult.Failure(DomainError.Network()))

        val actividad = crear(estudiante).state.value.activity

        assertEquals(listOf("2", "3", UNKNOWN), actividad.stats.map { it.value })
    }

    @Test
    fun `refrescar vuelve a pedir las cifras`() = runTest(testDispatcher) {
        estudianteConDatos(cursos = 1, apuntes = 1, repasos = DomainResult.Success(emptyList()))
        val viewModel = crear(estudiante)
        assertEquals(listOf("1", "1", "0"), viewModel.state.value.activity.stats.map { it.value })

        estudianteConDatos(cursos = 1, apuntes = 4, repasos = DomainResult.Success(emptyList()))
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("1", "4", "0"), viewModel.state.value.activity.stats.map { it.value })
    }
}
