package una.codice.core.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import una.codice.core.data.network.coverage.CoverageApiService
import una.codice.core.data.network.enrollment.EnrollmentApiService
import una.codice.core.data.network.teacher.AggregatedSubtopicDto
import una.codice.core.data.network.teacher.CourseCreateDto
import una.codice.core.data.network.teacher.CourseDto
import una.codice.core.data.network.teacher.EnrollmentStatusDto
import una.codice.core.data.network.teacher.GroupCoverageDto
import una.codice.core.data.network.teacher.GroupCreateDto
import una.codice.core.data.network.teacher.GroupEnrollmentDto
import una.codice.core.data.network.teacher.TeacherApiService
import una.codice.core.data.network.teacher.TeacherGroupDto
import una.codice.core.domain.model.EnrollmentStatus
import una.codice.core.domain.model.NewCourse
import una.codice.core.domain.model.NewGroup
import una.codice.core.domain.result.DomainResult

/**
 * El lado del profesor de [EnrollmentRepositoryImpl] y [CoverageRepositoryImpl.getGroupCoverage]
 * contra [TeacherApiService] mockeado con MockK.
 */
class TeacherRepositoryTest {

    private val teacherApi = mockk<TeacherApiService>()
    private val enrollments = EnrollmentRepositoryImpl(mockk<EnrollmentApiService>(), teacherApi)
    private val coverage = CoverageRepositoryImpl(mockk<EnrollmentApiService>(), mockk<CoverageApiService>(), teacherApi)

    private val grupo = TeacherGroupDto(
        id = 20, courseId = 10, courseCode = "EIF411", courseName = "Móviles", teacherName = "Laura Jiménez",
        number = "01", period = "2026-II", inviteCode = "EIF411-01-7K2A", requiresApproval = true,
        inviteExpiresAt = "2026-11-13T00:00:00Z",
    )

    private fun matricula(id: Long, status: String, name: String = "Ana Vargas Mora") =
        GroupEnrollmentDto(id = id, studentName = name, studentEmail = "ana@example.com", status = status, requestedAt = "2026-10-12T15:00:00Z")

    private fun subtema(id: Long, module: Int, covered: Double) = AggregatedSubtopicDto(
        subtopicId = id, subtopicCode = "$module.$id", subtopicTitle = "Subtema $id",
        coveredPercentage = covered, partialPercentage = 0.0, noNotesPercentage = 100 - covered,
        moduleNumber = module, moduleTitle = "Módulo $module",
    )

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun `los grupos del profesor traen inscritos, pendientes y cobertura promedio`() = runTest {
        coEvery { teacherApi.getMyCourses() } returns listOf(CourseDto(10, "EIF411", "Móviles"))
        coEvery { teacherApi.getCourseGroups(10) } returns listOf(grupo)
        coEvery { teacherApi.getGroupEnrollments(20, null) } returns
            listOf(matricula(1, "ACTIVE"), matricula(2, "ACTIVE"), matricula(3, "PENDING"), matricula(4, "REJECTED"))
        coEvery { teacherApi.getGroupCoverage(20) } returns
            GroupCoverageDto(groupId = 20, totalStudents = 2, subtopics = listOf(subtema(1, 4, 50.0), subtema(2, 4, 0.0)))

        val resumen = enrollments.observeTeacherGroups("1").first().single()

        assertEquals("20", resumen.group.id)
        assertEquals("EIF411", resumen.group.courseCode)
        assertEquals("Laura Jiménez", resumen.group.professorName)
        assertEquals(2, resumen.activeStudentCount)
        assertEquals(1, resumen.pendingRequestsCount)
        assertEquals(0.25f, resumen.averageCoverage, 0.001f)
    }

    @Test
    fun `si la cobertura falla el grupo se muestra igual con cobertura cero`() = runTest {
        coEvery { teacherApi.getMyCourses() } returns listOf(CourseDto(10, "EIF411", "Móviles"))
        coEvery { teacherApi.getCourseGroups(10) } returns listOf(grupo)
        coEvery { teacherApi.getGroupEnrollments(20, null) } returns emptyList()
        coEvery { teacherApi.getGroupCoverage(20) } throws httpError(500)

        val resumen = enrollments.observeTeacherGroups("1").first().single()

        assertEquals(0f, resumen.averageCoverage)
    }

    @Test
    fun `la invitacion trae el codigo, si pide aprobacion y el vencimiento`() = runTest {
        coEvery { teacherApi.getMyCourses() } returns listOf(CourseDto(10, "EIF411", "Móviles"))
        coEvery { teacherApi.getCourseGroups(10) } returns listOf(grupo)

        val invitacion = enrollments.observeGroupInviteInfo("20").first()

        assertEquals("EIF411-01-7K2A", invitacion.inviteCode)
        assertTrue(invitacion.requiresApproval)
        assertTrue(invitacion.expiresAt.orEmpty().startsWith("Vence el "))
    }

    @Test
    fun `los estudiantes del grupo ocultan las solicitudes rechazadas y parten el nombre`() = runTest {
        coEvery { teacherApi.getGroupEnrollments(20, null) } returns
            listOf(matricula(1, "ACTIVE"), matricula(2, "PENDING", name = "Luis Rojas"), matricula(3, "REJECTED"))

        val estudiantes = enrollments.observeGroupStudents("20").first()

        assertEquals(listOf(EnrollmentStatus.ACTIVE, EnrollmentStatus.PENDING), estudiantes.map { it.status })
        assertEquals("Ana", estudiantes.first().user.firstName)
        assertEquals("Vargas Mora", estudiantes.first().user.lastName)
        assertEquals(null, estudiantes.first().coverage)
    }

    @Test
    fun `aprobar manda ACTIVE y vuelve a pedir la lista`() = runTest {
        coEvery { teacherApi.decideEnrollment(2, EnrollmentStatusDto("ACTIVE")) } returns Unit
        coEvery { teacherApi.getGroupEnrollments(20, null) } returnsMany listOf(
            listOf(matricula(2, "PENDING")),
            listOf(matricula(2, "ACTIVE")),
        )
        val lista = enrollments.observeGroupStudents("20")
        assertEquals(EnrollmentStatus.PENDING, lista.first().single().status)

        val resultado = enrollments.decideEnrollment("2", approve = true)

        assertTrue(resultado is DomainResult.Success)
        assertEquals(EnrollmentStatus.ACTIVE, lista.first().single().status)
    }

    @Test
    fun `rechazar manda REJECTED`() = runTest {
        coEvery { teacherApi.decideEnrollment(any(), any()) } returns Unit

        enrollments.decideEnrollment("2", approve = false)

        coVerify { teacherApi.decideEnrollment(2, EnrollmentStatusDto("REJECTED")) }
    }

    @Test
    fun `crear curso crea el curso y su primer grupo y devuelve la invitacion`() = runTest {
        coEvery { teacherApi.getMyCourses() } returns emptyList()
        coEvery { teacherApi.createCourse(CourseCreateDto("EIF411", "Móviles")) } returns CourseDto(10, "EIF411", "Móviles")
        coEvery { teacherApi.createGroup(GroupCreateDto(10, "01", "2026-II", requiresApproval = false)) } returns grupo

        val resultado = enrollments.createCourse(NewCourse("EIF411", "Móviles", "01", "2026-II", requiresApproval = false))

        assertEquals("EIF411-01-7K2A", (resultado as DomainResult.Success).data.inviteCode)
        assertEquals("20", resultado.data.groupId)
    }

    @Test
    fun `un codigo de curso que es de otro profesor se explica en el error`() = runTest {
        coEvery { teacherApi.getMyCourses() } returns emptyList()
        coEvery { teacherApi.createCourse(any()) } throws httpError(409)

        val resultado = enrollments.createCourse(NewCourse("EIF411", "Móviles", "01", "2026-II", requiresApproval = true))

        assertEquals("Ese código de curso ya lo usa otro profesor", (resultado as DomainResult.Failure).error.message)
        coVerify(exactly = 0) { teacherApi.createGroup(any()) }
    }

    @Test
    fun `crear curso con un codigo que ya es mio agrega el grupo a ese curso`() = runTest {
        coEvery { teacherApi.getMyCourses() } returns listOf(CourseDto(10, "EIF411", "Móviles"))
        coEvery { teacherApi.createGroup(GroupCreateDto(10, "03", "2026-II", requiresApproval = true)) } returns grupo

        // El código llega en mayúsculas desde el formulario; igual se reconoce el curso.
        val resultado = enrollments.createCourse(NewCourse("eif411", "Móviles", "03", "2026-II", requiresApproval = true))

        assertEquals("20", (resultado as DomainResult.Success).data.groupId)
        coVerify(exactly = 0) { teacherApi.createCourse(any()) }
    }

    @Test
    fun `agregar un grupo a un curso existente devuelve su invitacion`() = runTest {
        coEvery { teacherApi.createGroup(GroupCreateDto(10, "03", "2026-II", requiresApproval = false)) } returns grupo

        val resultado = enrollments.addGroup("10", NewGroup("03", "2026-II", requiresApproval = false))

        assertEquals("EIF411-01-7K2A", (resultado as DomainResult.Success).data.inviteCode)
    }

    @Test
    fun `un numero de grupo repetido se explica en el error`() = runTest {
        coEvery { teacherApi.createGroup(any()) } throws httpError(409)

        val resultado = enrollments.addGroup("10", NewGroup("02", "2026-II", requiresApproval = true))

        assertEquals("Ese curso ya tiene un grupo 02", (resultado as DomainResult.Failure).error.message)
    }

    @Test
    fun `eliminar un curso llama al backend con su id`() = runTest {
        coEvery { teacherApi.deleteCourse(10) } returns Unit

        val resultado = enrollments.deleteCourse("10")

        assertTrue(resultado is DomainResult.Success)
        coVerify { teacherApi.deleteCourse(10) }
    }

    @Test
    fun `un curso con estudiantes matriculados no se puede eliminar y se explica`() = runTest {
        coEvery { teacherApi.deleteCourse(10) } throws httpError(422)

        val resultado = enrollments.deleteCourse("10")

        assertEquals(
            "No se puede eliminar un curso con estudiantes matriculados",
            (resultado as DomainResult.Failure).error.message,
        )
    }

    @Test
    fun `la cobertura del grupo se agrupa por modulo en orden`() = runTest {
        coEvery { teacherApi.getGroupCoverage(20) } returns GroupCoverageDto(
            groupId = 20,
            totalStudents = 3,
            subtopics = listOf(subtema(1, 5, 100.0), subtema(2, 4, 50.0), subtema(3, 4, 0.0)),
        )

        val grupoCobertura = (coverage.getGroupCoverage("20") as DomainResult.Success).data

        assertEquals(3, grupoCobertura.totalStudents)
        assertEquals(listOf(4, 5), grupoCobertura.modules.map { it.moduleNumber })
        assertEquals(25.0, grupoCobertura.modules.first().averageCoveredPercentage, 0.001)
        assertEquals(50.0, grupoCobertura.averageCoveredPercentage, 0.001)
    }

    @Test
    fun `un estudiante que pide la cobertura del grupo recibe un error claro`() = runTest {
        coEvery { teacherApi.getGroupCoverage(20) } throws httpError(403)

        val resultado = coverage.getGroupCoverage("20")

        assertEquals("Solo el profesor del grupo ve su cobertura", (resultado as DomainResult.Failure).error.message)
    }
}
