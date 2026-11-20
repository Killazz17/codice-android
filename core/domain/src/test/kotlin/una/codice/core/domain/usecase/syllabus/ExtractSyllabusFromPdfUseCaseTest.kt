package una.codice.core.domain.usecase.syllabus

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.result.DomainResult

class ExtractSyllabusFromPdfUseCaseTest {

    @Test
    fun `pdf vacio retorna error de validacion`() = runTest {
        val repositorio = FakeCourseRepository()
        val extractSyllabus = ExtractSyllabusFromPdfUseCase(repositorio)

        val resultado = extractSyllabus("curso-1", byteArrayOf(), "programa.pdf")

        assertTrue(resultado is DomainResult.Failure)
        assertTrue((resultado as DomainResult.Failure).error is DomainError.Validation)
    }

    @Test
    fun `pdf valido extrae el temario exitosamente`() = runTest {
        val repositorio = FakeCourseRepository()
        val extractSyllabus = ExtractSyllabusFromPdfUseCase(repositorio)
        val pdfBytes = "CONTENIDOS 1-Liderazgo A-Fundamentos".toByteArray()

        val resultado = extractSyllabus("curso-1", pdfBytes, "programa.pdf")

        assertTrue(resultado is DomainResult.Success)
        assertEquals("curso-1", (resultado as DomainResult.Success).data.courseId)
    }
}
