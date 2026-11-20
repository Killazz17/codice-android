package una.codice.core.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import una.codice.core.data.network.enrollment.EnrollmentApiService
import una.codice.core.data.network.enrollment.EnrollmentDto
import una.codice.core.data.network.enrollment.EnrollmentRequestDto
import una.codice.core.data.network.enrollment.StudentGroupDto
import una.codice.core.data.network.teacher.TeacherApiService
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.EnrollmentStatus
import una.codice.core.domain.result.DomainResult

/**
 * Tests del lado del estudiante de [EnrollmentRepositoryImpl] contra [EnrollmentApiService]
 * mockeado con MockK, igual que [NoteRepositoryImplTest].
 */
class EnrollmentRepositoryImplTest {

    private val api = mockk<EnrollmentApiService>()
    private val teacherApi = mockk<TeacherApiService>()
    private val repositorio = EnrollmentRepositoryImpl(api, teacherApi)

    private val grupo = StudentGroupDto(
        id = 20,
        courseId = 3,
        courseCode = "EIF411",
        courseName = "Aplicaciones Móviles",
        teacherName = "Laura Jiménez",
        number = "01",
        period = "II Ciclo 2026",
    )

    private fun matricula(id: Long, status: String) = EnrollmentDto(
        id = id,
        groupId = 20,
        groupNumber = "01",
        courseId = 3,
        courseCode = "EIF411",
        courseName = "Aplicaciones Móviles",
        status = status,
    )

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun `resolver un codigo trae el grupo con su curso y su profesor`() = runTest {
        coEvery { api.findGroupByInviteCode("EIF411-01-7K2A") } returns grupo

        val encontrado = (repositorio.resolveInviteCode("EIF411-01-7K2A") as DomainResult.Success).data

        assertEquals("20", encontrado.id)
        assertEquals("3", encontrado.courseId)
        assertEquals("EIF411", encontrado.courseCode)
        assertEquals("Laura Jiménez", encontrado.professorName)
        assertEquals("II Ciclo 2026", encontrado.period)
    }

    @Test
    fun `un codigo que no existe es un error del campo codigo`() = runTest {
        coEvery { api.findGroupByInviteCode(any()) } throws httpError(404)

        val error = (repositorio.resolveInviteCode("EIF411-01-XXXX") as DomainResult.Failure).error

        assertTrue(error is DomainError.Validation && error.field == DomainError.Field.CODE)
    }

    @Test
    fun `unirse solicita la matricula con el id del grupo del codigo`() = runTest {
        coEvery { api.findGroupByInviteCode("EIF411-01-7K2A") } returns grupo
        coEvery { api.requestEnrollment(EnrollmentRequestDto(20)) } returns matricula(1, "PENDING")

        val resultado = repositorio.requestJoin("EIF411-01-7K2A", "1")

        assertTrue(resultado is DomainResult.Success)
        coVerify(exactly = 1) { api.requestEnrollment(EnrollmentRequestDto(20)) }
    }

    @Test
    fun `unirse dos veces al mismo curso es un error del campo codigo`() = runTest {
        coEvery { api.findGroupByInviteCode(any()) } returns grupo
        coEvery { api.requestEnrollment(any()) } throws httpError(409)

        val error = (repositorio.requestJoin("EIF411-01-7K2A", "1") as DomainResult.Failure).error

        assertTrue(error is DomainError.Validation && error.field == DomainError.Field.CODE)
    }

    @Test
    fun `si el codigo no existe no se intenta matricular`() = runTest {
        coEvery { api.findGroupByInviteCode(any()) } throws httpError(404)

        repositorio.requestJoin("EIF411-01-XXXX", "1")

        coVerify(exactly = 0) { api.requestEnrollment(any()) }
    }

    @Test
    fun `mis matriculas traducen el estado y el codigo del curso`() = runTest {
        coEvery { api.getMyEnrollments() } returns listOf(matricula(1, "ACTIVE"), matricula(2, "PENDING"), matricula(3, "OTRO"))

        val lista = repositorio.observeMyEnrollments().first()

        assertEquals(listOf(EnrollmentStatus.ACTIVE, EnrollmentStatus.PENDING, EnrollmentStatus.PENDING), lista.map { it.status })
        assertEquals("EIF411", lista.first().group.courseCode)
        assertEquals("Aplicaciones Móviles", lista.first().group.courseName)
    }

    @Test
    fun `despues de unirse mis matriculas se vuelven a pedir`() = runTest {
        coEvery { api.findGroupByInviteCode(any()) } returns grupo
        coEvery { api.requestEnrollment(any()) } returns matricula(1, "ACTIVE")
        coEvery { api.getMyEnrollments() } returnsMany listOf(emptyList(), listOf(matricula(1, "ACTIVE")))
        val flujo = repositorio.observeMyEnrollments()
        assertTrue(flujo.first().isEmpty())

        repositorio.requestJoin("EIF411-01-7K2A", "1")

        assertEquals(1, flujo.first().size)
    }

    @Test
    fun `sin conexion mis matriculas salen vacias en vez de tumbar la pantalla`() = runTest {
        coEvery { api.getMyEnrollments() } throws IOException("backend apagado")

        assertTrue(repositorio.observeMyEnrollments().first().isEmpty())
    }
}
