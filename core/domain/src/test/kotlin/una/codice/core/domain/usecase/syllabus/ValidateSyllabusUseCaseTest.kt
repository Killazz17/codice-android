package una.codice.core.domain.usecase.syllabus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import una.codice.core.domain.model.CourseModule
import una.codice.core.domain.model.Subtopic
import una.codice.core.domain.model.Syllabus

/**
 * Tests de [ValidateSyllabusUseCase].
 *
 * Cada regla que se prueba aquí corresponde a una restricción real del
 * modelo de datos (`CODICE_2.HTM#ddl`): `UNIQUE (course_id, number)`,
 * `UNIQUE (module_id, code)` y las columnas `title` declaradas `NOT NULL`.
 * La idea es cortar en el cliente lo que la base rechazaría igual, y avisar
 * de lo que la base aceptaría pero dejaría el mapa de cobertura inservible.
 */
class ValidateSyllabusUseCaseTest {

    private val validar = ValidateSyllabusUseCase()

    private fun subtema(
        code: String,
        title: String = "Subtema $code",
        position: Int = 0,
        objectives: List<String> = listOf("Objetivo de $code"),
    ) = Subtopic(id = "sub-$code", code = code, title = title, position = position, objectives = objectives)

    private fun modulo(
        number: Int,
        title: String = "Módulo $number",
        subtopics: List<Subtopic> = listOf(subtema("$number.1")),
    ) = CourseModule(id = "mod-$number", number = number, title = title, subtopics = subtopics)

    private fun temario(vararg modules: CourseModule) =
        Syllabus(courseId = "curso-1", modules = modules.toList())

    @Test
    fun `un temario bien armado no reporta ningun problema`() {
        val issues = validar(temario(modulo(1), modulo(2)))

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `un temario sin modulos se reporta como vacio y bloquea`() {
        val issues = validar(Syllabus(courseId = "curso-1"))

        assertEquals(listOf(SyllabusIssue.EmptySyllabus), issues)
        assertTrue(issues.hasBlockingIssues())
    }

    /** `UNIQUE (course_id, number)`: dos módulos 4 no caben en la base. */
    @Test
    fun `dos modulos con el mismo numero bloquean la publicacion`() {
        val issues = validar(temario(modulo(4), modulo(4, title = "Otro módulo 4")))

        assertTrue(issues.contains(SyllabusIssue.DuplicateModuleNumber(4)))
        assertTrue(issues.hasBlockingIssues())
    }

    /**
     * Un número repetido tres veces se reporta una sola vez: al profesor le
     * sirve saber *cuál* módulo arreglar, no cuántas copias hay.
     */
    @Test
    fun `un numero repetido tres veces se reporta una sola vez`() {
        val issues = validar(temario(modulo(4), modulo(4), modulo(4)))

        assertEquals(1, issues.count { it is SyllabusIssue.DuplicateModuleNumber })
    }

    /** `UNIQUE (module_id, code)`, dentro del módulo. */
    @Test
    fun `dos subtemas con el mismo codigo en un modulo bloquean`() {
        val issues = validar(
            temario(modulo(4, subtopics = listOf(subtema("4.4"), subtema("4.4", title = "Repetido")))),
        )

        assertTrue(issues.contains(SyllabusIssue.DuplicateSubtopicCode(4, "4.4")))
        assertTrue(issues.hasBlockingIssues())
    }

    /**
     * El mismo código en módulos distintos es legal: la restricción de la
     * base es por módulo, no por curso. Este test evita que alguien
     * "arregle" la validación endureciéndola de más.
     */
    @Test
    fun `el mismo codigo en modulos distintos es valido`() {
        val issues = validar(
            temario(
                modulo(1, subtopics = listOf(subtema("1.1"))),
                modulo(2, subtopics = listOf(subtema("1.1"))),
            ),
        )

        assertTrue(issues.none { it is SyllabusIssue.DuplicateSubtopicCode })
    }

    /**
     * `"4.4 "` y `"4.4"` son el mismo código para cualquier lector, pero la
     * base los aceptaría como distintos. Se comparan normalizados.
     */
    @Test
    fun `dos codigos que solo difieren en espacios cuentan como repetidos`() {
        val issues = validar(
            temario(modulo(4, subtopics = listOf(subtema("4.4"), subtema("4.4 ")))),
        )

        assertTrue(issues.any { it is SyllabusIssue.DuplicateSubtopicCode })
    }

    @Test
    fun `un modulo sin titulo bloquea`() {
        val issues = validar(temario(modulo(3, title = "   ")))

        assertTrue(issues.contains(SyllabusIssue.BlankModuleTitle(3)))
        assertTrue(issues.hasBlockingIssues())
    }

    @Test
    fun `un subtema sin titulo bloquea`() {
        val issues = validar(
            temario(modulo(3, subtopics = listOf(subtema("3.1", title = "")))),
        )

        assertTrue(issues.contains(SyllabusIssue.BlankSubtopicTitle(3, "3.1")))
        assertTrue(issues.hasBlockingIssues())
    }

    /**
     * Un módulo vacío no rompe nada en la base, pero no aporta al mapa de
     * cobertura: se avisa sin impedir publicar.
     */
    @Test
    fun `un modulo sin subtemas avisa pero no bloquea`() {
        val issues = validar(temario(modulo(5, subtopics = emptyList())))

        assertEquals(listOf(SyllabusIssue.ModuleWithoutSubtopics(5)), issues)
        assertTrue(!issues.hasBlockingIssues())
    }

    /**
     * Sin objetivos, la llamada de IA 3 no tiene contra qué auditar el
     * apunte y ese subtema queda congelado en "sin apuntes" para siempre.
     * Es grave, pero es decisión del profesor: se avisa, no se bloquea.
     */
    @Test
    fun `un subtema sin objetivos avisa pero no bloquea`() {
        val issues = validar(
            temario(modulo(4, subtopics = listOf(subtema("4.4", objectives = emptyList())))),
        )

        assertTrue(issues.contains(SyllabusIssue.SubtopicWithoutObjectives("4.4")))
        assertTrue(!issues.hasBlockingIssues())
    }

    /** Un objetivo en blanco no cuenta como objetivo. */
    @Test
    fun `un subtema con un objetivo vacio cuenta como sin objetivos`() {
        val issues = validar(
            temario(modulo(4, subtopics = listOf(subtema("4.4", objectives = listOf("  ", ""))))),
        )

        assertTrue(issues.contains(SyllabusIssue.SubtopicWithoutObjectives("4.4")))
    }

    /**
     * Un temario con varios problemas los reporta TODOS de una vez, para que
     * el profesor no tenga que descubrirlos de uno en uno — ver el KDoc del
     * caso de uso.
     */
    @Test
    fun `se reportan todos los problemas juntos, no solo el primero`() {
        val issues = validar(
            temario(
                modulo(1, title = ""),
                modulo(1, subtopics = listOf(subtema("1.1", objectives = emptyList()))),
                modulo(9, subtopics = emptyList()),
            ),
        )

        assertTrue(issues.any { it is SyllabusIssue.DuplicateModuleNumber })
        assertTrue(issues.any { it is SyllabusIssue.BlankModuleTitle })
        assertTrue(issues.any { it is SyllabusIssue.SubtopicWithoutObjectives })
        assertTrue(issues.any { it is SyllabusIssue.ModuleWithoutSubtopics })
    }
}
