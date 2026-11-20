package una.codice.core.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import una.codice.core.data.network.coverage.CoverageApiService
import una.codice.core.data.network.coverage.PersonalCoverageDto
import una.codice.core.data.network.coverage.SubtopicCoverageDto
import una.codice.core.data.network.enrollment.EnrollmentApiService
import una.codice.core.data.network.enrollment.EnrollmentDto
import una.codice.core.data.network.teacher.TeacherApiService
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.CoverageStatus
import una.codice.core.domain.result.DomainResult

/**
 * Tests de [CoverageRepositoryImpl] contra los servicios de red mockeados con MockK, sin levantar el
 * backend, igual que [NoteRepositoryImplTest].
 */
class CoverageRepositoryImplTest {

    private val enrollmentApi = mockk<EnrollmentApiService>()
    private val coverageApi = mockk<CoverageApiService>()
    private val teacherApi = mockk<TeacherApiService>()
    private val repositorio = CoverageRepositoryImpl(enrollmentApi, coverageApi, teacherApi)

    private fun matricula(groupId: Long, status: String) =
        EnrollmentDto(id = groupId, groupId = groupId, courseId = 1, status = status)

    private fun subtema(id: Long, status: String, moduleNumber: Int? = 4, moduleTitle: String? = "Servicios") =
        SubtopicCoverageDto(
            subtopicId = id,
            subtopicCode = "4.$id",
            subtopicTitle = "Subtema $id",
            status = status,
            moduleNumber = moduleNumber,
            moduleTitle = moduleTitle,
        )

    @Test
    fun `pide la cobertura del grupo con la matricula aprobada`() = runTest {
        coEvery { enrollmentApi.getMyEnrollments() } returns listOf(matricula(7, "PENDING"), matricula(20, "ACTIVE"))
        coEvery { coverageApi.getMyCoverage(20) } returns PersonalCoverageDto(
            groupId = 20,
            subtopics = listOf(subtema(1, "COVERED"), subtema(2, "NO_NOTES")),
        )

        val filas = (repositorio.getSubtopicCoverage("1") as DomainResult.Success).data

        assertEquals(listOf("1", "2"), filas.map { it.subtopicId })
        assertEquals(listOf(CoverageStatus.COVERED, CoverageStatus.NO_NOTES), filas.map { it.status })
        assertEquals(4, filas.first().moduleNumber)
        assertEquals("Servicios", filas.first().moduleTitle)
        assertEquals("4.1", filas.first().subtopicCode)
        coVerify(exactly = 0) { coverageApi.getMyCoverage(7) }
    }

    @Test
    fun `sin matricula aprobada entrega una lista vacia sin pedir cobertura`() = runTest {
        coEvery { enrollmentApi.getMyEnrollments() } returns listOf(matricula(7, "PENDING"), matricula(8, "REJECTED"))

        val filas = (repositorio.getSubtopicCoverage("1") as DomainResult.Success).data

        assertTrue(filas.isEmpty())
        coVerify(exactly = 0) { coverageApi.getMyCoverage(any()) }
    }

    @Test
    fun `un estado desconocido o un subtema sin modulo no tumban el mapa`() = runTest {
        coEvery { enrollmentApi.getMyEnrollments() } returns listOf(matricula(20, "ACTIVE"))
        coEvery { coverageApi.getMyCoverage(20) } returns PersonalCoverageDto(
            groupId = 20,
            subtopics = listOf(subtema(1, "OTRO", moduleNumber = null, moduleTitle = null)),
        )

        val fila = (repositorio.getSubtopicCoverage("1") as DomainResult.Success).data.single()

        assertEquals(CoverageStatus.NO_NOTES, fila.status)
        assertEquals(0, fila.moduleNumber)
        assertEquals("", fila.moduleTitle)
    }

    @Test
    fun `un 401 se traduce a sesion expirada`() = runTest {
        coEvery { enrollmentApi.getMyEnrollments() } throws HttpException(Response.error<Any>(401, "".toResponseBody()))

        val resultado = repositorio.getSubtopicCoverage("1")

        assertTrue((resultado as DomainResult.Failure).error is DomainError.Unauthorized)
    }

    @Test
    fun `sin conexion se traduce a error de red`() = runTest {
        coEvery { enrollmentApi.getMyEnrollments() } throws IOException("backend apagado")

        val resultado = repositorio.getSubtopicCoverage("1")

        assertTrue((resultado as DomainResult.Failure).error is DomainError.Network)
    }

    @Test
    fun `con un curso elegido pide la cobertura de ese grupo sin buscar matriculas`() = runTest {
        coEvery { coverageApi.getMyCoverage(30) } returns PersonalCoverageDto(groupId = 30, subtopics = listOf(subtema(1, "COVERED")))

        val filas = (repositorio.getSubtopicCoverage("1", groupId = "30") as DomainResult.Success).data

        assertEquals(listOf("1"), filas.map { it.subtopicId })
        coVerify(exactly = 0) { enrollmentApi.getMyEnrollments() }
    }
}
