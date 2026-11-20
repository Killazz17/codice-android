package una.codice.core.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import una.codice.core.data.ai.AIProvider
import una.codice.core.data.ai.GeminiAIStrategy
import una.codice.core.data.network.coverage.SubtopicCoverageDto
import una.codice.core.data.network.enrollment.EnrollmentApiService
import una.codice.core.data.network.enrollment.EnrollmentDto
import una.codice.core.data.network.isoInstantToEpochMillis
import una.codice.core.data.network.note.CreatedNoteDto
import una.codice.core.data.network.note.NoteApiService
import una.codice.core.data.network.note.NoteCreateRequestDto
import una.codice.core.data.network.note.NoteDto
import una.codice.core.data.network.note.PageDto
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.CoverageStatus
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.SyncStatus
import una.codice.core.domain.result.DomainResult

/**
 * Tests de [NoteRepositoryImpl] contra un [NoteApiService] mockeado con MockK.
 */
class NoteRepositoryImplTest {

    private fun noteDto(id: Long, syncStatus: String = "SYNCED") = NoteDto(
        id = id,
        subtopicId = 44,
        syncStatus = syncStatus,
        createdAt = "2026-10-12T15:00:00.123456Z",
        confirmedText = "Un apunte de prueba",
        pageCount = 2,
        subtopicCode = "4.4",
        subtopicTitle = "Servicios REST",
        moduleId = 4,
        moduleLabel = "Módulo 4 · Servicios",
    )

    private fun page(vararg ids: Long, page: Int = 0, totalPages: Int = 1) =
        PageDto(content = ids.map { noteDto(it) }, page = page, size = 100, totalElements = ids.size.toLong(), totalPages = totalPages)

    private fun createRepository(
        api: NoteApiService,
        enrollments: EnrollmentApiService = mockk(),
    ): NoteRepositoryImpl {
        val aiProvider = AIProvider(GeminiAIStrategy(api))
        return NoteRepositoryImpl(api, enrollments, aiProvider)
    }

    private fun enrollment(id: Long, groupId: Long, status: String = "ACTIVE") = EnrollmentDto(
        id = id,
        groupId = groupId,
        courseId = 1,
        status = status,
    )

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    private fun draft(id: String = "borrador-1", subtopicId: String? = "44") = Note(
        id = id,
        subtopicId = subtopicId,
        moduleId = null,
        moduleLabel = null,
        subtopicCode = null,
        subtopicTitle = null,
        confirmedText = "Texto confirmado",
        pageCount = 1,
        syncStatus = SyncStatus.QUEUED,
        createdAt = 0L,
    )

    @Test
    fun `observeNotes traduce los DTO del backend a modelos de dominio`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.getNotes(0, 100, null) } returns page(1, 2)

        val notas = createRepository(api).observeNotes("1").first()

        assertEquals(listOf("1", "2"), notas.map { it.id })
        val nota = notas.first()
        assertEquals("44", nota.subtopicId)
        assertEquals("4", nota.moduleId)
        assertEquals("Módulo 4 · Servicios", nota.moduleLabel)
        assertEquals("4.4", nota.subtopicCode)
        assertEquals(2, nota.pageCount)
        assertEquals(SyncStatus.SYNCED, nota.syncStatus)
        assertEquals(isoInstantToEpochMillis("2026-10-12T15:00:00.123Z"), nota.createdAt)
    }

    @Test
    fun `observeNotes pide todas las paginas del backend`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.getNotes(0, 100, null) } returns page(1, 2, totalPages = 2)
        coEvery { api.getNotes(1, 100, null) } returns page(3, page = 1, totalPages = 2)

        val notas = createRepository(api).observeNotes("1").first()

        assertEquals(listOf("1", "2", "3"), notas.map { it.id })
    }

    @Test
    fun `los estados pendientes del backend se muestran como en cola`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.getNotes(0, 100, null) } returns PageDto(
            content = listOf(noteDto(1, "QUEUED"), noteDto(2, "UPLOADING"), noteDto(3, "FAILED")),
            totalPages = 1,
        )

        val notas = createRepository(api).observeNotes("1").first()

        assertTrue(notas.all { it.syncStatus == SyncStatus.QUEUED })
    }

    // Sin una lista anterior que conservar, la lista sale vacía, pero el error dice que fue un fallo y no "sin apuntes".
    @Test
    fun `si la llamada falla sin lista anterior emite vacio y avisa el error`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.getNotes(any(), any(), any()) } throws IOException("sin red")
        val repository = createRepository(api)

        val notas = repository.observeNotes("1").first()

        assertTrue(notas.isEmpty())
        assertEquals(DomainError.Network(), repository.loadError.first())
    }

    @Test
    fun `un 401 al listar se avisa como sesion vencida`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.getNotes(any(), any(), any()) } throws httpError(401)
        val repository = createRepository(api)

        repository.observeNotes("1").first()

        assertEquals(DomainError.Unauthorized(), repository.loadError.first())
    }

    // Una consulta que falla después de una buena no vacía la pantalla: se sigue con la última lista del servidor.
    @Test
    fun `si una consulta falla despues de una buena conserva la ultima lista`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.getNotes(0, 100, null) } returns page(1, 2) andThenThrows IOException("sin red")
        val repository = createRepository(api)

        val primera = repository.observeNotes("1").first()
        val segunda = repository.observeNotes("1").first()

        assertEquals(listOf("1", "2"), primera.map { it.id })
        assertEquals(listOf("1", "2"), segunda.map { it.id })
        assertEquals(DomainError.Network(), repository.loadError.first())
    }

    @Test
    fun `una consulta buena despues de un fallo limpia el error`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.getNotes(0, 100, null) } throws IOException("sin red") andThen page(1)
        val repository = createRepository(api)

        repository.observeNotes("1").first()
        assertEquals(DomainError.Network(), repository.loadError.first())

        val notas = repository.observeNotes("1").first()

        assertEquals(listOf("1"), notas.map { it.id })
        assertNull(repository.loadError.first())
    }

    // La lista de un curso no sirve de respaldo para otro.
    @Test
    fun `la lista conservada es del mismo curso`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.getNotes(0, 100, 20L) } returns page(7)
        coEvery { api.getNotes(0, 100, 30L) } throws IOException("sin red")
        val repository = createRepository(api)

        repository.observeNotes("1", groupId = "20").first()
        val deOtroCurso = repository.observeNotes("1", groupId = "30").first()

        assertTrue(deOtroCurso.isEmpty())
    }

    @Test
    fun `refreshNotes vuelve a pedir los apuntes`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.getNotes(0, 100, null) } returns page(1)
        val repository = createRepository(api)

        val emitidas = mutableListOf<List<String>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeNotes("1").collect { emitidas += it.map { nota -> nota.id } }
        }
        repository.refreshNotes()
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(2, emitidas.size)
        coVerify(exactly = 2) { api.getNotes(0, 100, null) }
    }

    @Test
    fun `la fecha ISO del backend se convierte a milisegundos en UTC`() {
        assertEquals(1_791_817_200_123L, isoInstantToEpochMillis("2026-10-12T15:00:00.123456Z"))
        assertEquals(1_791_774_000_000L, isoInstantToEpochMillis("2026-10-12T03:00:00Z"))
        assertEquals(0L, isoInstantToEpochMillis("no es una fecha"))
    }

    @Test
    fun `con un curso elegido solo pide los apuntes de ese grupo`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.getNotes(0, 100, 20L) } returns page(7)

        val notas = createRepository(api).observeNotes("1", groupId = "20").first()

        assertEquals(listOf("7"), notas.map { it.id })
    }

    @Test
    fun `saveNote crea el apunte en la matricula aprobada del grupo`() = runTest {
        val api = mockk<NoteApiService>()
        val enrollments = mockk<EnrollmentApiService>()
        coEvery { enrollments.getMyEnrollments() } returns listOf(enrollment(5, groupId = 10), enrollment(6, groupId = 20))
        val request = slot<NoteCreateRequestDto>()
        coEvery { api.createNote(capture(request)) } returns CreatedNoteDto(id = 99)

        val result = createRepository(api, enrollments).saveNote("20", draft())

        assertTrue(result is DomainResult.Success)
        val saved = (result as DomainResult.Success).data
        assertEquals("99", saved.id)
        assertEquals(SyncStatus.SYNCED, saved.syncStatus)
        assertEquals(6L, request.captured.enrollmentId)
        assertEquals(44L, request.captured.subtopicId)
        assertEquals("app-borrador-1", request.captured.clientRequestId)
        assertEquals("Texto confirmado", request.captured.confirmedText)
    }

    @Test
    fun `saveNote falla sin llamar al servidor si no hay matricula aprobada en el grupo`() = runTest {
        val api = mockk<NoteApiService>()
        val enrollments = mockk<EnrollmentApiService>()
        coEvery { enrollments.getMyEnrollments() } returns listOf(
            enrollment(5, groupId = 20, status = "PENDING"),
            enrollment(6, groupId = 10),
        )

        val result = createRepository(api, enrollments).saveNote("20", draft())

        assertTrue(result is DomainResult.Failure)
        assertEquals("No tienes una matrícula aprobada en este curso", (result as DomainResult.Failure).error.message)
        coVerify(exactly = 0) { api.createNote(any()) }
    }

    @Test
    fun `saveNote con un grupo que no es numerico falla sin pedir nada`() = runTest {
        val api = mockk<NoteApiService>()

        val result = createRepository(api).saveNote("c1", draft())

        assertTrue(result is DomainResult.Failure)
        assertEquals("No se pudo identificar el curso del apunte", (result as DomainResult.Failure).error.message)
    }

    @Test
    fun `saveNote explica el 403 del servidor`() = runTest {
        val api = mockk<NoteApiService>()
        val enrollments = mockk<EnrollmentApiService>()
        coEvery { enrollments.getMyEnrollments() } returns listOf(enrollment(6, groupId = 20))
        coEvery { api.createNote(any()) } throws httpError(403)

        val result = createRepository(api, enrollments).saveNote("20", draft())

        assertTrue(result is DomainResult.Failure)
        assertEquals(
            "No puedes guardar apuntes en este curso: revisa que tu matrícula esté aprobada",
            (result as DomainResult.Failure).error.message,
        )
    }

    @Test
    fun `saveNote sin subtema asignado manda el apunte sin clasificar`() = runTest {
        val api = mockk<NoteApiService>()
        val enrollments = mockk<EnrollmentApiService>()
        coEvery { enrollments.getMyEnrollments() } returns listOf(enrollment(6, groupId = 20))
        val request = slot<NoteCreateRequestDto>()
        coEvery { api.createNote(capture(request)) } returns CreatedNoteDto(id = 1)

        createRepository(api, enrollments).saveNote("20", draft(subtopicId = null))

        assertEquals(null, request.captured.subtopicId)
    }

    @Test
    fun `despues de guardar vuelve a pedir la lista de apuntes`() = runTest {
        val api = mockk<NoteApiService>()
        val enrollments = mockk<EnrollmentApiService>()
        coEvery { enrollments.getMyEnrollments() } returns listOf(enrollment(6, groupId = 20))
        coEvery { api.createNote(any()) } returns CreatedNoteDto(id = 99)
        coEvery { api.getNotes(0, 100, 20L) } returns page(1)
        val repository = createRepository(api, enrollments)

        val before = repository.observeNotes("1", groupId = "20").first()
        coEvery { api.getNotes(0, 100, 20L) } returns page(1, 99)
        repository.saveNote("20", draft())
        val after = repository.observeNotes("1", groupId = "20").first()

        assertEquals(listOf("1"), before.map { it.id })
        assertEquals(listOf("1", "99"), after.map { it.id })
    }

    @Test
    fun `auditNote devuelve los subtemas que toco el apunte`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.auditNote(99L) } returns listOf(
            SubtopicCoverageDto(44, "4.4", "Servicios REST", "COVERED", moduleNumber = 4, moduleTitle = "Servicios"),
        )

        val result = createRepository(api).auditNote("99")

        assertTrue(result is DomainResult.Success)
        val subtopic = (result as DomainResult.Success).data.single()
        assertEquals("44", subtopic.subtopicId)
        assertEquals(CoverageStatus.COVERED, subtopic.status)
        assertEquals(4, subtopic.moduleNumber)
    }

    @Test
    fun `auditNote con un apunte que no se guardo falla sin llamar al servidor`() = runTest {
        val api = mockk<NoteApiService>()

        val result = createRepository(api).auditNote("borrador-1")

        assertTrue(result is DomainResult.Failure)
        assertEquals("El apunte todavía no se guardó en el servidor", (result as DomainResult.Failure).error.message)
        coVerify(exactly = 0) { api.auditNote(any()) }
    }

    @Test
    fun `auditNote explica los errores del servidor`() = runTest {
        val expected = mapOf(
            403 to "Solo el dueño del apunte puede auditarlo",
            404 to "No se encontró el apunte en el servidor",
            422 to "El apunte no tiene texto confirmado",
            503 to "La IA no está disponible en este momento",
        )
        expected.forEach { (code, message) ->
            val api = mockk<NoteApiService>()
            coEvery { api.auditNote(any()) } throws httpError(code)

            val result = createRepository(api).auditNote("99")

            assertTrue("código $code", result is DomainResult.Failure)
            assertEquals(message, (result as DomainResult.Failure).error.message)
        }
    }
}
