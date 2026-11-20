package una.codice.feature.notes.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.CoverageStatus
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.StudentGroup
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.model.SyncStatus
import una.codice.core.domain.model.TranscriptionBlock
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.auth.ObserveSessionUseCase
import una.codice.core.domain.usecase.note.ExtractNoteFromImageUseCase
import una.codice.core.domain.usecase.note.ExtractNoteFromPagesUseCase

/**
 * Cubre lo que `StudentCaptureViewModel` hace con el servidor: mostrar la cobertura real, guardar el apunte que el
 * estudiante confirmó y mandarlo a auditar. Los fakes devuelven lo que el test les diga, así que ninguna prueba
 * toca la red ni la IA.
 *
 * Igual que en `LibraryViewModelTest`, `Dispatchers.Main` se reemplaza antes de crear el ViewModel porque su
 * `init {}` ya lanza corrutinas; cada test las avanza a mano con `advanceUntilIdle()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudentCaptureViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var noteRepository: FakeNoteRepository
    private lateinit var coverageRepository: FakeCoverageRepository
    private lateinit var courseSelection: FakeCourseSelectionRepository
    private lateinit var viewModel: StudentCaptureViewModel

    @Before
    fun antesDeCadaTest() {
        Dispatchers.setMain(testDispatcher)

        noteRepository = FakeNoteRepository()
        coverageRepository = FakeCoverageRepository()
        // Un solo curso: queda elegido solo, como cuando el estudiante tiene una matrícula aprobada.
        courseSelection = FakeCourseSelectionRepository(listOf(grupo("g1")))
    }

    @After
    fun despuesDeCadaTest() {
        Dispatchers.resetMain()
    }

    private fun grupo(id: String) = StudentGroup(
        id = id,
        courseId = "curso-$id",
        courseName = "Curso $id",
        groupNumber = "01",
        period = "2026-II",
        professorName = "Profesora",
        courseCode = "EIF-$id",
    )

    private fun subtema(id: String, codigo: String, titulo: String, modulo: Int, estado: CoverageStatus) =
        SubtopicCoverage(
            subtopicId = id,
            subtopicCode = codigo,
            subtopicTitle = titulo,
            moduleNumber = modulo,
            moduleTitle = "Módulo $modulo",
            status = estado,
        )

    private val filas = listOf(
        subtema("s1", "1.1", "Introducción", 1, CoverageStatus.COVERED),
        subtema("s2", "1.2", "Variables", 1, CoverageStatus.PARTIAL),
        subtema("s3", "2.1", "Funciones", 2, CoverageStatus.NO_NOTES),
        subtema("s4", "2.2", "Recursión", 2, CoverageStatus.REINFORCED),
    )

    /** Crea el ViewModel con los fakes tal como los dejó el test y corre su `init {}`. */
    private fun abrirCaptura() {
        val authRepository = FakeAuthRepository(
            seed = Session(token = "t", userId = "estudiante-1", role = UserRole.STUDENT, userName = "Ana"),
        )
        viewModel = StudentCaptureViewModel(
            extractNoteFromImageUseCase = ExtractNoteFromImageUseCase(noteRepository),
            extractNoteFromPagesUseCase = ExtractNoteFromPagesUseCase(ExtractNoteFromImageUseCase(noteRepository)),
            observeSession = ObserveSessionUseCase(authRepository),
            noteRepository = noteRepository,
            coverageRepository = coverageRepository,
            courseSelection = courseSelection,
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /** Sube una foto con el fake de siempre para que el borrador quede abierto, como lo ve el estudiante. */
    private fun abrirBorrador() {
        viewModel.onFileBytesSelected(byteArrayOf(1, 2, 3), "content://galeria/apunte.jpg", "file")
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /** Abre el borrador con una transcripción de tres bloques: uno seguro (0.95) y dos dudosos (0.55 y 0.30). */
    private fun abrirBorradorConBloques() {
        coverageRepository.rowsByGroup = mapOf("g1" to filas)
        abrirCaptura()
        noteRepository.extractResult = DomainResult.Success(
            Note(
                id = "x",
                subtopicId = null,
                moduleId = null,
                moduleLabel = null,
                subtopicCode = null,
                subtopicTitle = null,
                // El texto completo de la IA no se usa cuando hay bloques: el apunte sale de ellos.
                confirmedText = "Texto transcrito",
                pageCount = 1,
                syncStatus = SyncStatus.QUEUED,
                createdAt = 0L,
                transcription = listOf(
                    TranscriptionBlock("claro", 0.95f),
                    TranscriptionBlock("dudoso", 0.55f),
                    TranscriptionBlock("ilegible", 0.30f),
                ),
            ),
        )
        abrirBorrador()
    }

    @Test
    fun `la cobertura sale de las filas reales del servidor`() = runTest(testDispatcher) {
        coverageRepository.rowsByGroup = mapOf("g1" to filas)

        abrirCaptura()

        val estado = viewModel.state.value
        assertEquals(filas, estado.coverage)
        assertEquals(2, estado.coveredSubtopicsCount)
        assertEquals(1, estado.reinforcedCount)
        assertEquals(50.0, estado.globalCoverage, 0.0)
    }

    // El mapa de un curso no sirve para otro: al cambiar se pide el del curso nuevo y no se mezclan.
    @Test
    fun `al cambiar de curso pide la cobertura del curso nuevo`() = runTest(testDispatcher) {
        val filasG2 = listOf(subtema("t1", "1.1", "Otro tema", 1, CoverageStatus.NO_NOTES))
        courseSelection.courses.value = listOf(grupo("g1"), grupo("g2"))
        coverageRepository.rowsByGroup = mapOf("g1" to filas, "g2" to filasG2)
        abrirCaptura()
        assertEquals("g1", viewModel.state.value.selectedCourseId)

        viewModel.onCourseSelected("g2")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("g2", viewModel.state.value.selectedCourseId)
        assertEquals(filasG2, viewModel.state.value.coverage)
        assertEquals(listOf<String?>("g1", "g2"), coverageRepository.requestedGroups)
    }

    @Test
    fun `guardar manda el borrador con el curso, el texto y el subtema elegido, y luego lo audita`() =
        runTest(testDispatcher) {
            coverageRepository.rowsByGroup = mapOf("g1" to filas)
            abrirCaptura()
            val tocado = filas[1].copy(status = CoverageStatus.COVERED)
            noteRepository.auditResult = DomainResult.Success(listOf(tocado))
            // Lo que el servidor devuelve al volver a pedir la cobertura después de auditar.
            coverageRepository.rowsByGroup = mapOf("g1" to listOf(filas[0], tocado, filas[2], filas[3]))

            viewModel.onUpdateDraftSubtopic("1.2 Variables")
            viewModel.onUpdateDraftText("Las variables guardan valores")
            viewModel.onSaveCapturedNote()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("g1", noteRepository.savedGroupId)
            assertEquals("Las variables guardan valores", noteRepository.savedDraft?.confirmedText)
            assertEquals("s2", noteRepository.savedDraft?.subtopicId)
            // Se audita el apunte con el id real que devolvió el servidor al guardar, no el del borrador.
            assertEquals("77", noteRepository.auditedNoteId)

            val estado = viewModel.state.value
            assertFalse(estado.isSaving)
            assertFalse(estado.isAuditing)
            assertFalse(estado.isEditModalOpen)
            assertNull(estado.errorMessage)
            assertEquals("• 1.2 Variables: cubierto", estado.infoMessage)
            assertEquals(CoverageStatus.COVERED, estado.coverage.first { it.subtopicId == "s2" }.status)
            assertEquals(listOf<String?>("g1", "g1"), coverageRepository.requestedGroups)
            assertEquals(listOf("77"), estado.capturedNotes.map { it.id })
        }

    @Test
    fun `si guardar falla el borrador sigue abierto, avisa el motivo y no audita`() = runTest(testDispatcher) {
        abrirCaptura()
        abrirBorrador()
        assertTrue(viewModel.state.value.isEditModalOpen)
        noteRepository.saveResult = DomainResult.Failure(DomainError.Unknown("El apunte no se pudo guardar: revisa el texto"))

        viewModel.onSaveCapturedNote()
        testDispatcher.scheduler.advanceUntilIdle()

        val estado = viewModel.state.value
        assertTrue(estado.isEditModalOpen)
        assertFalse(estado.isSaving)
        assertFalse(estado.isAuditing)
        assertEquals("El apunte no se pudo guardar: revisa el texto", estado.errorMessage)
        assertNull(noteRepository.auditedNoteId)
    }

    // Reintentar con el mismo borrador manda el mismo id: el repositorio lo usa para que el servidor no lo duplique.
    @Test
    fun `reintentar el guardado usa el mismo id de borrador`() = runTest(testDispatcher) {
        abrirCaptura()
        abrirBorrador()
        noteRepository.saveResult = DomainResult.Failure(DomainError.Network())
        viewModel.onSaveCapturedNote()
        testDispatcher.scheduler.advanceUntilIdle()
        val primerIntento = noteRepository.savedDraft?.id
        assertNotNull(primerIntento)

        noteRepository.saveResult = null
        viewModel.onDismissError()
        viewModel.onSaveCapturedNote()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, noteRepository.saveCallCount)
        assertEquals(primerIntento, noteRepository.savedDraft?.id)
        assertEquals("77", noteRepository.auditedNoteId)
        assertFalse(viewModel.state.value.isEditModalOpen)
    }

    @Test
    fun `si falla solo la auditoria avisa que el apunte si se guardo`() = runTest(testDispatcher) {
        abrirCaptura()
        abrirBorrador()
        noteRepository.auditResult = DomainResult.Failure(DomainError.Unknown("La IA no está disponible en este momento"))

        viewModel.onSaveCapturedNote()
        testDispatcher.scheduler.advanceUntilIdle()

        val estado = viewModel.state.value
        assertEquals(1, noteRepository.saveCallCount)
        assertFalse(estado.isEditModalOpen)
        assertFalse(estado.isSaving)
        assertFalse(estado.isAuditing)
        assertNull(estado.infoMessage)
        assertEquals(
            "Tu apunte se guardó, pero no se pudo auditar: La IA no está disponible en este momento",
            estado.errorMessage,
        )
    }

    @Test
    fun `un apunte con el texto vacio no se guarda`() = runTest(testDispatcher) {
        abrirCaptura()
        viewModel.onUpdateDraftText("   ")

        viewModel.onSaveCapturedNote()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, noteRepository.saveCallCount)
        assertEquals("El apunte está vacío: revisa el texto antes de guardarlo.", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `sin curso elegido no guarda el apunte`() = runTest(testDispatcher) {
        courseSelection.courses.value = emptyList()
        abrirCaptura()
        viewModel.onUpdateDraftText("Texto del apunte")

        viewModel.onSaveCapturedNote()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, noteRepository.saveCallCount)
        assertEquals("Elige un curso antes de guardar el apunte.", viewModel.state.value.errorMessage)
    }

    // Un doble toque en Guardar llega antes de que termine la primera corrutina: solo cuenta el primero.
    @Test
    fun `un doble toque en guardar no guarda dos veces`() = runTest(testDispatcher) {
        abrirCaptura()
        viewModel.onUpdateDraftText("Texto del apunte")

        viewModel.onSaveCapturedNote()
        viewModel.onSaveCapturedNote()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, noteRepository.saveCallCount)
    }

    @Test
    fun `sin temario el apunte se guarda sin subtema`() = runTest(testDispatcher) {
        abrirCaptura()
        assertFalse(viewModel.state.value.isSyllabusAvailable)
        viewModel.onUpdateDraftText("Texto del apunte")

        viewModel.onSaveCapturedNote()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(noteRepository.savedDraft?.subtopicId)
    }

    @Test
    fun `si la IA no encuentra subtemas lo dice en el resultado`() = runTest(testDispatcher) {
        abrirCaptura()
        noteRepository.auditResult = DomainResult.Success(emptyList())
        viewModel.onUpdateDraftText("Texto del apunte")

        viewModel.onSaveCapturedNote()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("La IA no encontró subtemas del temario en este apunte.", viewModel.state.value.infoMessage)
    }

    // Los bloques bajo 0.70 de confianza son los que el estudiante debe revisar antes de guardar.
    @Test
    fun `la transcripcion llega en bloques y solo los dudosos piden revision`() = runTest(testDispatcher) {
        abrirBorradorConBloques()

        val estado = viewModel.state.value
        assertTrue(estado.isEditModalOpen)
        assertEquals(listOf("claro", "dudoso", "ilegible"), estado.draftBlocks.map { it.text })
        assertEquals(listOf(false, true, true), estado.draftBlocks.map { it.needsReview })
        assertEquals(2, estado.draftBlocksToReview)
        assertTrue(estado.draftId.isNotBlank())
    }

    // El texto del apunte se arma con los bloques, no con el texto completo que mandó la IA.
    @Test
    fun `el texto del borrador es el de los bloques en orden`() = runTest(testDispatcher) {
        abrirBorradorConBloques()

        assertEquals("claro\ndudoso\nilegible", viewModel.state.value.draftText)
    }

    @Test
    fun `no se puede guardar mientras haya bloques dudosos sin revisar`() = runTest(testDispatcher) {
        abrirBorradorConBloques()

        viewModel.onSaveCapturedNote()
        testDispatcher.scheduler.advanceUntilIdle()

        val estado = viewModel.state.value
        assertEquals(0, noteRepository.saveCallCount)
        assertTrue(estado.isEditModalOpen)
        assertEquals("Confirma o corrige 2 bloques dudosos antes de guardar el apunte.", estado.errorMessage)
    }

    @Test
    fun `confirmar un bloque dudoso lo da por revisado sin cambiar su texto`() = runTest(testDispatcher) {
        abrirBorradorConBloques()

        viewModel.onConfirmDraftBlock(1)

        val estado = viewModel.state.value
        assertFalse(estado.draftBlocks[1].needsReview)
        assertEquals("dudoso", estado.draftBlocks[1].text)
        assertEquals(1, estado.draftBlocksToReview)
        assertEquals("claro\ndudoso\nilegible", estado.draftText)

        viewModel.onSaveCapturedNote()
        assertEquals("Confirma o corrige 1 bloque dudoso antes de guardar el apunte.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `con todos los bloques dudosos revisados se guarda y se audita`() = runTest(testDispatcher) {
        abrirBorradorConBloques()

        viewModel.onConfirmDraftBlock(1)
        viewModel.onConfirmDraftBlock(2)
        viewModel.onSaveCapturedNote()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, noteRepository.saveCallCount)
        assertEquals("claro\ndudoso\nilegible", noteRepository.savedDraft?.confirmedText)
        assertEquals("77", noteRepository.auditedNoteId)
        val estado = viewModel.state.value
        assertFalse(estado.isEditModalOpen)
        assertTrue(estado.draftBlocks.isEmpty())
    }

    @Test
    fun `corregir un bloque dudoso lo da por revisado y cambia el texto que se guarda`() = runTest(testDispatcher) {
        abrirBorradorConBloques()

        viewModel.onUpdateDraftBlock(1, "dudoso corregido")
        viewModel.onConfirmDraftBlock(2)

        val estado = viewModel.state.value
        assertFalse(estado.draftBlocks[1].needsReview)
        assertEquals("claro\ndudoso corregido\nilegible", estado.draftText)

        viewModel.onSaveCapturedNote()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("claro\ndudoso corregido\nilegible", noteRepository.savedDraft?.confirmedText)
    }

    @Test
    fun `un bloque en blanco no deja lineas vacias en el texto`() = runTest(testDispatcher) {
        abrirBorradorConBloques()

        viewModel.onUpdateDraftBlock(1, "   ")

        assertEquals("claro\nilegible", viewModel.state.value.draftText)
    }

    @Test
    fun `editar un bloque seguro no cambia cuantos faltan por revisar`() = runTest(testDispatcher) {
        abrirBorradorConBloques()

        viewModel.onUpdateDraftBlock(0, "claro y editado")

        val estado = viewModel.state.value
        assertEquals(2, estado.draftBlocksToReview)
        assertEquals("claro y editado\ndudoso\nilegible", estado.draftText)
    }

    @Test
    fun `un bloque que no existe se ignora`() = runTest(testDispatcher) {
        abrirBorradorConBloques()
        val antes = viewModel.state.value

        viewModel.onConfirmDraftBlock(9)
        viewModel.onUpdateDraftBlock(-1, "nada")

        assertEquals(antes, viewModel.state.value)
    }

    @Test
    fun `cerrar el borrador descarta los bloques`() = runTest(testDispatcher) {
        abrirBorradorConBloques()

        viewModel.onDismissEditModal()

        val estado = viewModel.state.value
        assertFalse(estado.isEditModalOpen)
        assertTrue(estado.draftBlocks.isEmpty())
    }

    // Si guardar falla, el estudiante conserva lo que ya revisó al reintentar.
    @Test
    fun `si guardar falla se conservan los bloques ya revisados`() = runTest(testDispatcher) {
        abrirBorradorConBloques()
        viewModel.onConfirmDraftBlock(1)
        viewModel.onUpdateDraftBlock(2, "corregido")
        noteRepository.saveResult = DomainResult.Failure(DomainError.Network())

        viewModel.onSaveCapturedNote()
        testDispatcher.scheduler.advanceUntilIdle()

        val estado = viewModel.state.value
        assertTrue(estado.isEditModalOpen)
        assertEquals(0, estado.draftBlocksToReview)
        assertEquals("corregido", estado.draftBlocks[2].text)
    }

    // Un borrador que no viene de la IA (sin bloques) se edita como un solo texto.
    @Test
    fun `sin bloques el borrador se edita como un solo texto`() = runTest(testDispatcher) {
        abrirCaptura()
        abrirBorrador()

        val estado = viewModel.state.value
        assertTrue(estado.draftBlocks.isEmpty())
        assertEquals("Texto OCR real", estado.draftText)
        assertEquals(0, estado.draftBlocksToReview)
    }

    @Test
    fun `si la IA falla al transcribir no se abre el borrador ni se inventa texto`() = runTest(testDispatcher) {
        abrirCaptura()
        noteRepository.extractResult = DomainResult.Failure(DomainError.Unknown("La IA no está disponible en este momento"))

        abrirBorrador()

        val estado = viewModel.state.value
        assertFalse(estado.isEditModalOpen)
        assertEquals("", estado.draftText)
        assertEquals("La IA no está disponible en este momento", estado.errorMessage)
    }

    private fun paginaTranscrita(texto: String) = Note(
        id = "",
        subtopicId = null,
        moduleId = null,
        moduleLabel = null,
        subtopicCode = null,
        subtopicTitle = null,
        confirmedText = texto,
        pageCount = 1,
        syncStatus = SyncStatus.QUEUED,
        createdAt = 0L,
    )

    private val foto = byteArrayOf(1, 2, 3)

    @Test
    fun `las paginas de la camara se transcriben juntas y abren el borrador con todo el texto`() =
        runTest(testDispatcher) {
            abrirCaptura()
            viewModel.onSelectSource("camera")
            noteRepository.extractHandler = { nombre -> DomainResult.Success(paginaTranscrita("texto de $nombre")) }

            viewModel.onPhotosCaptured(listOf(foto, foto))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("pagina_1.jpg", "pagina_2.jpg"), noteRepository.extractedFilenames)
            val estado = viewModel.state.value
            assertTrue(estado.isEditModalOpen)
            assertFalse(estado.isCameraActive)
            assertEquals("camera", estado.draftSourceType)
            assertEquals("texto de pagina_1.jpg\n\ntexto de pagina_2.jpg", estado.draftText)
        }

    @Test
    fun `cerrar la camara sin paginas no transcribe nada`() = runTest(testDispatcher) {
        abrirCaptura()
        viewModel.onSelectSource("camera")
        assertTrue(viewModel.state.value.isCameraActive)

        viewModel.onPhotosCaptured(emptyList())
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isCameraActive)
        assertTrue(noteRepository.extractedFilenames.isEmpty())
    }

    @Test
    fun `si una pagina falla no se abre el borrador y se dice cual fue`() = runTest(testDispatcher) {
        abrirCaptura()
        noteRepository.extractHandler = { nombre ->
            if (nombre == "pagina_2.jpg") {
                DomainResult.Failure(DomainError.Unknown("La IA no está disponible en este momento"))
            } else {
                DomainResult.Success(paginaTranscrita("texto"))
            }
        }

        viewModel.onPhotosCaptured(listOf(foto, foto))
        testDispatcher.scheduler.advanceUntilIdle()

        val estado = viewModel.state.value
        assertFalse(estado.isEditModalOpen)
        assertFalse(estado.isTranscribing)
        assertEquals("", estado.draftText)
        assertEquals("Página 2 de 2: La IA no está disponible en este momento", estado.errorMessage)
    }

    @Test
    fun `sin curso elegido las paginas no se mandan a la IA`() = runTest(testDispatcher) {
        courseSelection.courses.value = emptyList()
        abrirCaptura()

        viewModel.onPhotosCaptured(listOf(foto))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(noteRepository.extractedFilenames.isEmpty())
        assertEquals("Elige un curso antes de agregar un apunte.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `sin permiso de camara avisa y cierra la camara`() = runTest(testDispatcher) {
        abrirCaptura()
        viewModel.onSelectSource("camera")

        viewModel.onCameraPermissionDenied()

        val estado = viewModel.state.value
        assertFalse(estado.isCameraActive)
        assertTrue(estado.errorMessage!!.startsWith("Sin permiso de cámara"))
    }

    // La IA tarda unos segundos: mientras tanto la pantalla muestra que está transcribiendo.
    @Test
    fun `mientras la IA transcribe se marca el estado y al terminar se apaga`() = runTest(testDispatcher) {
        abrirCaptura()

        viewModel.onFileBytesSelected(foto, "content://galeria/apunte.jpg", "file")
        assertTrue(viewModel.state.value.isTranscribing)

        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isTranscribing)
        assertTrue(viewModel.state.value.isEditModalOpen)
    }

    @Test
    fun `un segundo envio mientras transcribe no vuelve a llamar a la IA`() = runTest(testDispatcher) {
        abrirCaptura()

        viewModel.onPhotosCaptured(listOf(foto))
        viewModel.onPhotosCaptured(listOf(foto))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, noteRepository.extractedFilenames.size)
    }
}
