package una.codice.core.domain.usecase.syllabus

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.CourseModule
import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.result.DomainResult

class LoadSyllabusUseCaseTest {

    private val repository = FakeCourseRepository()
    private val useCase = LoadSyllabusUseCase(repository)

    @Test
    fun `entrega el temario que tiene el servidor`() = runTest {
        val temario = Syllabus(courseId = "7", modules = listOf(CourseModule("m1", 1, "Intro")), isPublished = true)
        repository.loadResult = DomainResult.Success(temario)

        assertEquals(DomainResult.Success(temario), useCase("7"))
        assertEquals(listOf("7"), repository.loadedCourses)
    }

    @Test
    fun `propaga el error cuando no se pudo leer`() = runTest {
        repository.loadResult = DomainResult.Failure(DomainError.Network())

        assertEquals(DomainResult.Failure(DomainError.Network()), useCase("7"))
    }
}
