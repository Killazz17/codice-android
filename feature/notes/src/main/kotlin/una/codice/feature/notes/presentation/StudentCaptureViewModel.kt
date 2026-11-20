package una.codice.feature.notes.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import una.codice.core.designsystem.component.CourseOption
import una.codice.core.domain.model.CoverageStatus
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.model.SyncStatus
import una.codice.core.domain.model.TranscriptionBlock
import una.codice.core.domain.repository.CourseSelectionRepository
import una.codice.core.domain.repository.CoverageRepository
import una.codice.core.domain.repository.NoteRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.auth.ObserveSessionUseCase
import una.codice.core.domain.usecase.file.safeFileName
import una.codice.core.domain.usecase.note.ExtractNoteFromImageUseCase
import una.codice.core.domain.usecase.note.ExtractNoteFromPagesUseCase

@HiltViewModel
class StudentCaptureViewModel @Inject constructor(
    private val extractNoteFromImageUseCase: ExtractNoteFromImageUseCase,
    private val extractNoteFromPagesUseCase: ExtractNoteFromPagesUseCase,
    private val observeSession: ObserveSessionUseCase,
    private val noteRepository: NoteRepository,
    private val coverageRepository: CoverageRepository,
    private val courseSelection: CourseSelectionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StudentCaptureUiState())
    val state: StateFlow<StudentCaptureUiState> = _state.asStateFlow()

    // El servidor identifica al estudiante por su token; este id solo cumple con la firma de los repositorios.
    private var studentId: String = ""

    // Solo una carga de cobertura a la vez: si el estudiante cambia de curso, la del curso anterior se cancela.
    private var coverageJob: Job? = null

    init {
        viewModelScope.launch {
            // Observar sesión para el nombre
            launch {
                observeSession().collect { session ->
                    if (session != null) {
                        studentId = session.userId
                        if (session.userName.isNotBlank()) {
                            _state.update { it.copy(studentName = session.userName) }
                        }
                    }
                }
            }

            // Observar matrículas aprobadas del estudiante para poblar las pestañas de cursos reales
            launch {
                combine(courseSelection.observeCourses(), courseSelection.selectedGroupId) { list, selected ->
                    val options = list.map { CourseOption(it.id, "${it.courseCode} · ${it.courseName}") }
                    val effectiveSelected = selected?.takeIf { id -> list.any { it.id == id } } ?: options.firstOrNull()?.id
                    options to effectiveSelected
                }.collect { (options, selectedId) ->
                    val changed = selectedId != _state.value.selectedCourseId
                    _state.update { it.copy(courses = options, selectedCourseId = selectedId) }
                    if (changed) {
                        // La cobertura del curso anterior no sirve para el nuevo: se vacía y se pide la que corresponde.
                        _state.update { it.copy(coverage = emptyList()) }
                        loadCoverage(selectedId)
                    }
                }
            }

            // Los apuntes del curso elegido, tal como están en el servidor
            launch {
                observeNotesOfSelectedCourse().collect { (groupId, notes) ->
                    val previews = notes.map { it.toPreview(groupId) }
                    _state.update { it.copy(capturedNotes = previews) }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeNotesOfSelectedCourse() =
        _state.map { it.selectedCourseId }
            .distinctUntilChanged()
            .flatMapLatest { groupId ->
                if (groupId == null) {
                    flowOf(null to emptyList<Note>())
                } else {
                    noteRepository.observeNotes(studentId, groupId).map { groupId to it }
                }
            }

    private fun TranscriptionBlock.toDraftBlock() =
        DraftBlock(text = text, confidence = confidence, isLowConfidence = requiresReview)

    // El texto del apunte es el de los bloques en orden, sin los que quedaron en blanco.
    private fun joinBlocks(blocks: List<DraftBlock>): String =
        blocks.map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString(separator = "\n")

    private fun Note.toPreview(groupId: String?) = CapturedNotePreview(
        id = id,
        courseId = groupId.orEmpty(),
        title = subtopicCode.orEmpty(),
        aiTopic = subtopicTitle.orEmpty(),
        transcribedText = confirmedText,
        timestamp = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(createdAt)),
        sourceType = "Archivo"
    )

    private fun loadCoverage(groupId: String?) {
        coverageJob?.cancel()
        if (groupId == null) return
        coverageJob = viewModelScope.launch { refreshCoverage(groupId) }
    }

    // Si la consulta falla se deja lo que ya había: un fallo de red no debe vaciar un mapa que sí se tenía.
    private suspend fun refreshCoverage(groupId: String) {
        val result = coverageRepository.getSubtopicCoverage(studentId, groupId)
        if (result is DomainResult.Success && _state.value.selectedCourseId == groupId) {
            _state.update { it.copy(coverage = result.data) }
        }
    }

    fun onCourseSelected(courseId: String?) {
        courseSelection.select(courseId)
    }

    fun onToggleSourcePicker(show: Boolean) {
        _state.update { it.copy(isSourcePickerOpen = show) }
    }

    fun onSelectSource(sourceType: String) {
        _state.update {
            it.copy(
                isSourcePickerOpen = false,
                draftSourceType = sourceType,
                isCameraActive = sourceType == "camera",
                isFilePickerOpen = sourceType == "file"
            )
        }
    }

    fun onDismissCamera() {
        _state.update { it.copy(isCameraActive = false) }
    }

    /** Las páginas que el estudiante fotografió con la cámara, en orden. Sin páginas solo se cierra la cámara. */
    fun onPhotosCaptured(pages: List<ByteArray>) {
        if (pages.isEmpty()) {
            _state.update { it.copy(isCameraActive = false) }
            return
        }
        transcribeWithAi(sourceType = "camera") { courseId -> extractNoteFromPagesUseCase(courseId, pages) }
    }

    /** El estudiante no dio permiso de cámara: se le dice y se le deja la galería como salida. */
    fun onCameraPermissionDenied() {
        _state.update {
            it.copy(
                isCameraActive = false,
                errorMessage = "Sin permiso de cámara no se puede escanear. Puedes dárselo en los ajustes del teléfono o elegir una foto de tu galería.",
            )
        }
    }

    fun onDismissFilePicker() {
        _state.update { it.copy(isFilePickerOpen = false) }
    }

    /** El selector descartó el archivo antes de leerlo completo (pasaba de 10 MB o no se pudo leer). */
    fun onFileRejected(message: String) {
        _state.update { it.copy(isCameraActive = false, isFilePickerOpen = false, errorMessage = message) }
    }

    fun onFileBytesSelected(imageBytes: ByteArray, uriString: String, sourceType: String) {
        // El nombre sale del `content://` del selector: se limpia antes de mandarlo al servidor.
        val fileName = safeFileName(uriString, "archivo_tarea.pdf")
        transcribeWithAi(sourceType) { courseId -> extractNoteFromImageUseCase(courseId, imageBytes, fileName) }
    }

    /**
     * Manda lo capturado a la IA del servidor, que puede tardar unos segundos ([StudentCaptureUiState.isTranscribing]),
     * y abre el borrador con el texto transcrito. Si falla se explica qué pasó y no se abre nada.
     */
    private fun transcribeWithAi(sourceType: String, extract: suspend (courseId: String) -> DomainResult<Note>) {
        if (_state.value.isTranscribing) return

        val courseId = _state.value.selectedCourseId
        if (courseId == null) {
            _state.update {
                it.copy(
                    isCameraActive = false,
                    isFilePickerOpen = false,
                    errorMessage = "Elige un curso antes de agregar un apunte.",
                )
            }
            return
        }
        _state.update { it.copy(isCameraActive = false, isFilePickerOpen = false, isTranscribing = true) }
        viewModelScope.launch {
            when (val result = extract(courseId)) {
                is DomainResult.Success -> {
                    val note = result.data
                    val detectedTopic = if (_state.value.isSyllabusAvailable) {
                        note.subtopicTitle ?: "Sin clasificar"
                    } else {
                        "Sin clasificar"
                    }
                    val blocks = note.transcription.map { it.toDraftBlock() }
                    _state.update {
                        it.copy(
                            isCameraActive = false,
                            isFilePickerOpen = false,
                            isTranscribing = false,
                            isEditModalOpen = true,
                            draftSourceType = sourceType,
                            draftSubtopic = detectedTopic,
                            // Con bloques el texto sale de ellos; sin bloques se edita el texto completo tal cual llegó.
                            draftBlocks = blocks,
                            draftText = if (blocks.isEmpty()) note.confirmedText else joinBlocks(blocks),
                            // Un id nuevo por foto: guardar dos veces el mismo borrador no duplica el apunte.
                            draftId = UUID.randomUUID().toString(),
                        )
                    }
                }
                is DomainResult.Failure -> {
                    // Sin texto inventado: se le dice al estudiante qué pasó y el borrador no se abre.
                    _state.update {
                        it.copy(
                            isCameraActive = false,
                            isFilePickerOpen = false,
                            isTranscribing = false,
                            errorMessage = result.error.message,
                        )
                    }
                }
            }
        }
    }

    fun onUpdateDraftSubtopic(subtopic: String) {
        _state.update { it.copy(draftSubtopic = subtopic) }
    }

    fun onUpdateDraftText(text: String) {
        _state.update { it.copy(draftText = text) }
    }

    /**
     * El estudiante corrigió el texto de un bloque: queda revisado, y el texto del apunte se vuelve a armar con todos
     * los bloques. Corregir un bloque seguro no cambia nada más que su texto.
     */
    fun onUpdateDraftBlock(index: Int, text: String) {
        _state.update { state ->
            val block = state.draftBlocks.getOrNull(index) ?: return@update state
            val blocks = state.draftBlocks.toMutableList().also { it[index] = block.copy(text = text, isConfirmed = true) }
            state.copy(draftBlocks = blocks, draftText = joinBlocks(blocks))
        }
    }

    /** El estudiante vio un bloque dudoso y está bien como lo leyó la IA: queda revisado sin cambiar su texto. */
    fun onConfirmDraftBlock(index: Int) {
        _state.update { state ->
            val block = state.draftBlocks.getOrNull(index) ?: return@update state
            val blocks = state.draftBlocks.toMutableList().also { it[index] = block.copy(isConfirmed = true) }
            state.copy(draftBlocks = blocks)
        }
    }

    fun onDismissEditModal() {
        _state.update { it.copy(isEditModalOpen = false, draftBlocks = emptyList()) }
    }

    fun onDismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun onDismissInfo() {
        _state.update { it.copy(infoMessage = null) }
    }

    /**
     * Guarda el apunte que el estudiante confirmó y, si el servidor lo acepta, lo manda a auditar: la IA lo compara
     * con el temario y actualiza la cobertura. Si guardar falla, el borrador sigue abierto para reintentar.
     */
    fun onSaveCapturedNote() {
        val current = _state.value
        if (current.isSaving) return

        val groupId = current.selectedCourseId
        if (groupId == null) {
            _state.update { it.copy(errorMessage = "Elige un curso antes de guardar el apunte.") }
            return
        }
        // Mismo bloqueo que el botón de la pantalla: un bloque dudoso se confirma o se corrige antes de guardar y auditar.
        val toReview = current.draftBlocksToReview
        if (toReview > 0) {
            val label = if (toReview == 1) "1 bloque dudoso" else "$toReview bloques dudosos"
            _state.update { it.copy(errorMessage = "Confirma o corrige $label antes de guardar el apunte.") }
            return
        }
        if (current.draftText.isBlank()) {
            _state.update { it.copy(errorMessage = "El apunte está vacío: revisa el texto antes de guardarlo.") }
            return
        }

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val draft = Note(
                id = current.draftId.ifBlank { UUID.randomUUID().toString() },
                subtopicId = current.subtopicIdOf(current.draftSubtopic),
                moduleId = null,
                moduleLabel = null,
                subtopicCode = null,
                subtopicTitle = current.draftSubtopic,
                confirmedText = current.draftText,
                pageCount = 1,
                syncStatus = SyncStatus.QUEUED,
                createdAt = System.currentTimeMillis()
            )
            when (val saved = noteRepository.saveNote(groupId, draft)) {
                is DomainResult.Failure -> _state.update {
                    it.copy(isSaving = false, errorMessage = saved.error.message)
                }
                is DomainResult.Success -> {
                    _state.update {
                        it.copy(isSaving = false, isEditModalOpen = false, draftBlocks = emptyList(), isAuditing = true)
                    }
                    auditSavedNote(saved.data.id, groupId)
                }
            }
        }
    }

    private suspend fun auditSavedNote(noteId: String, groupId: String) {
        when (val audit = noteRepository.auditNote(noteId)) {
            is DomainResult.Success -> {
                refreshCoverage(groupId)
                _state.update { it.copy(isAuditing = false, infoMessage = auditSummary(audit.data)) }
            }
            is DomainResult.Failure -> _state.update {
                it.copy(
                    isAuditing = false,
                    errorMessage = "Tu apunte se guardó, pero no se pudo auditar: ${audit.error.message}",
                )
            }
        }
    }

    private fun auditSummary(touched: List<SubtopicCoverage>): String {
        if (touched.isEmpty()) return "La IA no encontró subtemas del temario en este apunte."
        return touched.joinToString(separator = "\n") { "• ${it.subtopicCode} ${it.subtopicTitle}: ${it.status.label()}" }
    }

    private fun CoverageStatus.label() = when (this) {
        CoverageStatus.NO_NOTES -> "sin apuntes"
        CoverageStatus.PARTIAL -> "parcial"
        CoverageStatus.COVERED -> "cubierto"
        CoverageStatus.REINFORCED -> "reforzado"
    }
}

/**
 * "Agregar apunte" desde la Biblioteca lleva a Inicio; esta marca hace que Inicio abra solo el diálogo de captura al llegar.
 * Se consume una vez: volver a Inicio más tarde por la barra de pestañas no abre nada.
 */
internal object CaptureRequest {
    private val pending = java.util.concurrent.atomic.AtomicBoolean(false)
    fun ask() = pending.set(true)
    fun consume(): Boolean = pending.getAndSet(false)
}
