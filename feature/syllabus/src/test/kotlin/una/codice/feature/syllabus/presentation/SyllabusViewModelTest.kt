package una.codice.feature.syllabus.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.CourseModule
import una.codice.core.domain.model.MoveDirection
import una.codice.core.domain.model.StudentGroup
import una.codice.core.domain.model.Subtopic
import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.enrollment.ObserveTeacherGroupsUseCase
import una.codice.core.domain.usecase.file.MAX_UPLOAD_BYTES
import una.codice.core.domain.usecase.syllabus.ExtractSyllabusFromPdfUseCase
import una.codice.core.domain.usecase.syllabus.LoadSyllabusUseCase
import una.codice.core.domain.usecase.syllabus.ObserveSyllabusUseCase
import una.codice.core.domain.usecase.syllabus.PublishSyllabusUseCase
import una.codice.core.domain.usecase.syllabus.SaveSyllabusDraftUseCase
import una.codice.core.domain.usecase.syllabus.SyllabusIssue
import una.codice.core.domain.usecase.syllabus.ValidateSyllabusUseCase

/**
 * Tests de [SyllabusViewModel] (issue #32, flujo manual).
 *
 * Ver el KDoc de `AuthViewModelTest` para el porqué de `setMain`/`resetMain`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyllabusViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    /** Lo mínimo que reconoce el validador como un PDF: la firma `%PDF-` al inicio. */
    private val pdfValido = "%PDF-1.7\n1 0 obj".toByteArray()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun temarioCompleto() = Syllabus(
        courseId = "EIF411",
        modules = listOf(
            CourseModule(
                id = "mod-1",
                number = 1,
                title = "Fundamentos",
                subtopics = listOf(
                    Subtopic("sub-1", "1.1", "Ciclo de vida", 0, listOf("Explicar los estados")),
                ),
            ),
        ),
    )

    private fun crearViewModel(
        inicial: Syllabus = Syllabus(courseId = "EIF411"),
        saveFailure: DomainError? = null,
        servidor: DomainResult<Syllabus> = DomainResult.Success(Syllabus(courseId = "EIF411")),
    ): Pair<SyllabusViewModel, FakeCourseRepository> {
        val repositorio = FakeCourseRepository(initialSyllabus = inicial, saveFailure = saveFailure)
        repositorio.serverSyllabus = servidor
        val viewModel = SyllabusViewModel(
            observeSyllabus = ObserveSyllabusUseCase(repositorio),
            saveDraft = SaveSyllabusDraftUseCase(repositorio),
            publishSyllabus = PublishSyllabusUseCase(repositorio, ValidateSyllabusUseCase()),
            extractSyllabusFromPdf = ExtractSyllabusFromPdfUseCase(repositorio),
            loadSyllabus = LoadSyllabusUseCase(repositorio),
        )
        viewModel.onCourseSelected("EIF411")
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel to repositorio
    }

    // ---------- Carga ----------

    @Test
    fun `al seleccionar el curso se carga su temario y termina la carga`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel(inicial = temarioCompleto())

        assertTrue(!viewModel.state.value.isLoading)
        assertEquals(1, viewModel.state.value.syllabus.modules.size)
    }

    // ---------- Edición ----------

    /** El primer módulo es el 1; el siguiente toma el número libre que sigue. */
    @Test
    fun `agregar modulos les asigna numeros correlativos`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel()

        viewModel.onAddModule()
        viewModel.onAddModule()

        assertEquals(listOf(1, 2), viewModel.state.value.syllabus.modules.map { it.number })
    }

    /**
     * El código propuesto sigue el formato del programa (`4.4`), tomando el
     * número del módulo. Es una propuesta: el profesor puede cambiarlo.
     */
    @Test
    fun `agregar un subtema propone el codigo con el numero del modulo`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel()
        viewModel.onAddModule()
        val moduleId = viewModel.state.value.syllabus.modules.first().id

        viewModel.onAddSubtopic(moduleId)
        viewModel.onAddSubtopic(moduleId)

        val codigos = viewModel.state.value.syllabus.modules.first().subtopics.map { it.code }
        assertEquals(listOf("1.1", "1.2"), codigos)
    }

    /**
     * Los objetivos se escriben en un solo campo, uno por línea. Las líneas
     * en blanco son el resultado normal de teclear Enter de más, no
     * objetivos vacíos que haya que guardar.
     */
    @Test
    fun `los objetivos se parten por linea y se descartan las vacias`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel()
        viewModel.onAddModule()
        val moduleId = viewModel.state.value.syllabus.modules.first().id
        viewModel.onAddSubtopic(moduleId)
        val subtopicId = viewModel.state.value.syllabus.modules.first().subtopics.first().id

        viewModel.onSubtopicObjectivesChanged(moduleId, subtopicId, "Primero\n\n  \nSegundo\n")

        val objetivos = viewModel.state.value.syllabus.modules.first().subtopics.first().objectives
        assertEquals(listOf("Primero", "Segundo"), objetivos)
    }

    /**
     * Al borrar un subtema del medio, los que quedan se renumeran para que
     * `position` no deje huecos — el orden de la lista es la fuente de
     * verdad.
     */
    @Test
    fun `borrar un subtema renumera las posiciones de los que quedan`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel()
        viewModel.onAddModule()
        val moduleId = viewModel.state.value.syllabus.modules.first().id
        repeat(3) { viewModel.onAddSubtopic(moduleId) }
        val segundo = viewModel.state.value.syllabus.modules.first().subtopics[1].id

        viewModel.onRemoveSubtopic(moduleId, segundo)

        val posiciones = viewModel.state.value.syllabus.modules.first().subtopics.map { it.position }
        assertEquals(listOf(0, 1), posiciones)
    }

    /**
     * Editar limpia los problemas del intento anterior: dejarlos en pantalla
     * mientras el profesor corrige es justo el ruido que marca el hallazgo
     * H10.2 de la evaluación heurística.
     */
    @Test
    fun `editar el temario limpia los problemas del intento anterior`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel()
        viewModel.onPublish() // temario vacío -> rechazado
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.issues.isNotEmpty())

        viewModel.onAddModule()

        assertTrue(viewModel.state.value.issues.isEmpty())
    }

    // ---------- Guardar ----------

    @Test
    fun `guardar el borrador lo manda al repositorio y apaga la bandera`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearViewModel(inicial = temarioCompleto())

        viewModel.onSaveDraft()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repositorio.savedDrafts.size)
        assertTrue(!viewModel.state.value.isSaving)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `si guardar falla se muestra el mensaje del error`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel(inicial = temarioCompleto(), saveFailure = DomainError.Network())

        viewModel.onSaveDraft()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(DomainError.Network().message, viewModel.state.value.errorMessage)
        assertTrue(!viewModel.state.value.isSaving)
    }

    // ---------- Publicar ----------

    @Test
    fun `publicar un temario valido lo publica y avisa`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearViewModel(inicial = temarioCompleto())

        viewModel.onPublish()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("EIF411"), repositorio.publishedCourses)
        assertTrue(viewModel.state.value.justPublished)
        assertTrue(!viewModel.state.value.isPublishing)
    }

    /**
     * Un temario vacío no llega al repositorio: los problemas se muestran y
     * la publicación se detiene antes. Es el criterio de aceptación del
     * issue #32 ("un fallo deja el curso sin temario, no a medias") visto
     * desde la pantalla.
     */
    @Test
    fun `publicar un temario vacio muestra los problemas y no publica`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearViewModel()

        viewModel.onPublish()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(repositorio.publishedCourses.isEmpty())
        assertTrue(viewModel.state.value.blockingIssues.contains(SyllabusIssue.EmptySyllabus))
        assertTrue(!viewModel.state.value.justPublished)
    }

    /** Las advertencias no impiden publicar, pero quedan a la vista. */
    @Test
    fun `un subtema sin objetivos se publica igual, con la advertencia visible`() = runTest(testDispatcher) {
        val sinObjetivos = Syllabus(
            courseId = "EIF411",
            modules = listOf(
                CourseModule(
                    id = "mod-1",
                    number = 1,
                    title = "Fundamentos",
                    subtopics = listOf(Subtopic("sub-1", "1.1", "Intro", 0, emptyList())),
                ),
            ),
        )
        val (viewModel, repositorio) = crearViewModel(inicial = sinObjetivos)

        viewModel.onPublish()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("EIF411"), repositorio.publishedCourses)
        assertTrue(viewModel.state.value.warnings.contains(SyllabusIssue.SubtopicWithoutObjectives("1.1")))
        assertTrue(viewModel.state.value.blockingIssues.isEmpty())
    }

    @Test
    fun `no se puede publicar mientras no haya modulos`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel()

        assertTrue(!viewModel.state.value.canPublish)

        viewModel.onAddModule()

        assertTrue(viewModel.state.value.canPublish)
    }

    // ---------- #37: revisar la propuesta de la IA y publicarla ----------

    /** Una propuesta con de todo: un subtema dudoso, uno sin objetivos y un módulo dudoso y vacío. */
    private fun propuestaDeLaIa() = Syllabus(
        courseId = "EIF411",
        modules = listOf(
            CourseModule(
                id = "mod-1",
                number = 1,
                title = "Fundamentos",
                confidence = 0.95f,
                subtopics = listOf(
                    Subtopic("sub-1", "1.1", "Seguro", 0, listOf("Objetivo"), confidence = 0.95f),
                    Subtopic("sub-2", "1.2", "Dudoso", 1, listOf("Objetivo"), confidence = 0.40f),
                    Subtopic("sub-3", "1.3", "Sin objetivos", 2, emptyList(), confidence = 0.90f),
                ),
            ),
            CourseModule(id = "mod-2", number = 2, title = "Vacío y dudoso", confidence = 0.50f),
        ),
    )

    @Test
    fun `la propuesta marca lo dudoso y lo incompleto y cuenta cuantos elementos hay por revisar`() =
        runTest(testDispatcher) {
            val (viewModel, _) = crearViewModel(inicial = propuestaDeLaIa())
            val estado = viewModel.state.value
            val modulo1 = estado.syllabus.modules[0]
            val modulo2 = estado.syllabus.modules[1]

            assertFalse(estado.needsAttention(modulo1))
            assertFalse(estado.needsAttention(modulo1.subtopics[0]))
            assertTrue(estado.needsAttention(modulo1.subtopics[1]))
            assertTrue(estado.needsAttention(modulo1.subtopics[2]))
            assertTrue(estado.needsAttention(modulo2))
            assertEquals(3, estado.itemsToReview)
        }

    @Test
    fun `corregir un subtema dudoso lo da por revisado`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel(inicial = propuestaDeLaIa())

        viewModel.onSubtopicTitleChanged("mod-1", "sub-2", "Dudoso corregido")

        val estado = viewModel.state.value
        assertFalse(estado.needsAttention(estado.syllabus.modules[0].subtopics[1]))
        assertEquals(2, estado.itemsToReview)
    }

    @Test
    fun `confirmar un elemento dudoso lo da por revisado sin cambiar el temario`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel(inicial = propuestaDeLaIa())
        val antes = viewModel.state.value.syllabus

        viewModel.onConfirmItem("sub-2")
        viewModel.onConfirmItem("mod-2")

        val estado = viewModel.state.value
        assertFalse(estado.needsAttention(estado.syllabus.modules[0].subtopics[1]))
        assertEquals(antes, estado.syllabus)
        // El módulo 2 sigue marcado porque está vacío: confirmarlo no le agrega subtemas.
        assertTrue(estado.needsAttention(estado.syllabus.modules[1]))
    }

    @Test
    fun `un subtema sin objetivos sigue marcado aunque se confirme`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel(inicial = propuestaDeLaIa())

        viewModel.onConfirmItem("sub-3")

        assertTrue(viewModel.state.value.needsAttention(viewModel.state.value.syllabus.modules[0].subtopics[2]))
    }

    @Test
    fun `agregar los objetivos que faltaban quita la marca`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel(inicial = propuestaDeLaIa())

        viewModel.onSubtopicObjectivesChanged("mod-1", "sub-3", "Un objetivo")

        assertFalse(viewModel.state.value.needsAttention(viewModel.state.value.syllabus.modules[0].subtopics[2]))
    }

    @Test
    fun `un temario escrito a mano y completo no tiene nada marcado`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel(inicial = temarioCompleto())

        assertEquals(0, viewModel.state.value.itemsToReview)
    }

    @Test
    fun `bajar un modulo lo reordena y renumera sus subtemas`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel(inicial = propuestaDeLaIa())

        viewModel.onMoveModule("mod-1", MoveDirection.DOWN)

        val modulos = viewModel.state.value.syllabus.modules
        assertEquals(listOf("mod-2", "mod-1"), modulos.map { it.id })
        assertEquals(listOf(1, 2), modulos.map { it.number })
        assertEquals(listOf("2.1", "2.2", "2.3"), modulos[1].subtopics.map { it.code })
    }

    @Test
    fun `subir un subtema lo cambia de lugar dentro de su modulo`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel(inicial = propuestaDeLaIa())

        viewModel.onMoveSubtopic("mod-1", "sub-3", MoveDirection.UP)

        val subtemas = viewModel.state.value.syllabus.modules[0].subtopics.sortedBy { it.position }
        assertEquals(listOf("sub-1", "sub-3", "sub-2"), subtemas.map { it.id })
    }

    @Test
    fun `mover limpia los problemas del intento anterior`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel()
        viewModel.onPublish()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.issues.isNotEmpty())

        viewModel.onMoveModule("no-existe", MoveDirection.UP)

        assertTrue(viewModel.state.value.issues.isEmpty())
    }

    // --- publicar sobre un temario existente ---

    @Test
    fun `al abrir un curso se lee lo que ya tiene el servidor`() = runTest(testDispatcher) {
        val (_, repositorio) = crearViewModel()

        assertEquals(listOf("EIF411"), repositorio.loadedCourses)
    }

    @Test
    fun `si el curso ya tiene temario publicar pide confirmar y no publica todavia`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearViewModel(
            inicial = temarioCompleto(),
            servidor = DomainResult.Success(temarioCompleto().copy(isPublished = true)),
        )
        assertTrue(viewModel.state.value.serverHasSyllabus)

        viewModel.onPublish()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isConfirmingOverwrite)
        assertTrue(repositorio.publishedCourses.isEmpty())
    }

    @Test
    fun `confirmar el aviso publica el temario`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearViewModel(
            inicial = temarioCompleto(),
            servidor = DomainResult.Success(temarioCompleto()),
        )
        viewModel.onPublish()

        viewModel.onConfirmOverwrite()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("EIF411"), repositorio.publishedCourses)
        assertFalse(viewModel.state.value.isConfirmingOverwrite)
        assertTrue(viewModel.state.value.justPublished)
    }

    @Test
    fun `cancelar el aviso no publica nada`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearViewModel(
            inicial = temarioCompleto(),
            servidor = DomainResult.Success(temarioCompleto()),
        )
        viewModel.onPublish()

        viewModel.onDismissOverwrite()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isConfirmingOverwrite)
        assertTrue(repositorio.publishedCourses.isEmpty())
    }

    @Test
    fun `confirmar sin un aviso pendiente no publica`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearViewModel(inicial = temarioCompleto())

        viewModel.onConfirmOverwrite()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(repositorio.publishedCourses.isEmpty())
    }

    @Test
    fun `un curso sin temario en el servidor se publica directo`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearViewModel(inicial = temarioCompleto())

        viewModel.onPublish()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isConfirmingOverwrite)
        assertEquals(listOf("EIF411"), repositorio.publishedCourses)
    }

    @Test
    fun `despues de publicar el servidor ya tiene temario y publicar de nuevo pide confirmar`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearViewModel(inicial = temarioCompleto())
        viewModel.onPublish()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPublish()

        assertTrue(viewModel.state.value.isConfirmingOverwrite)
        assertEquals(1, repositorio.publishedCourses.size)
    }

    @Test
    fun `si no se pudo leer el servidor se muestra el motivo`() = runTest(testDispatcher) {
        val (viewModel, _) = crearViewModel(servidor = DomainResult.Failure(DomainError.Network()))

        assertEquals(DomainError.Network().message, viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isLoading)
    }

    // --- cursos del profesor ---

    private fun grupo(courseId: String, code: String, name: String = "Curso $code") = StudentGroup(
        id = "g-$courseId",
        courseId = courseId,
        courseName = name,
        groupNumber = "01",
        period = "2026-II",
        professorName = "Profe",
        courseCode = code,
    )

    private fun crearConGrupos(grupos: FakeTeacherGroups): Pair<SyllabusViewModel, FakeCourseRepository> {
        val repositorio = FakeCourseRepository(initialSyllabus = Syllabus(courseId = "25"))
        val viewModel = SyllabusViewModel(
            observeSyllabus = ObserveSyllabusUseCase(repositorio),
            saveDraft = SaveSyllabusDraftUseCase(repositorio),
            publishSyllabus = PublishSyllabusUseCase(repositorio, ValidateSyllabusUseCase()),
            extractSyllabusFromPdf = ExtractSyllabusFromPdfUseCase(repositorio),
            loadSyllabus = LoadSyllabusUseCase(repositorio),
            observeTeacherGroups = ObserveTeacherGroupsUseCase(grupos.auth, grupos.enrollment),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel to repositorio
    }

    // El id del curso es el del servidor (numérico) y el código solo es la etiqueta: con el código el servidor no lo encuentra.
    @Test
    fun `el selector usa el id numerico del curso y su codigo como etiqueta`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearConGrupos(FakeTeacherGroups(listOf(grupo("25", "EIF411"), grupo("30", "EIF303"))))

        val estado = viewModel.state.value
        assertEquals(listOf("25", "30"), estado.availableCourses.map { it.id })
        assertEquals(listOf("EIF411", "EIF303"), estado.availableCourses.map { it.label })
        assertEquals("25", estado.selectedCourseId)
        assertEquals(listOf("25"), repositorio.loadedCourses)
    }

    @Test
    fun `dos grupos del mismo curso muestran una sola opcion`() = runTest(testDispatcher) {
        val (viewModel, _) = crearConGrupos(FakeTeacherGroups(listOf(grupo("25", "EIF411"), grupo("25", "EIF411"))))

        assertEquals(listOf("25"), viewModel.state.value.availableCourses.map { it.id })
    }

    @Test
    fun `sin cursos no se inventa uno ni se pide ningun temario`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearConGrupos(FakeTeacherGroups())

        val estado = viewModel.state.value
        assertTrue(estado.availableCourses.isEmpty())
        assertEquals("", estado.selectedCourseId)
        assertFalse(estado.isLoading)
        assertTrue(repositorio.loadedCourses.isEmpty())
    }

    @Test
    fun `sin un curso elegido no se extrae el temario del PDF`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearConGrupos(FakeTeacherGroups())

        viewModel.onExtractFromPdf(pdfValido, "programa.pdf")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(repositorio.extractedCourses.isEmpty())
        assertEquals("Elige un curso antes de cargar su temario.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `la extraccion usa el id numerico del curso elegido y reinicia lo revisado`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearConGrupos(FakeTeacherGroups(listOf(grupo("25", "EIF411"))))
        viewModel.onConfirmItem("sub-2")

        viewModel.onExtractFromPdf(pdfValido, "programa.pdf")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("25"), repositorio.extractedCourses)
        assertTrue(viewModel.state.value.reviewedIds.isEmpty())
    }

    // Issue #32: un archivo que no es PDF se rechaza antes de subirlo.
    @Test
    fun `un archivo que no es PDF se rechaza sin subirlo`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearConGrupos(FakeTeacherGroups(listOf(grupo("25", "EIF411"))))

        viewModel.onExtractFromPdf(byteArrayOf(1, 2, 3), "foto.png")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(repositorio.extractedCourses.isEmpty())
        assertTrue(viewModel.state.value.errorMessage!!.contains("no es un PDF"))
        assertFalse(viewModel.state.value.canRetryExtraction)
        assertFalse(viewModel.state.value.isExtractingAi)
    }

    @Test
    fun `un PDF de mas de 10 MB se rechaza sin subirlo`() = runTest(testDispatcher) {
        val (viewModel, repositorio) = crearConGrupos(FakeTeacherGroups(listOf(grupo("25", "EIF411"))))

        viewModel.onExtractFromPdf(pdfValido + ByteArray(MAX_UPLOAD_BYTES), "enorme.pdf")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(repositorio.extractedCourses.isEmpty())
        assertTrue(viewModel.state.value.errorMessage!!.contains("10 MB"))
    }

    @Test
    fun `un archivo que el selector descarto se explica sin ofrecer reintento`() = runTest(testDispatcher) {
        val (viewModel, _) = crearConGrupos(FakeTeacherGroups(listOf(grupo("25", "EIF411"))))

        viewModel.onPdfRejected("pesa más de 10 MB")

        assertEquals("pesa más de 10 MB", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.canRetryExtraction)
    }

    // Cambiar de curso deja de seguir el borrador del anterior: si no, su temario pisaría la pantalla.
    @Test
    fun `al cambiar de curso se deja de seguir el temario del anterior`() = runTest(testDispatcher) {
        val grupos = FakeTeacherGroups(listOf(grupo("25", "EIF411"), grupo("30", "EIF303")))
        val (viewModel, repositorio) = crearConGrupos(grupos)

        viewModel.onCourseSelected("30")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("25", "30"), repositorio.loadedCourses)
        assertEquals("30", viewModel.state.value.selectedCourseId)
        assertFalse(viewModel.state.value.serverHasSyllabus)
    }
}
