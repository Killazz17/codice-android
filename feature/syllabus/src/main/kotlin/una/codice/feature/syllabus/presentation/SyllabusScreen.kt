package una.codice.feature.syllabus.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import una.codice.core.domain.usecase.file.MAX_UPLOAD_BYTES
import una.codice.core.domain.usecase.file.readAtMost
import una.codice.core.domain.usecase.file.safeFileName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import una.codice.core.designsystem.component.CourseOption
import una.codice.core.designsystem.component.CourseSelector
import una.codice.core.designsystem.component.CtaButton
import una.codice.core.designsystem.component.EmptyState
import una.codice.core.designsystem.component.GlassCard
import una.codice.core.designsystem.component.Pill
import una.codice.core.designsystem.component.CodiceLoader
import una.codice.core.designsystem.component.CtaButton
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.theme.LocalAccent
import una.codice.core.designsystem.theme.CodiceColors
import kotlin.math.roundToInt
import una.codice.core.domain.model.CourseModule
import una.codice.core.domain.model.MoveDirection
import una.codice.core.domain.model.Subtopic
import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.usecase.syllabus.SyllabusIssue
import una.codice.feature.syllabus.R
import androidx.compose.ui.res.stringResource

/**
 * Punto de entrada de la pantalla de temario del profesor ([IA-02]).
 */
@Composable
fun SyllabusRoute(
    courseId: String = "",
    viewModel: SyllabusViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Un curso llega por argumento solo si alguien lo pide; si no, el ViewModel elige el primero del profesor.
    LaunchedEffect(courseId) {
        if (courseId.isNotBlank() && state.selectedCourseId.isBlank()) {
            viewModel.onCourseSelected(courseId)
        }
    }

    SyllabusScreen(
        state = state,
        callbacks = SyllabusCallbacks(
            onCourseSelected = viewModel::onCourseSelected,
            onAddModule = viewModel::onAddModule,
            onModuleTitleChanged = viewModel::onModuleTitleChanged,
            onRemoveModule = viewModel::onRemoveModule,
            onAddSubtopic = viewModel::onAddSubtopic,
            onSubtopicCodeChanged = viewModel::onSubtopicCodeChanged,
            onSubtopicTitleChanged = viewModel::onSubtopicTitleChanged,
            onSubtopicObjectivesChanged = viewModel::onSubtopicObjectivesChanged,
            onRemoveSubtopic = viewModel::onRemoveSubtopic,
            onConfirmItem = viewModel::onConfirmItem,
            onMoveModule = viewModel::onMoveModule,
            onMoveSubtopic = viewModel::onMoveSubtopic,
            onSaveDraft = viewModel::onSaveDraft,
            onPublish = viewModel::onPublish,
            onConfirmOverwrite = viewModel::onConfirmOverwrite,
            onDismissOverwrite = viewModel::onDismissOverwrite,
            onExtractFromPdf = viewModel::onExtractFromPdf,
            onRetryExtraction = viewModel::onRetryExtraction,
            onPdfRejected = viewModel::onPdfRejected,
        ),
        modifier = modifier,
    )
}

/**
 * Las acciones de la pantalla, agrupadas.
 */
data class SyllabusCallbacks(
    val onCourseSelected: (courseId: String) -> Unit = {},
    val onAddModule: () -> Unit = {},
    val onModuleTitleChanged: (moduleId: String, title: String) -> Unit = { _, _ -> },
    val onRemoveModule: (moduleId: String) -> Unit = {},
    val onAddSubtopic: (moduleId: String) -> Unit = {},
    val onSubtopicCodeChanged: (moduleId: String, subtopicId: String, code: String) -> Unit = { _, _, _ -> },
    val onSubtopicTitleChanged: (moduleId: String, subtopicId: String, title: String) -> Unit = { _, _, _ -> },
    val onSubtopicObjectivesChanged: (moduleId: String, subtopicId: String, texto: String) -> Unit = { _, _, _ -> },
    val onRemoveSubtopic: (moduleId: String, subtopicId: String) -> Unit = { _, _ -> },
    val onConfirmItem: (id: String) -> Unit = {},
    val onMoveModule: (moduleId: String, direction: MoveDirection) -> Unit = { _, _ -> },
    val onMoveSubtopic: (moduleId: String, subtopicId: String, direction: MoveDirection) -> Unit = { _, _, _ -> },
    val onSaveDraft: () -> Unit = {},
    val onPublish: () -> Unit = {},
    val onConfirmOverwrite: () -> Unit = {},
    val onDismissOverwrite: () -> Unit = {},
    val onExtractFromPdf: (pdfBytes: ByteArray, filename: String) -> Unit = { _, _ -> },
    val onRetryExtraction: () -> Unit = {},
    val onPdfRejected: (message: String) -> Unit = {},
)

/**
 * La pantalla de temario del profesor con soporte para extracción de PDF con IA ([IA-02]).
 */
@Composable
fun SyllabusScreen(
    state: SyllabusUiState,
    callbacks: SyllabusCallbacks,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        when {
            state.isLoading -> LoadingContent()

            // Sin cursos no hay temario que armar ni a quién pedirle uno.
            state.availableCourses.isEmpty() && state.selectedCourseId.isBlank() -> NoCoursesContent()

            state.isExtractingAi -> AiExtractingContent()

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    SyllabusHeader(state = state, callbacks = callbacks)
                }

                if (state.issues.isNotEmpty()) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            IssuesCard(state)
                        }
                    }
                }

                if (state.syllabus.modules.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            EmptyState(
                                title = stringResource(R.string.syl_this_course_has_no_syllabus_yet),
                                description = stringResource(R.string.syl_upload_the_course_program_as_a_pdf_abo),
                            )
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = callbacks.onAddModule) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = LocalAccent.current.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.syl_add_module_by_hand), color = LocalAccent.current.primary)
                            }
                        }
                    }
                } else {
                    if (state.itemsToReview > 0) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                ReviewSummary(state.itemsToReview)
                            }
                        }
                    }

                    val modulos = state.syllabus.modules.sortedBy { it.number }
                    items(modulos, key = { it.id }) { modulo ->
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            ModuleCard(
                                modulo = modulo,
                                state = state,
                                callbacks = callbacks,
                                isFirst = modulo.id == modulos.first().id,
                                isLast = modulo.id == modulos.last().id,
                            )
                        }
                    }

                    item {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            TextButton(onClick = callbacks.onAddModule) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = LocalAccent.current.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.syl_add_module_by_hand), color = LocalAccent.current.primary)
                            }
                        }
                    }
                }

                item {
                    SyllabusActions(state, callbacks)
                }
            }
        }

        if (state.isConfirmingOverwrite) {
            OverwriteDialog(callbacks)
        }
    }
}

@Composable
private fun NoCoursesContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            title = stringResource(R.string.syl_you_don_t_have_any_courses_yet),
            description = stringResource(R.string.syl_create_a_course_from_the_courses_tab_s),
        )
    }
}

/** Cuántos módulos y subtemas piden la atención del profesor antes de publicar. */
@Composable
private fun ReviewSummary(count: Int) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CodiceColors.Yellow, RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (count == 1) stringResource(R.string.syl_1_item_to_review) else stringResource(R.string.syl_items_to_review, count),
                color = CodiceColors.Yellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.syl_they_re_marked_in_amber_the_ai_was_uns),
                color = CodiceColors.TextMid,
                fontSize = 12.sp,
            )
        }
    }
}

/** Aviso antes de publicar encima del temario que el curso ya tiene en el servidor. */
@Composable
private fun OverwriteDialog(callbacks: SyllabusCallbacks) {
    AlertDialog(
        onDismissRequest = callbacks.onDismissOverwrite,
        containerColor = CodiceColors.GlassHigh,
        title = { Text(stringResource(R.string.syl_this_course_already_has_a_syllabus), color = CodiceColors.TextHigh, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = stringResource(R.string.syl_overwrite_warning),
                color = CodiceColors.TextMid,
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = callbacks.onConfirmOverwrite) {
                Text(stringResource(R.string.syl_publish_anyway), color = CodiceColors.Lime, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = callbacks.onDismissOverwrite) {
                Text(stringResource(R.string.syl_cancel), color = CodiceColors.TextMid)
            }
        },
    )
}

@Composable
private fun SyllabusHeader(
    state: SyllabusUiState,
    callbacks: SyllabusCallbacks,
) {
    val context = LocalContext.current
    // El callback del selector de archivos no es @Composable: el mensaje se resuelve antes.
    val pdfRejectedMessage = stringResource(R.string.syl_couldn_t_read_the_file_or_it_s_larger_)
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            // `readAtMost` corta al pasar de 10 MB: un archivo enorme elegido por error no llena la memoria del teléfono.
            val pdfBytes = runCatching {
                context.contentResolver.openInputStream(it)?.use { stream -> stream.readAtMost(MAX_UPLOAD_BYTES) }
            }.getOrNull()

            if (pdfBytes == null) {
                callbacks.onPdfRejected(pdfRejectedMessage)
            } else {
                // El nombre lo pone quien guardó el archivo: se limpia antes de mandarlo al servidor.
                val displayName = runCatching {
                    var name: String? = null
                    context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                    name
                }.getOrNull()

                callbacks.onExtractFromPdf(pdfBytes, safeFileName(displayName, "programa.pdf"))
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradientText(
                text = stringResource(R.string.syl_course_syllabus),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Pill(
                text = if (state.syllabus.isPublished) stringResource(R.string.syl_published) else stringResource(R.string.syl_draft),
                color = if (state.syllabus.isPublished) CodiceColors.Lime else CodiceColors.Yellow,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.syl_students_only_see_the_syllabus_after_y),
            color = CodiceColors.TextMid,
            fontSize = 13.sp,
        )

        if (state.availableCourses.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            CourseSelector(
                options = state.availableCourses,
                selectedId = state.selectedCourseId.ifBlank { state.syllabus.courseId },
                onSelect = { selected ->
                    selected?.let { callbacks.onCourseSelected(it) }
                },
                // Esta pantalla ya tiene su margen: sin esto los chips quedaban sangrados respecto al título.
                horizontalPadding = 0.dp,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Tarjeta IA para subir el PDF ([IA-02])
        // La tarjeta de la IA es la protagonista de la pantalla: lleva el borde neón.
        GlassCard(modifier = Modifier.fillMaxWidth(), glow = true) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "IA",
                        tint = LocalAccent.current.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.syl_extract_syllabus_from_pdf_with_ai),
                        color = CodiceColors.TextHigh,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.syl_upload_the_course_program_as_a_pdf_the),
                    color = CodiceColors.TextMid,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                // Mientras la IA analiza, el botón muestra su rueda; el texto largo ya aparece en la pantalla de análisis.
                CtaButton(
                    text = stringResource(R.string.syl_upload_program_pdf),
                    onClick = { pdfLauncher.launch(arrayOf("application/pdf", "application/octet-stream", "*/*")) },
                    isLoading = state.isExtractingAi,
                )

                if (state.uploadedFileName != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CodiceColors.GlassHigh, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = LocalAccent.current.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.uploadedFileName,
                                    color = CodiceColors.TextHigh,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                state.uploadedFileSize?.let { size ->
                                    Text(
                                        text = stringResource(R.string.syl_program_pdf_uploaded, size),
                                        color = CodiceColors.TextLow,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Pill(text = stringResource(R.string.syl_uploaded), color = CodiceColors.Lime)
                        }
                    }
                }
            }
        }

        if (state.aiConfidenceMessage != null) {
            Spacer(Modifier.height(8.dp))
            Pill(
                text = state.aiConfidenceMessage,
                color = LocalAccent.current.primary,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CodiceLoader()
    }
}

@Composable
private fun AiExtractingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CodiceLoader(size = 48.dp, strokeWidth = 5.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.syl_analyzing_program_pdf_with_ai),
            color = CodiceColors.TextHigh,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.syl_generating_a_proposal_of_modules_subto),
            color = CodiceColors.TextMid,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))
        // Los pasos de verdad: el archivo ya se revisó antes de llegar aquí; subir y analizar es una sola llamada al
        // servidor (puede tardar medio minuto) y el borrador se guarda al recibir la respuesta.
        ExtractionStep(label = stringResource(R.string.syl_file_checked_it_s_a_valid_pdf), state = StepState.DONE)
        ExtractionStep(label = stringResource(R.string.syl_uploading_and_analyzing_the_syllabus_w), state = StepState.ACTIVE)
        ExtractionStep(label = stringResource(R.string.syl_saving_the_result_as_a_draft), state = StepState.PENDING)
    }
}

private enum class StepState { DONE, ACTIVE, PENDING }

@Composable
private fun ExtractionStep(label: String, state: StepState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = when (state) {
                StepState.DONE -> "✓"
                StepState.ACTIVE -> "●"
                StepState.PENDING -> "○"
            },
            color = if (state == StepState.PENDING) CodiceColors.TextLow else CodiceColors.Lime,
            fontSize = 14.sp,
            modifier = Modifier.width(22.dp),
        )
        Text(
            text = label,
            color = if (state == StepState.PENDING) CodiceColors.TextLow else CodiceColors.TextHigh,
            fontSize = 13.sp,
            fontWeight = if (state == StepState.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}



@Composable
private fun IssuesCard(state: SyllabusUiState) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.blockingIssues.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.syl_can_t_publish_yet),
                    color = CodiceColors.TextHigh,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                state.blockingIssues.forEach { issue ->
                    IssueRow(issue.message, CodiceColors.Orange)
                }
            }
            if (state.warnings.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.syl_warnings),
                    color = CodiceColors.TextHigh,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                state.warnings.forEach { issue ->
                    IssueRow(issue.message, CodiceColors.Yellow)
                }
            }
        }
    }
}

@Composable
private fun IssueRow(message: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.Top) {
        Text(text = "•", color = color, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(text = message, color = CodiceColors.TextMid, fontSize = 13.sp)
    }
}

@Composable
private fun ModuleCard(
    modulo: CourseModule,
    state: SyllabusUiState,
    callbacks: SyllabusCallbacks,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val reason = attentionReason(state, modulo)
    val subtemas = modulo.subtopics.sortedBy { it.position }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (reason != null) Modifier.border(1.dp, CodiceColors.Yellow, RoundedCornerShape(16.dp)) else Modifier),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.syl_module, modulo.number),
                    color = CodiceColors.TextLow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (reason != null) {
                    Pill(text = stringResource(R.string.syl_to_review), color = CodiceColors.Yellow)
                }
                MoveButtons(
                    label = stringResource(R.string.syl_module_2, modulo.number),
                    canMoveUp = !isFirst,
                    canMoveDown = !isLast,
                    onMove = { direction -> callbacks.onMoveModule(modulo.id, direction) },
                )
                IconButton(onClick = { callbacks.onRemoveModule(modulo.id) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.syl_delete_module, modulo.number),
                        tint = CodiceColors.TextLow,
                    )
                }
            }

            ReviewReason(reason)

            SyllabusField(
                value = modulo.title,
                onValueChange = { callbacks.onModuleTitleChanged(modulo.id, it) },
                placeholder = stringResource(R.string.syl_module_title),
            )

            if (modulo.isLowConfidence && modulo.id !in state.reviewedIds) {
                ConfirmButton(onClick = { callbacks.onConfirmItem(modulo.id) })
            }

            subtemas.forEachIndexed { index, subtema ->
                SubtopicBlock(
                    moduleId = modulo.id,
                    subtema = subtema,
                    state = state,
                    callbacks = callbacks,
                    isFirst = index == 0,
                    isLast = index == subtemas.lastIndex,
                )
            }

            TextButton(onClick = { callbacks.onAddSubtopic(modulo.id) }) {
                Icon(Icons.Default.Add, contentDescription = null, tint = LocalAccent.current.primary)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.syl_add_subtopic), color = LocalAccent.current.primary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SubtopicBlock(
    moduleId: String,
    subtema: Subtopic,
    state: SyllabusUiState,
    callbacks: SyllabusCallbacks,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val reason = attentionReason(state, subtema)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (reason != null) {
                    Modifier.border(1.dp, CodiceColors.Yellow, RoundedCornerShape(12.dp)).padding(8.dp)
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SyllabusField(
                value = subtema.code,
                onValueChange = { callbacks.onSubtopicCodeChanged(moduleId, subtema.id, it) },
                placeholder = "4.4",
                modifier = Modifier.width(88.dp),
            )
            Spacer(Modifier.width(8.dp))
            SyllabusField(
                value = subtema.title,
                onValueChange = { callbacks.onSubtopicTitleChanged(moduleId, subtema.id, it) },
                placeholder = stringResource(R.string.syl_subtopic_title),
                modifier = Modifier.weight(1f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (reason != null) {
                Pill(text = stringResource(R.string.syl_to_review), color = CodiceColors.Yellow)
            }
            Spacer(Modifier.weight(1f))
            MoveButtons(
                label = stringResource(R.string.syl_subtopic, subtema.code),
                canMoveUp = !isFirst,
                canMoveDown = !isLast,
                onMove = { direction -> callbacks.onMoveSubtopic(moduleId, subtema.id, direction) },
            )
            IconButton(onClick = { callbacks.onRemoveSubtopic(moduleId, subtema.id) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.syl_delete_subtopic, subtema.code),
                    tint = CodiceColors.TextLow,
                )
            }
        }

        ReviewReason(reason)

        SyllabusField(
            value = subtema.objectives.joinToString("\n"),
            onValueChange = { callbacks.onSubtopicObjectivesChanged(moduleId, subtema.id, it) },
            placeholder = stringResource(R.string.syl_objectives_one_per_line),
            singleLine = false,
        )

        if (subtema.isLowConfidence && subtema.id !in state.reviewedIds) {
            ConfirmButton(onClick = { callbacks.onConfirmItem(subtema.id) })
        }
    }
}

/** Subir y bajar un módulo o subtema dentro de su lista; el que está en el borde no se puede mover más allá. */
@Composable
private fun MoveButtons(
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (MoveDirection) -> Unit,
) {
    IconButton(onClick = { onMove(MoveDirection.UP) }, enabled = canMoveUp) {
        Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = stringResource(R.string.syl_move_up, label),
            tint = if (canMoveUp) CodiceColors.TextMid else CodiceColors.Border,
        )
    }
    IconButton(onClick = { onMove(MoveDirection.DOWN) }, enabled = canMoveDown) {
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.syl_move_down, label),
            tint = if (canMoveDown) CodiceColors.TextMid else CodiceColors.Border,
        )
    }
}

/** Por qué algo está marcado: el texto acompaña al color, que solo no basta. */
@Composable
private fun ReviewReason(reason: String?) {
    if (reason != null) {
        Text(text = reason, color = CodiceColors.Yellow, fontSize = 12.sp)
    }
}

@Composable
private fun ConfirmButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(stringResource(R.string.syl_looks_good_confirm), color = CodiceColors.Yellow, fontSize = 13.sp)
    }
}

@Composable
private fun attentionReason(state: SyllabusUiState, module: CourseModule): String? = when {
    !state.needsAttention(module) -> null
    module.title.isBlank() -> stringResource(R.string.syl_the_module_title_is_missing)
    module.isLowConfidence && module.id !in state.reviewedIds ->
        stringResource(R.string.syl_the_ai_was_unsure_about_this_module_co, (module.confidence * 100).roundToInt())
    else -> stringResource(R.string.syl_this_module_has_no_subtopics)
}

@Composable
private fun attentionReason(state: SyllabusUiState, subtopic: Subtopic): String? = when {
    !state.needsAttention(subtopic) -> null
    subtopic.title.isBlank() -> stringResource(R.string.syl_the_subtopic_title_is_missing)
    subtopic.isLowConfidence && subtopic.id !in state.reviewedIds ->
        stringResource(R.string.syl_the_ai_was_unsure_about_this_subtopic_, (subtopic.confidence * 100).roundToInt())
    else -> stringResource(R.string.syl_no_objectives_notes_can_t_be_audited_a)
}

@Composable
private fun SyllabusField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = CodiceColors.TextLow, fontSize = 13.sp) },
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = CodiceColors.TextHigh,
            unfocusedTextColor = CodiceColors.TextHigh,
            focusedBorderColor = CodiceColors.Lime,
            unfocusedBorderColor = CodiceColors.Border,
            cursorColor = CodiceColors.Lime,
        ),
    )
}

@Composable
private fun SyllabusActions(state: SyllabusUiState, callbacks: SyllabusCallbacks) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.errorMessage?.let { mensaje ->
            Text(text = mensaje, color = CodiceColors.Orange, fontSize = 13.sp)
            if (state.canRetryExtraction) {
                TextButton(onClick = callbacks.onRetryExtraction) {
                    Text(stringResource(R.string.syl_retry_with_the_same_pdf), color = CodiceColors.Lime, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (state.justPublished) {
            Text(
                text = stringResource(R.string.syl_syllabus_published_it_s_now_visible_to),
                color = CodiceColors.Lime,
                fontSize = 13.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                onClick = callbacks.onSaveDraft,
                enabled = !state.isSaving && !state.isPublishing && !state.isExtractingAi,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (state.isSaving) stringResource(R.string.syl_saving) else stringResource(R.string.syl_save_draft),
                    color = CodiceColors.TextMid,
                )
            }
            CtaButton(
                text = stringResource(R.string.syl_publish_to_course),
                onClick = callbacks.onPublish,
                enabled = state.canPublish && !state.isExtractingAi,
                isLoading = state.isPublishing,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------- Previews ----------

private val temarioDePrueba = Syllabus(
    courseId = "EIF411",
    modules = listOf(
        CourseModule(
            id = "mod-1",
            number = 1,
            title = "Fundamentos de Android",
            subtopics = listOf(
                Subtopic("sub-1", "1.1", "Ciclo de vida", 0, listOf("Explicar cada estado")),
            ),
        ),
        CourseModule(
            id = "mod-4",
            number = 4,
            title = "Programación distribuida",
            // Lo que la IA dudó o dejó incompleto sale en ámbar.
            subtopics = listOf(Subtopic("sub-4", "4.4", "Servicios REST", 0, emptyList(), confidence = 0.45f)),
            confidence = 0.6f,
        ),
    ),
)

@Preview(name = "Con temario", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun SyllabusScreenPreview() {
    MaterialTheme {
        SyllabusScreen(
            state = SyllabusUiState(
                syllabus = temarioDePrueba,
                isLoading = false,
                availableCourses = listOf(
                    CourseOption("EIF411", "EIF411"),
                    CourseOption("EIF303", "EIF303"),
                ),
                selectedCourseId = "EIF411",
            ),
            callbacks = SyllabusCallbacks(),
        )
    }
}
