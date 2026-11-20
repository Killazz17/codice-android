package una.codice.feature.syllabus.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import una.codice.core.designsystem.component.CourseOption
import una.codice.core.domain.model.CourseModule
import una.codice.core.domain.model.MoveDirection
import una.codice.core.domain.model.Subtopic
import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.model.moveModule
import una.codice.core.domain.model.moveSubtopic
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.enrollment.ObserveTeacherGroupsUseCase
import una.codice.core.domain.usecase.file.validateSyllabusPdf
import una.codice.core.domain.usecase.syllabus.ExtractSyllabusFromPdfUseCase
import una.codice.core.domain.usecase.syllabus.LoadSyllabusUseCase
import una.codice.core.domain.usecase.syllabus.ObserveSyllabusUseCase
import una.codice.core.domain.usecase.syllabus.PublishResult
import una.codice.core.domain.usecase.syllabus.PublishSyllabusUseCase
import una.codice.core.domain.usecase.syllabus.SaveSyllabusDraftUseCase

/**
 * Orquesta la pantalla de temario del profesor (issue #32, flujo manual e IA-02).
 */
@HiltViewModel
class SyllabusViewModel @Inject constructor(
    private val observeSyllabus: ObserveSyllabusUseCase,
    private val saveDraft: SaveSyllabusDraftUseCase,
    private val publishSyllabus: PublishSyllabusUseCase,
    private val extractSyllabusFromPdf: ExtractSyllabusFromPdfUseCase,
    private val loadSyllabus: LoadSyllabusUseCase,
    private val observeTeacherGroups: ObserveTeacherGroupsUseCase? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(SyllabusUiState())
    val state: StateFlow<SyllabusUiState> = _state.asStateFlow()

    private var courseId: String = ""

    // Solo se sigue el temario del curso elegido: al cambiar de curso se cancela el del anterior, que si no seguiría
    // pisando la pantalla con el borrador del curso viejo.
    private var syllabusJob: Job? = null

    init {
        if (observeTeacherGroups != null) {
            viewModelScope.launch {
                observeTeacherGroups().collect { groups ->
                    // El id es el del curso en el servidor (numérico) y el código solo es la etiqueta: con el código
                    // la extracción y la publicación no encontrarían el curso.
                    val courseOptions = groups.map { summary ->
                        val label = summary.group.courseCode.ifBlank { summary.group.courseName.ifBlank { summary.group.courseId } }
                        CourseOption(id = summary.group.courseId, label = label)
                    }.distinctBy { it.id }

                    // Sin cursos no hay temario que armar: no se inventa uno.
                    if (courseOptions.isEmpty()) {
                        _state.update { it.copy(availableCourses = emptyList(), selectedCourseId = "", isLoading = false) }
                        return@collect
                    }

                    val currentSelected = _state.value.selectedCourseId
                    val newSelected = currentSelected.takeIf { id -> courseOptions.any { it.id == id } }
                        ?: courseOptions.first().id

                    _state.update { it.copy(availableCourses = courseOptions, selectedCourseId = newSelected) }

                    if (courseId != newSelected) {
                        onCourseSelected(newSelected)
                    }
                }
            }
        } else {
            _state.value = _state.value.copy(
                availableCourses = listOf(CourseOption("EIF411", "EIF411")),
                selectedCourseId = "EIF411",
            )
        }
    }

    /**
     * Carga el temario del curso. La pantalla la llama al seleccionar un curso o al entrar.
     */
    fun onCourseSelected(courseId: String) {
        if (this.courseId == courseId && _state.value.syllabus.courseId == courseId) return
        this.courseId = courseId
        _state.update {
            it.copy(
                selectedCourseId = courseId,
                isLoading = true,
                errorMessage = null,
                aiConfidenceMessage = null,
                uploadedFileName = null,
                uploadedFileSize = null,
                reviewedIds = emptySet(),
                serverHasSyllabus = false,
                isConfirmingOverwrite = false,
            )
        }

        syllabusJob?.cancel()
        syllabusJob = viewModelScope.launch {
            // Primero lo que el curso ya tiene en el servidor: abre como borrador y sirve para avisar, antes de
            // publicar, que se va a escribir encima de un temario existente.
            when (val result = loadSyllabus(courseId)) {
                is DomainResult.Success -> _state.update { it.copy(serverHasSyllabus = result.data.modules.isNotEmpty()) }
                is DomainResult.Failure -> _state.update { it.copy(errorMessage = result.error.message) }
            }
            observeSyllabus(courseId).collect { syllabus ->
                _state.update { it.copy(syllabus = syllabus, isLoading = false, selectedCourseId = courseId) }
            }
        }
    }

    // ---------- [IA-02] Extracción de PDF con IA ----------

    // El último PDF que se intentó subir, para "Reintentar" sin volver a elegirlo. No va en el estado: son bytes.
    private var lastPdf: Pair<ByteArray, String>? = null

    /** El selector de archivos descartó el archivo (por ejemplo, más de 10 MB) antes de leerlo completo. */
    fun onPdfRejected(message: String) {
        _state.update { it.copy(errorMessage = message, canRetryExtraction = false) }
    }

    fun onRetryExtraction() {
        val (bytes, name) = lastPdf ?: return
        onExtractFromPdf(bytes, name)
    }

    fun onExtractFromPdf(pdfBytes: ByteArray, filename: String = "programa.pdf") {
        if (_state.value.isExtractingAi) return

        // Un archivo que no es un PDF, o que pasa del tope, se rechaza aquí y no se sube: el motivo sale al instante.
        validateSyllabusPdf(pdfBytes)?.let { rejected ->
            lastPdf = null
            _state.update { it.copy(errorMessage = rejected.message, canRetryExtraction = false) }
            return
        }
        lastPdf = pdfBytes to filename

        // Sin un curso elegido no hay a quién pedirle el temario: no se usa uno de relleno.
        val targetCourseId = courseId.ifBlank { _state.value.selectedCourseId }
        if (targetCourseId.isBlank()) {
            _state.update { it.copy(errorMessage = "Elige un curso antes de cargar su temario.") }
            return
        }

        val sizeKb = pdfBytes.size / 1024
        val formattedSize = if (sizeKb >= 1024) {
            "${String.format(java.util.Locale.US, "%.1f", sizeKb / 1024.0)} MB"
        } else {
            "$sizeKb KB"
        }

        _state.value = _state.value.copy(
            isExtractingAi = true,
            canRetryExtraction = false,
            errorMessage = null,
            aiConfidenceMessage = null,
            uploadedFileName = filename,
            uploadedFileSize = formattedSize,
        )

        viewModelScope.launch {
            when (val result = extractSyllabusFromPdf(targetCourseId, pdfBytes, filename)) {
                is DomainResult.Success -> {
                    _state.value = _state.value.copy(
                        isExtractingAi = false,
                        syllabus = result.data,
                        aiConfidenceMessage = "✨ Temario extraído desde '$filename' con IA",
                        // Una propuesta nueva se revisa de cero.
                        reviewedIds = emptySet(),
                        issues = emptyList(),
                    )
                }
                is DomainResult.Failure -> {
                    _state.value = _state.value.copy(
                        isExtractingAi = false,
                        // Un fallo de la IA o de la red se puede reintentar con el mismo PDF; el otro camino es cargar a mano.
                        canRetryExtraction = true,
                        // El motivo real (IA caída, archivo inválido, sin permiso) y la salida: cargar el temario a mano.
                        errorMessage = "${result.error.message}. Podés reintentar o ingresar los módulos manualmente.",
                    )
                }
            }
        }
    }

    // ---------- Edición del árbol ----------

    /** Agrega un módulo al final, con el siguiente número libre. */
    fun onAddModule() {
        val actual = _state.value.syllabus
        val siguienteNumero = (actual.modules.maxOfOrNull { it.number } ?: 0) + 1

        actualizarTemario(
            actual.copy(
                modules = actual.modules + CourseModule(
                    id = nuevoId("mod"),
                    number = siguienteNumero,
                    title = "",
                ),
            ),
        )
    }

    // Corregir un módulo o un subtema es revisarlo: deja de marcarse como dudoso.
    fun onModuleTitleChanged(moduleId: String, title: String) {
        actualizarModulo(moduleId) { it.copy(title = title) }
        marcarRevisado(moduleId)
    }

    /** "Está bien": el profesor vio el módulo o subtema marcado y lo da por bueno sin cambiarlo. */
    fun onConfirmItem(id: String) {
        marcarRevisado(id)
    }

    /** Sube o baja un módulo; los códigos de sus subtemas siguen a su número nuevo. */
    fun onMoveModule(moduleId: String, direction: MoveDirection) {
        actualizarTemario(_state.value.syllabus.moveModule(moduleId, direction))
    }

    /** Sube o baja un subtema dentro de su módulo. */
    fun onMoveSubtopic(moduleId: String, subtopicId: String, direction: MoveDirection) {
        actualizarModulo(moduleId) { it.moveSubtopic(subtopicId, direction) }
    }

    fun onRemoveModule(moduleId: String) {
        val actual = _state.value.syllabus
        actualizarTemario(actual.copy(modules = actual.modules.filterNot { it.id == moduleId }))
    }

    /**
     * Agrega un subtema al módulo, proponiendo el código `<módulo>.<n>` — el
     * formato del programa del curso (`4.4`). El profesor puede cambiarlo:
     * es una propuesta, no una imposición.
     */
    fun onAddSubtopic(moduleId: String) {
        actualizarModulo(moduleId) { modulo ->
            val siguientePosicion = modulo.subtopics.size
            modulo.copy(
                subtopics = modulo.subtopics + Subtopic(
                    id = nuevoId("sub"),
                    code = "${modulo.number}.${siguientePosicion + 1}",
                    title = "",
                    position = siguientePosicion,
                ),
            )
        }
    }

    fun onSubtopicCodeChanged(moduleId: String, subtopicId: String, code: String) {
        actualizarSubtema(moduleId, subtopicId) { it.copy(code = code) }
        marcarRevisado(subtopicId)
    }

    fun onSubtopicTitleChanged(moduleId: String, subtopicId: String, title: String) {
        actualizarSubtema(moduleId, subtopicId) { it.copy(title = title) }
        marcarRevisado(subtopicId)
    }

    /**
     * Reemplaza los objetivos de un subtema a partir del texto del campo,
     * una línea por objetivo. Las líneas en blanco se descartan: son el
     * resultado normal de teclear Enter dos veces, no un objetivo vacío que
     * el profesor quiera guardar.
     */
    fun onSubtopicObjectivesChanged(moduleId: String, subtopicId: String, texto: String) {
        val objetivos = texto.lines().map { it.trim() }.filter { it.isNotEmpty() }
        actualizarSubtema(moduleId, subtopicId) { it.copy(objectives = objetivos) }
        marcarRevisado(subtopicId)
    }

    fun onRemoveSubtopic(moduleId: String, subtopicId: String) {
        actualizarModulo(moduleId) { modulo ->
            modulo.copy(
                subtopics = modulo.subtopics
                    .filterNot { it.id == subtopicId }
                    // Se recalcula `position` para que no queden huecos: el
                    // orden de la lista es la fuente de verdad, no el índice
                    // que tenía cada subtema antes de borrar.
                    .mapIndexed { indice, subtema -> subtema.copy(position = indice) },
            )
        }
    }

    // ---------- Guardar y publicar ----------

    fun onSaveDraft() {
        if (_state.value.isSaving) return
        _state.value = _state.value.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            when (val resultado = saveDraft(_state.value.syllabus)) {
                is DomainResult.Success -> _state.value = _state.value.copy(isSaving = false)
                is DomainResult.Failure -> _state.value = _state.value.copy(
                    isSaving = false,
                    errorMessage = resultado.error.message,
                )
            }
        }
    }

    /**
     * "Publicar al curso". Si el curso ya tiene un temario en el servidor primero se pide confirmar: publicar lo
     * actualiza (agrega, cambia y quita subtemas) y los estudiantes verán el resultado.
     */
    fun onPublish() {
        val state = _state.value
        if (state.isPublishing || state.isConfirmingOverwrite) return

        if (state.serverHasSyllabus) {
            _state.update { it.copy(isConfirmingOverwrite = true) }
            return
        }
        publicarAhora()
    }

    /** El profesor confirmó que quiere publicar encima del temario que ya tiene el curso. */
    fun onConfirmOverwrite() {
        if (!_state.value.isConfirmingOverwrite) return
        _state.update { it.copy(isConfirmingOverwrite = false) }
        publicarAhora()
    }

    fun onDismissOverwrite() {
        _state.update { it.copy(isConfirmingOverwrite = false) }
    }

    private fun publicarAhora() {
        if (_state.value.isPublishing) return
        _state.value = _state.value.copy(isPublishing = true, errorMessage = null, justPublished = false)

        viewModelScope.launch {
            when (val resultado = publishSyllabus(_state.value.syllabus)) {
                is PublishResult.Published -> _state.value = _state.value.copy(
                    isPublishing = false,
                    issues = resultado.warnings,
                    justPublished = true,
                    serverHasSyllabus = true,
                )
                is PublishResult.Rejected -> _state.value = _state.value.copy(
                    isPublishing = false,
                    issues = resultado.issues,
                )
                is PublishResult.Failed -> _state.value = _state.value.copy(
                    isPublishing = false,
                    errorMessage = resultado.message,
                )
            }
        }
    }

    /** La pantalla lo llama tras mostrar la confirmación, para no repetirla. */
    fun onPublishedMessageShown() {
        _state.value = _state.value.copy(justPublished = false)
    }

    // ---------- Utilidades privadas ----------

    private fun marcarRevisado(id: String) {
        _state.update { it.copy(reviewedIds = it.reviewedIds + id) }
    }

    /**
     * Cualquier edición limpia los problemas del intento anterior: dejarlos
     * en pantalla mientras el profesor corrige es ruido, y el hallazgo H10.2
     * de la evaluación heurística del equipo apunta justo a eso.
     */
    private fun actualizarTemario(nuevo: Syllabus) {
        _state.value = _state.value.copy(syllabus = nuevo, issues = emptyList(), justPublished = false)
    }

    private fun actualizarModulo(moduleId: String, cambio: (CourseModule) -> CourseModule) {
        val actual = _state.value.syllabus
        actualizarTemario(
            actual.copy(
                modules = actual.modules.map { if (it.id == moduleId) cambio(it) else it },
            ),
        )
    }

    private fun actualizarSubtema(
        moduleId: String,
        subtopicId: String,
        cambio: (Subtopic) -> Subtopic,
    ) {
        actualizarModulo(moduleId) { modulo ->
            modulo.copy(
                subtopics = modulo.subtopics.map { if (it.id == subtopicId) cambio(it) else it },
            )
        }
    }

    /**
     * Id local para una fila recién creada.
     *
     * Lleva prefijo `local-` a propósito: deja evidente que **no** es el
     * `uuid` que emite el servidor (ver el KDoc de
     * [una.codice.core.domain.model.User]). Cuando exista el backend, el id
     * definitivo llega en la respuesta al guardar y reemplaza a este.
     */
    private fun nuevoId(prefijo: String): String = "local-$prefijo-${UUID.randomUUID()}"
}
