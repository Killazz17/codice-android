package una.codice.presentation.navigation

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import una.codice.core.domain.model.StudentGroup
import una.codice.core.domain.model.TeacherGroupSummary
import una.codice.R

class ProfileStatsTest {

    private fun grupo(alumnos: Int, cobertura: Float, solicitudes: Int) = TeacherGroupSummary(
        group = mockk<StudentGroup>(),
        activeStudentCount = alumnos,
        averageCoverage = cobertura,
        pendingRequestsCount = solicitudes,
    )

    @Test
    fun `el profesor suma sus grupos, alumnos y solicitudes y promedia la cobertura`() {
        val actividad = teacherActivity(listOf(grupo(10, 0.5f, 1), grupo(4, 0.7f, 2)))

        assertEquals(listOf("2", "14", "3"), actividad.stats.map { it.value })
        assertEquals(listOf(R.string.app_groups, R.string.app_students, R.string.app_requests), actividad.stats.map { it.labelRes })
        assertEquals(0.6f, actividad.coverage!!, 0.001f)
    }

    @Test
    fun `un profesor sin grupos ve ceros y no muestra cobertura`() {
        val actividad = teacherActivity(emptyList())

        assertEquals(listOf("0", "0", "0"), actividad.stats.map { it.value })
        assertNull(actividad.coverage)
    }

    @Test
    fun `si no se pudieron pedir los grupos todas las cifras quedan sin conocer`() {
        val actividad = teacherActivity(null)

        assertEquals(listOf(UNKNOWN, UNKNOWN, UNKNOWN), actividad.stats.map { it.value })
        assertNull(actividad.coverage)
    }

    @Test
    fun `el estudiante ve sus cursos, apuntes y repasos de hoy`() {
        val actividad = studentActivity(courses = 2, notes = 3, dueReviews = 1)

        assertEquals(listOf("2", "3", "1"), actividad.stats.map { it.value })
        assertEquals(listOf(R.string.app_courses, R.string.app_notes, R.string.app_reviews_today), actividad.stats.map { it.labelRes })
        assertNull(actividad.coverage)
    }

    @Test
    fun `una cifra del estudiante que no se pudo pedir queda sin conocer y las demas se conservan`() {
        val actividad = studentActivity(courses = 2, notes = null, dueReviews = 0)

        assertEquals(listOf("2", UNKNOWN, "0"), actividad.stats.map { it.value })
    }
}
