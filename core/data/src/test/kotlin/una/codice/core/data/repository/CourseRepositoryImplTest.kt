package una.codice.core.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import una.codice.core.data.network.teacher.ExtractedModuleDto
import una.codice.core.data.network.teacher.ExtractedSubtopicDto
import una.codice.core.data.network.teacher.ExtractedSyllabusDto
import una.codice.core.data.network.teacher.TeacherApiService
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.result.DomainResult

/**
 * IA-02: el temario que propone la IA del backend. Si la IA falla, el error llega a la pantalla; nunca se arma un
 * temario de relleno a partir del nombre del archivo.
 */
class CourseRepositoryImplTest {

    private val pdf = "%PDF-1.4 programa".toByteArray()

    private fun propuesta() = ExtractedSyllabusDto(
        courseId = 25,
        modules = listOf(
            ExtractedModuleDto(
                number = 1,
                title = "Fundamentos de Android",
                confidence = 0.95,
                subtopics = listOf(
                    ExtractedSubtopicDto("1.1", "Ciclo de vida de una Activity", 0.92, listOf("Explicar los estados de una Activity")),
                    ExtractedSubtopicDto("1.2", "Jetpack Compose", 0.45, emptyList()),
                ),
            ),
        ),
    )

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun `la extraccion arma el temario del backend y lo deja como borrador sin publicar`() = runTest {
        val api = mockk<TeacherApiService>()
        coEvery { api.extractSyllabusFromPdf(25L, any()) } returns propuesta()
        val repositorio = CourseRepositoryImpl(api, mockk())

        val temario = (repositorio.extractSyllabusFromPdf("25", pdf, "programa.pdf") as DomainResult.Success).data

        assertEquals("25", temario.courseId)
        assertFalse(temario.isPublished)
        val modulo = temario.modules.single()
        assertEquals("Fundamentos de Android", modulo.title)
        assertEquals(listOf("1.1", "1.2"), modulo.subtopics.map { it.code })
        assertEquals(listOf("Explicar los estados de una Activity"), modulo.subtopics.first().objectives)
        // Quedó guardado como borrador para que el profesor lo revise antes de publicar.
        assertEquals(temario, repositorio.observeSyllabus("25").first())
    }

    @Test
    fun `si la IA falla se informa el motivo y no se inventa un temario`() = runTest {
        val api = mockk<TeacherApiService>()
        val repositorio = CourseRepositoryImpl(api, mockk())

        val mensajes = listOf(503, 400, 403, 413).map { codigo ->
            coEvery { api.extractSyllabusFromPdf(25L, any()) } throws httpError(codigo)
            ((repositorio.extractSyllabusFromPdf("25", pdf, "programa.pdf")) as DomainResult.Failure).error.message
        }

        assertTrue(mensajes[0], "IA no está disponible" in mensajes[0])
        assertTrue(mensajes[1], "PDF válido" in mensajes[1])
        assertTrue(mensajes[2], "profesor dueño" in mensajes[2])
        assertTrue(mensajes[3], "pesado" in mensajes[3])
    }

    @Test
    fun `un id de curso que no es numerico no llama al backend ni usa un curso inventado`() = runTest {
        val api = mockk<TeacherApiService>()
        val repositorio = CourseRepositoryImpl(api, mockk())

        val resultado = repositorio.extractSyllabusFromPdf("EIF411", pdf, "programa.pdf")

        assertTrue((resultado as DomainResult.Failure).error is DomainError.Unknown)
        coVerify(exactly = 0) { api.extractSyllabusFromPdf(any(), any()) }
    }

    @Test
    fun `sin conexion es un error de red`() = runTest {
        val api = mockk<TeacherApiService>()
        coEvery { api.extractSyllabusFromPdf(25L, any()) } throws IOException("sin internet")

        val resultado = CourseRepositoryImpl(api, mockk()).extractSyllabusFromPdf("25", pdf, "programa.pdf")

        assertTrue((resultado as DomainResult.Failure).error is DomainError.Network)
    }
}
