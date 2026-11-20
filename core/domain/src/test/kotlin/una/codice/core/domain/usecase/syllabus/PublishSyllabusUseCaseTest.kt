package una.codice.core.domain.usecase.syllabus

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.CourseModule
import una.codice.core.domain.model.Subtopic
import una.codice.core.domain.model.Syllabus

/**
 * Tests de [PublishSyllabusUseCase].
 *
 * El foco es el criterio de aceptación del issue #32: *"un fallo deja el
 * curso sin temario, no a medias"*. Por eso varios tests no miran solo el
 * resultado devuelto, sino que comprueban contra el fake **qué llamadas se
 * hicieron y cuáles no**.
 */
class PublishSyllabusUseCaseTest {

    private fun temarioValido() = Syllabus(
        courseId = "curso-1",
        modules = listOf(
            CourseModule(
                id = "mod-1",
                number = 1,
                title = "Fundamentos",
                subtopics = listOf(
                    Subtopic(
                        id = "sub-1",
                        code = "1.1",
                        title = "Introducción",
                        position = 0,
                        objectives = listOf("Explicar el ciclo de vida"),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `un temario valido se guarda y luego se publica, en ese orden`() = runTest {
        val repositorio = FakeCourseRepository()
        val publicar = PublishSyllabusUseCase(repositorio, ValidateSyllabusUseCase())

        val resultado = publicar(temarioValido())

        assertTrue(resultado is PublishResult.Published)
        assertEquals(1, repositorio.savedDrafts.size)
        assertEquals(listOf("curso-1"), repositorio.publishedCourses)
    }

    /**
     * **El test más importante del issue #32.**
     *
     * Con un problema bloqueante no basta con devolver el rechazo: hay que
     * comprobar que **no se tocó el repositorio en absoluto**. Un caso de uso
     * que guardara primero y validara después dejaría el borrador escrito en
     * el servidor pese al rechazo.
     */
    @Test
    fun `un temario con problemas bloqueantes no llega al repositorio`() = runTest {
        val repositorio = FakeCourseRepository()
        val publicar = PublishSyllabusUseCase(repositorio, ValidateSyllabusUseCase())

        val resultado = publicar(Syllabus(courseId = "curso-1"))

        assertTrue(repositorio.savedDrafts.isEmpty())
        assertTrue(repositorio.publishedCourses.isEmpty())
        val rechazo = resultado as PublishResult.Rejected
        assertEquals(listOf(SyllabusIssue.EmptySyllabus), rechazo.issues)
    }

    /**
     * Si el guardado falla, la publicación **ni se intenta**: es exactamente
     * el "no a medias" del criterio de aceptación. Sin esta comprobación, un
     * curso podría quedar marcado como publicado apuntando al temario viejo.
     */
    @Test
    fun `si el guardado falla no se intenta publicar`() = runTest {
        val repositorio = FakeCourseRepository(saveFailure = DomainError.Network())
        val publicar = PublishSyllabusUseCase(repositorio, ValidateSyllabusUseCase())

        val resultado = publicar(temarioValido())

        assertTrue(repositorio.publishedCourses.isEmpty())
        assertEquals(DomainError.Network().message, (resultado as PublishResult.Failed).message)
    }

    @Test
    fun `si la publicacion falla se reporta el error del repositorio`() = runTest {
        val repositorio = FakeCourseRepository(publishFailure = DomainError.Network())
        val publicar = PublishSyllabusUseCase(repositorio, ValidateSyllabusUseCase())

        val resultado = publicar(temarioValido())

        // El borrador sí quedó guardado: eso es correcto, no es "a medias".
        assertEquals(1, repositorio.savedDrafts.size)
        assertEquals(DomainError.Network().message, (resultado as PublishResult.Failed).message)
    }

    /**
     * Las advertencias no bloqueantes no impiden publicar, pero viajan en el
     * resultado para que la pantalla pueda decir "publicado, con 2 subtemas
     * sin objetivos".
     */
    @Test
    fun `las advertencias no bloquean pero viajan en el resultado`() = runTest {
        val repositorio = FakeCourseRepository()
        val publicar = PublishSyllabusUseCase(repositorio, ValidateSyllabusUseCase())
        val conAdvertencia = Syllabus(
            courseId = "curso-1",
            modules = listOf(
                CourseModule(
                    id = "mod-1",
                    number = 1,
                    title = "Fundamentos",
                    subtopics = listOf(
                        Subtopic(id = "sub-1", code = "1.1", title = "Intro", position = 0, objectives = emptyList()),
                    ),
                ),
            ),
        )

        val resultado = publicar(conAdvertencia)

        val publicado = resultado as PublishResult.Published
        assertTrue(publicado.warnings.contains(SyllabusIssue.SubtopicWithoutObjectives("1.1")))
        assertEquals(listOf("curso-1"), repositorio.publishedCourses)
    }
}
