package una.codice.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SyllabusEditingTest {

    private fun subtema(id: String, code: String, position: Int) =
        Subtopic(id = id, code = code, title = "Subtema $code", position = position)

    private fun modulo(id: String, number: Int, vararg subtemas: Subtopic) =
        CourseModule(id = id, number = number, title = "Módulo $number", subtopics = subtemas.toList())

    private val temario = Syllabus(
        courseId = "1",
        modules = listOf(
            modulo("a", 1, subtema("a1", "1.1", 0), subtema("a2", "1.2", 1)),
            modulo("b", 2, subtema("b1", "2.1", 0)),
            modulo("c", 3, subtema("c1", "3.1", 0)),
        ),
    )

    // --- mover módulos ---

    @Test
    fun `bajar un modulo intercambia su lugar y sus numeros con el siguiente`() {
        val resultado = temario.moveModule("a", MoveDirection.DOWN)

        assertEquals(listOf("b", "a", "c"), resultado.modules.map { it.id })
        assertEquals(listOf(1, 2, 3), resultado.modules.map { it.number })
    }

    @Test
    fun `subir un modulo lo pone antes del anterior`() {
        val resultado = temario.moveModule("c", MoveDirection.UP)

        assertEquals(listOf("a", "c", "b"), resultado.modules.map { it.id })
        assertEquals(listOf(1, 2, 3), resultado.modules.map { it.number })
    }

    // Los códigos cuentan lo mismo que antes: el módulo "a" ahora es el 2, así que 1.1 pasa a 2.1.
    @Test
    fun `al mover un modulo el prefijo de los codigos de sus subtemas sigue a su numero`() {
        val resultado = temario.moveModule("a", MoveDirection.DOWN)

        val a = resultado.modules.first { it.id == "a" }
        val b = resultado.modules.first { it.id == "b" }
        assertEquals(2, a.number)
        assertEquals(listOf("2.1", "2.2"), a.subtopics.map { it.code })
        assertEquals(1, b.number)
        assertEquals(listOf("1.1"), b.subtopics.map { it.code })
    }

    @Test
    fun `un codigo sin el prefijo del modulo no se toca`() {
        val especial = Syllabus(
            courseId = "1",
            modules = listOf(
                modulo("a", 1, subtema("a1", "A", 0), subtema("a2", "1.2", 1)),
                modulo("b", 2),
            ),
        )

        val resultado = especial.moveModule("a", MoveDirection.DOWN)

        assertEquals(listOf("A", "2.2"), resultado.modules.first { it.id == "a" }.subtopics.map { it.code })
    }

    // Si los números tienen huecos (1, 2, 4), mover reparte esos mismos números: no inventa el 3.
    @Test
    fun `mover conserva los numeros que ya tenia el temario`() {
        val conHuecos = Syllabus(
            courseId = "1",
            modules = listOf(modulo("a", 1), modulo("b", 2), modulo("d", 4)),
        )

        val resultado = conHuecos.moveModule("b", MoveDirection.DOWN)

        assertEquals(listOf("a", "d", "b"), resultado.modules.map { it.id })
        assertEquals(listOf(1, 2, 4), resultado.modules.map { it.number })
    }

    @Test
    fun `el primer modulo no sube ni el ultimo baja`() {
        assertSame(temario, temario.moveModule("a", MoveDirection.UP))
        assertSame(temario, temario.moveModule("c", MoveDirection.DOWN))
    }

    @Test
    fun `un modulo que no existe deja el temario como estaba`() {
        assertSame(temario, temario.moveModule("zzz", MoveDirection.UP))
    }

    @Test
    fun `mover ordena por numero aunque la lista venga desordenada`() {
        val desordenado = temario.copy(modules = temario.modules.reversed())

        val resultado = desordenado.moveModule("a", MoveDirection.DOWN)

        assertEquals(listOf("b", "a", "c"), resultado.modules.map { it.id })
    }

    // --- mover subtemas ---

    @Test
    fun `bajar un subtema cambia las posiciones y deja los codigos`() {
        val modulo = temario.modules.first { it.id == "a" }

        val resultado = modulo.moveSubtopic("a1", MoveDirection.DOWN)

        assertEquals(listOf("a2", "a1"), resultado.subtopics.map { it.id })
        assertEquals(listOf(0, 1), resultado.subtopics.map { it.position })
        assertEquals(listOf("1.2", "1.1"), resultado.subtopics.map { it.code })
    }

    @Test
    fun `subir un subtema lo pone antes del anterior`() {
        val modulo = temario.modules.first { it.id == "a" }

        val resultado = modulo.moveSubtopic("a2", MoveDirection.UP)

        assertEquals(listOf("a2", "a1"), resultado.subtopics.map { it.id })
    }

    @Test
    fun `las posiciones quedan sin huecos despues de mover`() {
        val conHuecos = modulo("a", 1, subtema("x", "1.1", 0), subtema("y", "1.2", 5), subtema("z", "1.3", 9))

        val resultado = conHuecos.moveSubtopic("y", MoveDirection.DOWN)

        assertEquals(listOf("x", "z", "y"), resultado.subtopics.map { it.id })
        assertEquals(listOf(0, 1, 2), resultado.subtopics.map { it.position })
    }

    @Test
    fun `un subtema en el borde o que no existe no cambia nada`() {
        val modulo = temario.modules.first { it.id == "a" }

        assertSame(modulo, modulo.moveSubtopic("a1", MoveDirection.UP))
        assertSame(modulo, modulo.moveSubtopic("a2", MoveDirection.DOWN))
        assertSame(modulo, modulo.moveSubtopic("zzz", MoveDirection.UP))
    }

    // --- confianza ---

    @Test
    fun `un modulo o subtema escrito a mano nunca se marca como dudoso`() {
        assertFalse(modulo("a", 1).isLowConfidence)
        assertFalse(subtema("a1", "1.1", 0).isLowConfidence)
    }

    @Test
    fun `bajo 0 punto 70 de confianza se marca como dudoso y desde 0 punto 70 no`() {
        assertTrue(subtema("a1", "1.1", 0).copy(confidence = 0.69f).isLowConfidence)
        assertFalse(subtema("a1", "1.1", 0).copy(confidence = 0.70f).isLowConfidence)
        assertTrue(modulo("a", 1).copy(confidence = 0f).isLowConfidence)
        assertFalse(modulo("a", 1).copy(confidence = 0.95f).isLowConfidence)
    }
}
