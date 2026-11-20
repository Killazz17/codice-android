package una.codice.feature.notes.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import una.codice.core.designsystem.component.CodiceLoader
import una.codice.core.designsystem.component.CourseSelector
import una.codice.core.designsystem.component.CtaButton
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.component.glassPanel
import una.codice.core.designsystem.theme.LocalAccent
import una.codice.core.designsystem.component.Meter
import una.codice.core.designsystem.component.Pill
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.domain.usecase.file.MAX_UPLOAD_BYTES
import una.codice.core.domain.usecase.file.readAtMost
import una.codice.feature.notes.presentation.camera.CameraCaptureOverlay
import kotlin.math.roundToInt
import una.codice.feature.notes.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.LaunchedEffect

@Composable
fun StudentCaptureRoute(
    modifier: Modifier = Modifier,
    onNavigateToEnrollment: () -> Unit = {},
    viewModel: StudentCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Si la persona llegó desde "Agregar apunte" en Apuntes, el diálogo de captura se abre solo.
    LaunchedEffect(Unit) { if (CaptureRequest.consume()) viewModel.onToggleSourcePicker(true) }

    StudentCaptureScreen(
        state = state,
        onCourseSelected = viewModel::onCourseSelected,
        onToggleSourcePicker = viewModel::onToggleSourcePicker,
        onSelectSource = viewModel::onSelectSource,
        onDismissCamera = viewModel::onDismissCamera,
        onPhotosCaptured = viewModel::onPhotosCaptured,
        onCameraPermissionDenied = viewModel::onCameraPermissionDenied,
        onDismissFilePicker = viewModel::onDismissFilePicker,
        onFileBytesSelected = viewModel::onFileBytesSelected,
        onUpdateDraftSubtopic = viewModel::onUpdateDraftSubtopic,
        onUpdateDraftText = viewModel::onUpdateDraftText,
        onUpdateDraftBlock = viewModel::onUpdateDraftBlock,
        onConfirmDraftBlock = viewModel::onConfirmDraftBlock,
        onDismissEditModal = viewModel::onDismissEditModal,
        onSaveCapturedNote = viewModel::onSaveCapturedNote,
        onDismissError = viewModel::onDismissError,
        onDismissInfo = viewModel::onDismissInfo,
        onFileRejected = viewModel::onFileRejected,
        onNavigateToEnrollment = onNavigateToEnrollment,
        modifier = modifier
    )
}

@Composable
fun StudentCaptureScreen(
    state: StudentCaptureUiState,
    onCourseSelected: (String?) -> Unit,
    onToggleSourcePicker: (Boolean) -> Unit,
    onSelectSource: (String) -> Unit,
    onDismissCamera: () -> Unit,
    onPhotosCaptured: (List<ByteArray>) -> Unit,
    onCameraPermissionDenied: () -> Unit,
    onDismissFilePicker: () -> Unit,
    onFileBytesSelected: (ByteArray, String, String) -> Unit,
    onUpdateDraftSubtopic: (String) -> Unit,
    onUpdateDraftText: (String) -> Unit,
    onUpdateDraftBlock: (Int, String) -> Unit,
    onConfirmDraftBlock: (Int) -> Unit,
    onDismissEditModal: () -> Unit,
    onSaveCapturedNote: () -> Unit,
    onDismissError: () -> Unit = {},
    onNavigateToEnrollment: () -> Unit = {},
    onDismissInfo: () -> Unit = {},
    onFileRejected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // pickFile corre en un callback, fuera de la composición: el mensaje se resuelve antes.
    val fileRejectedMessage = stringResource(R.string.notes_couldn_t_read_the_file_or_it_s_larger_)

    // `readAtMost` corta al pasar de 10 MB: un archivo enorme elegido por error no llena la memoria del teléfono.
    fun pickFile(uri: Uri) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream -> stream.readAtMost(MAX_UPLOAD_BYTES) }
        }.getOrNull()
        if (bytes == null) {
            onFileRejected(fileRejectedMessage)
        } else {
            onFileBytesSelected(bytes, uri.toString(), "file")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let(::pickFile) }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(::pickFile) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Encabezado: saludo, cuánto falta y el botón principal. El botón lleva TEXTO: antes era un "+" suelto y
            // nadie adivinaba que ahí se agrega un apunte.
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    GradientText(
                        text = if (state.studentName.isBlank()) stringResource(R.string.notes_hi) else stringResource(R.string.notes_hi_2, state.studentName),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pluralStringResource(R.plurals.notes_week_topics_left, state.openVoidsCount, state.weekNumber, state.openVoidsCount),
                        color = CodiceColors.TextMid,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    CtaButton(
                        text = stringResource(R.string.notes_scan_note),
                        onClick = { onToggleSourcePicker(true) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Course Selector Tabs
            item {
                CourseSelector(
                    options = state.courses,
                    selectedId = state.selectedCourseId,
                    onSelect = onCourseSelected,
                    horizontalPadding = 20.dp,
                    joinLabel = stringResource(R.string.notes_join),
                    // "Unirme" vive en la pestaña Cursos; este chip solo aparece cuando todavía no hay ningún curso que elegir.
                    onJoin = onNavigateToEnrollment.takeIf { state.courses.isEmpty() }
                )
            }

            // Dynamic Content: Empty state or dashboard
            if (!state.hasNotes) {
                item {
                    EmptyCaptureContent(
                        courseName = state.courses.find { it.id == state.selectedCourseId }?.label ?: stringResource(R.string.notes_this_course),
                        onAddClick = { onToggleSourcePicker(true) }
                    )
                }
            } else {
                // 3 Metric Cards Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            value = "${state.globalCoverage.roundToInt()} %",
                            label = stringResource(R.string.notes_metric_covered),
                            accentColor = LocalAccent.current.primary
                        )
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            value = "${state.openVoidsCount}",
                            label = stringResource(R.string.notes_metric_to_cover),
                            accentColor = CodiceColors.Orange
                        )
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            value = "${state.reinforcedCount}",
                            label = stringResource(R.string.notes_metric_reinforced),
                            accentColor = CodiceColors.Blue
                        )
                    }
                }

                // Qué significa cada cifra, en una línea: "cobertura" y "reforzados" no se entienden solos.
                item {
                    Text(
                        text = stringResource(R.string.notes_metric_legend),
                        color = CodiceColors.TextLow,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                // Avance por módulo Section: solo cuando el profesor ya publicó el temario
                if (state.modules.isNotEmpty()) item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .glassPanel(20.dp)
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.notes_coverage_by_module),
                                color = CodiceColors.TextHigh,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.notes_modules, state.modules.size),
                                color = CodiceColors.TextLow,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            state.modules.forEach { module ->
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(R.string.notes_mod, module.number, module.title),
                                            color = CodiceColors.TextMid,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "${module.percentage.roundToInt()}%",
                                            color = CodiceColors.TextHigh,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Meter(
                                        progress = (module.percentage / 100.0).toFloat(),
                                        color = module.color,
                                        showValue = false,
                                    )
                                }
                            }
                        }
                    }
                }

                // Próximos repasos · Repetición espaciada Section: solo cuando hay repasos reales que mostrar
                if (state.upcomingReviews.isNotEmpty()) item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .glassPanel(20.dp)
                            .padding(18.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.notes_upcoming_reviews_spaced_repetition),
                            color = CodiceColors.TextLow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.upcomingReviews.forEach { review ->
                                ReviewRow(review)
                            }
                        }
                    }
                }

                // Captured Notes Section
                item {
                    Text(
                        text = stringResource(R.string.notes_recent_notes_in_this_course),
                        color = CodiceColors.TextHigh,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)
                    )
                }
                items(state.currentCourseNotes, key = { it.id }) { note ->
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        CapturedNoteCard(note)
                    }
                }
            }
        }

        // 1. Source Picker Dialog
        if (state.isSourcePickerOpen) {
            CaptureOptionsDialog(
                onDismiss = { onToggleSourcePicker(false) },
                onOptionSelected = { type -> onSelectSource(type) }
            )
        }

        // 2. Cámara real (CameraX): una o varias páginas del apunte
        if (state.isCameraActive) {
            CameraCaptureOverlay(
                onPagesCaptured = onPhotosCaptured,
                onPermissionDenied = onCameraPermissionDenied,
                onClose = onDismissCamera
            )
        }

        // 3. File / Gallery Source Choice Dialog
        if (state.isFilePickerOpen) {
            FilePickerChoiceDialog(
                onDismiss = onDismissFilePicker,
                onSelectGallery = {
                    onDismissFilePicker()
                    galleryLauncher.launch("image/*")
                },
                onSelectDocument = {
                    onDismissFilePicker()
                    documentLauncher.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*"))
                }
            )
        }

        // 4. Edit & Review Modal
        if (state.isEditModalOpen) {
            EditCaptureDialog(
                state = state,
                onDismiss = onDismissEditModal,
                onSubtopicChange = onUpdateDraftSubtopic,
                onTextChange = onUpdateDraftText,
                onBlockTextChange = onUpdateDraftBlock,
                onBlockConfirm = onConfirmDraftBlock,
                onSave = onSaveCapturedNote
            )
        }

        // 4b. Mientras el servidor transcribe la foto o las páginas con la IA (puede tardar unos segundos)
        if (state.isTranscribing) {
            AlertDialog(
                onDismissRequest = {},
                containerColor = CodiceColors.GlassHigh,
                titleContentColor = CodiceColors.TextHigh,
                textContentColor = CodiceColors.TextMid,
                title = { Text(stringResource(R.string.notes_transcribing_your_note)) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CodiceLoader(size = 24.dp, strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.notes_the_ai_is_reading_the_text_of_your_not))
                    }
                },
                confirmButton = {},
            )
        }

        // 5. Mientras el servidor audita el apunte con la IA (puede tardar unos segundos)
        if (state.isAuditing) {
            AlertDialog(
                onDismissRequest = {},
                containerColor = CodiceColors.GlassHigh,
                titleContentColor = CodiceColors.TextHigh,
                textContentColor = CodiceColors.TextMid,
                title = { Text(stringResource(R.string.notes_auditing_your_note)) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CodiceLoader(size = 24.dp, strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.notes_the_ai_is_comparing_it_with_the_course))
                    }
                },
                confirmButton = {},
            )
        }

        // 6. Qué subtemas tocó el apunte y en qué estado quedaron
        state.infoMessage?.let { message ->
            AlertDialog(
                onDismissRequest = onDismissInfo,
                containerColor = CodiceColors.GlassHigh,
                titleContentColor = CodiceColors.TextHigh,
                textContentColor = CodiceColors.TextMid,
                title = { Text(stringResource(R.string.notes_note_saved_and_audited)) },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = onDismissInfo) { Text(stringResource(R.string.notes_done_2)) } },
            )
        }

        // 7. Error de la IA o de la captura: se explica qué pasó, no se inventa un texto.
        state.errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = onDismissError,
                containerColor = CodiceColors.GlassHigh,
                titleContentColor = CodiceColors.TextHigh,
                textContentColor = CodiceColors.TextMid,
                title = { Text(stringResource(R.string.notes_couldn_t_process_the_note)) },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(R.string.notes_got_it)) } },
            )
        }
    }
}

@Composable
private fun EmptyCaptureContent(courseName: String, onAddClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .glassPanel(20.dp)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(CodiceColors.Lime.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = CodiceColors.Lime,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.notes_no_notes_in, courseName),
                color = CodiceColors.TextHigh,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.notes_empty_hint),
                color = CodiceColors.TextMid,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            CtaButton(text = stringResource(R.string.notes_scan_or_insert_note), onClick = onAddClick)
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    accentColor: Color
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(CodiceColors.GlassHigh.copy(alpha = 0.90f), CodiceColors.Glass.copy(alpha = 0.85f))
                )
            )
            // Un tinte del color de la métrica en la esquina: cada cifra tiene su propia "luz".
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.maxDimension,
                    )
                )
            }
            .border(1.dp, accentColor.copy(alpha = 0.45f), shape)
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = value,
                color = accentColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = CodiceColors.TextMid,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ReviewRow(review: ReviewTaskItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .background(review.accentColor, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = review.subtopicTitle,
                    color = CodiceColors.TextHigh,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = review.scheduleText,
                    color = CodiceColors.TextLow,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(CodiceColors.Canvas, RoundedCornerShape(8.dp))
                .border(1.dp, CodiceColors.Border, RoundedCornerShape(8.dp))
                .clickable { }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "ir",
                color = CodiceColors.TextHigh,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CapturedNoteCard(note: CapturedNotePreview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(16.dp)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CodiceColors.Lime.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = CodiceColors.Lime,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    // El título es el NOMBRE del tema que la IA le asignó: "Apunte (2.1)" no decía de qué trataba.
                    Text(
                        text = note.aiTopic.ifBlank { stringResource(R.string.notes_unclassified) },
                        color = CodiceColors.TextHigh,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (note.title.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.notes_topic_code, note.title),
                            color = CodiceColors.Lime,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Text(
                text = note.timestamp,
                color = CodiceColors.TextLow,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = note.transcribedText,
            color = CodiceColors.TextMid,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun CaptureOptionsDialog(
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CodiceColors.GlassHigh,
        title = {
            GradientText(stringResource(R.string.notes_add_note), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.notes_choose_how_you_want_to_add_your_note),
                    color = CodiceColors.TextMid,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                        .clickable { onOptionSelected("camera") }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = CodiceColors.Lime
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.notes_scan_note_camera),
                            color = CodiceColors.TextHigh,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.notes_take_a_photo_with_the_device_camera),
                            color = CodiceColors.TextLow,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                        .clickable { onOptionSelected("file") }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = CodiceColors.Blue
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.notes_insert_photo_or_saved_file),
                            color = CodiceColors.TextHigh,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.notes_pick_from_your_phone_s_gallery_or_file),
                            color = CodiceColors.TextLow,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.notes_cancel), color = CodiceColors.TextMid)
            }
        }
    )
}

@Composable
private fun FilePickerChoiceDialog(
    onDismiss: () -> Unit,
    onSelectGallery: () -> Unit,
    onSelectDocument: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CodiceColors.GlassHigh,
        title = {
            GradientText(stringResource(R.string.notes_select_file_source), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.notes_choose_where_your_note_is_saved),
                    color = CodiceColors.TextMid,
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onSelectGallery)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = CodiceColors.Lime)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.notes_photos_gallery), color = CodiceColors.TextHigh, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.notes_pick_an_image_from_the_gallery), color = CodiceColors.TextLow, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onSelectDocument)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = CodiceColors.Blue)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.notes_phone_files), color = CodiceColors.TextHigh, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.notes_pick_a_pdf_word_or_document), color = CodiceColors.TextLow, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.notes_cancel), color = CodiceColors.TextMid)
            }
        }
    )
}

@Composable
private fun EditCaptureDialog(
    state: StudentCaptureUiState,
    onDismiss: () -> Unit,
    onSubtopicChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onBlockTextChange: (Int, String) -> Unit,
    onBlockConfirm: (Int) -> Unit,
    onSave: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CodiceColors.GlassHigh,
        title = {
            GradientText(stringResource(R.string.notes_review_and_correct_note), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            // Con varios bloques el contenido no cabe en el diálogo: se desplaza.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!state.isSyllabusAvailable) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CodiceColors.Orange.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .border(1.dp, CodiceColors.Orange.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.notes_no_syllabus_warning),
                            color = CodiceColors.TextHigh,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.notes_the_ai_processed_your_file_you_can_fix),
                        color = CodiceColors.TextMid,
                        fontSize = 13.sp
                    )
                }

                if (state.draftBlocksToReview > 0) {
                    Text(
                        stringResource(R.string.notes_the_ai_was_unsure_about_block_s_of_the, state.draftBlocksToReview),
                        color = CodiceColors.Yellow,
                        fontSize = 12.sp
                    )
                }

                // Subtopic Dropdown Selector
                Text(
                    stringResource(R.string.notes_detected_assigned_subtopic),
                    color = CodiceColors.Lime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box {
                    OutlinedButton(
                        onClick = { if (state.isSyllabusAvailable) expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CodiceColors.TextHigh)
                    ) {
                        Text(state.draftSubtopic, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        if (state.isSyllabusAvailable) {
                            Text("▼", fontSize = 10.sp)
                        }
                    }
                    if (state.isSyllabusAvailable) {
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            state.availableSubtopics.forEach { subtopic ->
                                DropdownMenuItem(
                                    text = { Text(subtopic, color = Color.Black) },
                                    onClick = {
                                        onSubtopicChange(subtopic)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Editable Transcribed Text
                Text(
                    stringResource(R.string.notes_transcribed_text_ocr),
                    color = CodiceColors.Lime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.draftBlocks.isEmpty()) {
                    OutlinedTextField(
                        value = state.draftText,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CodiceColors.Lime,
                            unfocusedBorderColor = CodiceColors.Border,
                            focusedTextColor = CodiceColors.TextHigh,
                            unfocusedTextColor = CodiceColors.TextHigh
                        )
                    )
                } else {
                    state.draftBlocks.forEachIndexed { index, block ->
                        DraftBlockCard(
                            index = index,
                            block = block,
                            enabled = !state.isSaving,
                            onTextChange = { onBlockTextChange(index, it) },
                            onConfirm = { onBlockConfirm(index) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            GradientActionButton(
                text = when {
                    state.isSaving -> stringResource(R.string.notes_saving)
                    state.draftBlocksToReview > 0 -> stringResource(R.string.notes_left_to_review, state.draftBlocksToReview)
                    else -> stringResource(R.string.notes_save_note)
                },
                enabled = !state.isSaving && state.draftBlocksToReview == 0,
                onClick = onSave,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.notes_cancel), color = CodiceColors.TextMid)
            }
        }
    )
}

/**
 * Botón compacto con el degradado del rol para la fila de botones de un diálogo (el `CtaButton` ocupa todo el ancho).
 * Deshabilitado se atenúa, igual que el `CtaButton`.
 */
@Composable
private fun GradientActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .alpha(if (enabled) 1f else 0.4f)
            .background(LocalAccent.current.brush())
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(text = text, color = CodiceColors.Canvas, fontWeight = FontWeight.Bold)
    }
}

/**
 * Un bloque de la transcripción: su texto editable y qué tan segura estaba la IA. Un bloque dudoso se marca como
 * "Por revisar" (con texto y borde ámbar, no solo color) hasta que el estudiante lo confirma o lo corrige.
 */
@Composable
private fun DraftBlockCard(
    index: Int,
    block: DraftBlock,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val accent = when {
        block.needsReview -> CodiceColors.Yellow
        block.isLowConfidence -> CodiceColors.Lime
        else -> CodiceColors.Border
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .border(1.dp, accent, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.notes_block, index + 1), color = CodiceColors.TextLow, fontSize = 12.sp)
            Pill(
                text = stringResource(R.string.notes_confidence, (block.confidence * 100).roundToInt()),
                color = if (block.isLowConfidence) CodiceColors.Yellow else CodiceColors.Lime
            )
            if (block.needsReview) {
                Pill(text = stringResource(R.string.notes_to_review), color = CodiceColors.Yellow)
            } else if (block.isLowConfidence) {
                Pill(text = stringResource(R.string.notes_reviewed), color = CodiceColors.Lime, icon = Icons.Default.Check)
            }
        }

        OutlinedTextField(
            value = block.text,
            onValueChange = onTextChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (block.needsReview) CodiceColors.Yellow else CodiceColors.Lime,
                unfocusedBorderColor = CodiceColors.Border,
                focusedTextColor = CodiceColors.TextHigh,
                unfocusedTextColor = CodiceColors.TextHigh
            )
        )

        if (block.needsReview) {
            TextButton(onClick = onConfirm, enabled = enabled) {
                Text(stringResource(R.string.notes_looks_good_confirm), color = CodiceColors.Yellow)
            }
        }
    }
}
