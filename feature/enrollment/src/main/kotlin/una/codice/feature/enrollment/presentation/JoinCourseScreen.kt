package una.codice.feature.enrollment.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import una.codice.core.designsystem.component.CodiceLoader
import una.codice.core.designsystem.component.CtaButton
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.component.glassPanel
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.feature.enrollment.presentation.components.EnrollmentItem
import una.codice.feature.enrollment.presentation.components.QrScannerOverlay
import una.codice.feature.enrollment.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.font.FontFamily
import una.codice.core.designsystem.theme.LocalAccent
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import una.codice.core.domain.model.Enrollment
import una.codice.core.domain.model.EnrollmentStatus

/**
 * La pestaña "Cursos": todo lo de los cursos del estudiante en un solo lugar. Arriba están sus cursos (tocar uno abre su
 * mapa de cobertura) y abajo, unirse a uno nuevo con el código del profesor o con su QR. Antes "Unirme" estaba repartido
 * por Inicio, Apuntes y Perfil.
 */
@Composable
fun JoinCourseScreen(
    viewModel: JoinCourseViewModel,
    onOpenCourse: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val myEnrollmentsState by viewModel.myEnrollments.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // El curso del que se pidió salir, mientras se espera la confirmación.
    var leaving by remember { mutableStateOf<Enrollment?>(null) }

    // Si todavía no tiene cursos, el formulario para unirse se muestra siempre; si ya tiene, se abre con el botón.
    var joinOpen by rememberSaveable { mutableStateOf(false) }
    val loaded = myEnrollmentsState is UiState.Success
    val noCourses = (myEnrollmentsState as? UiState.Success)?.data?.isEmpty() == true
    val showJoin = joinOpen || noCourses

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is JoinEvent.Success -> {
                    joinOpen = false
                    // El texto se arma aquí y no en el ViewModel para que salga en el idioma elegido.
                    snackbar.showSnackbar(
                        if (event.professorName.isBlank()) context.getString(R.string.enr_request_sent_generic)
                        else context.getString(R.string.enr_request_sent, event.professorName)
                    )
                }
                is JoinEvent.Left -> snackbar.showSnackbar(context.getString(R.string.enr_left_course))
                is JoinEvent.ShowMessage -> snackbar.showSnackbar(event.message)
            }
        }
    }

    // Sin fondo propio: el fondo aurora de CodiceScaffold se ve detrás, como en el resto de la app.
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    GradientText(text = stringResource(R.string.enr_my_courses_2), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.enr_courses_subtitle),
                        color = CodiceColors.TextMid,
                        fontSize = 14.sp
                    )
                }
            }

            when (val enrollments = myEnrollmentsState) {
                is UiState.Loading -> item { CodiceLoader() }
                is UiState.Success -> {
                    if (enrollments.data.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.enr_you_haven_t_joined_any_course_yet),
                                color = CodiceColors.TextMid,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        items(enrollments.data, key = { it.id }) { enrollment ->
                            EnrollmentItem(
                                enrollment = enrollment,
                                onClick = {
                                    viewModel.onOpenCourse(enrollment.group.id)
                                    onOpenCourse()
                                },
                                onLeave = { leaving = enrollment }
                            )
                        }
                    }
                }
                is UiState.Error -> item { Text(enrollments.message, color = CodiceColors.Coral) }
            }

            if (loaded && !noCourses) {
                item { JoinToggle(open = joinOpen, onClick = { joinOpen = !joinOpen }) }
            }

            if (showJoin) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = stringResource(R.string.enr_ask_your_teacher_for_your_group_s_code),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CodiceColors.TextMid
                        )
                        CodeInputSection(
                            code = state.code,
                            isLoading = state.isLoading,
                            errorMessage = state.errorMessage,
                            canSubmit = state.code.replace("-", "").length in MIN_CODE_CHARS..MAX_CODE_CHARS,
                            onCodeChange = viewModel::onCodeChange,
                            onResolve = viewModel::onResolve
                        )
                        CustomDivider()
                        ScanCard(onClick = viewModel::onQrClick)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    leaving?.let { enrollment ->
        val course = enrollment.group.courseCode.ifBlank { enrollment.group.courseName }
        val isPending = enrollment.status == EnrollmentStatus.PENDING
        AlertDialog(
            onDismissRequest = { leaving = null },
            containerColor = CodiceColors.Glass,
            title = {
                Text(
                    stringResource(if (isPending) R.string.enr_cancel_request_title else R.string.enr_leave_title, course),
                    color = CodiceColors.TextHigh
                )
            },
            text = {
                Text(
                    stringResource(if (isPending) R.string.enr_cancel_request_message else R.string.enr_leave_message),
                    color = CodiceColors.TextMid
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onLeave(enrollment.id)
                    leaving = null
                }) {
                    Text(
                        stringResource(if (isPending) R.string.enr_cancel_request else R.string.enr_leave_confirm),
                        color = CodiceColors.Coral
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { leaving = null }) {
                    Text(stringResource(R.string.enr_stay), color = CodiceColors.TextLow)
                }
            }
        )
    }

    if (state.groupFound != null) {
        ConfirmationDialog(
            group = state.groupFound!!,
            onConfirm = viewModel::onConfirmJoin,
            onDismiss = viewModel::onDismissDialog
        )
    }

    if (state.isScannerOpen) {
        QrScannerOverlay(
            onQrDetected = viewModel::onQrDetected,
            onDismiss = viewModel::onDismissScanner
        )
    }
}

/** El botón que abre (o cierra) el formulario para unirse a otro curso: con texto y un "+", no un ícono suelto. */
@Composable
private fun JoinToggle(open: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(20.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = accent.primary, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.enr_join_toggle_title),
                style = MaterialTheme.typography.titleSmall,
                color = CodiceColors.TextHigh,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.enr_join_toggle_hint),
                style = MaterialTheme.typography.bodySmall,
                color = CodiceColors.TextMid
            )
        }

        Icon(
            imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = CodiceColors.TextLow,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Entrada del código del grupo: UN campo, con un ejemplo a la vista. Antes eran 12 casillas rígidas que asumían un
 * código de curso de exactamente 6 caracteres; pero el servidor acepta de 5 a 10, y un curso con otro largo generaba un
 * código que nadie podía usar. Se acepta con o sin guiones: el ViewModel los acomoda al buscar.
 */
@Composable
fun CodeInputSection(
    code: String,
    isLoading: Boolean,
    errorMessage: String?,
    canSubmit: Boolean,
    onCodeChange: (String) -> Unit,
    onResolve: () -> Unit
) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(20.dp)
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.enr_group_code),
            color = CodiceColors.TextMid,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = errorMessage != null,
            placeholder = { Text(stringResource(R.string.enr_group_code_example), color = CodiceColors.TextLow) },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 18.sp, letterSpacing = 1.sp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onResolve() }),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CodiceColors.TextHigh,
                unfocusedTextColor = CodiceColors.TextHigh,
                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                focusedBorderColor = accent.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                errorBorderColor = CodiceColors.Coral,
                cursorColor = accent.primary,
            ),
        )
        Text(
            text = errorMessage ?: stringResource(R.string.enr_group_code_hint),
            color = if (errorMessage != null) CodiceColors.Coral else CodiceColors.TextLow,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        CtaButton(
            text = stringResource(R.string.enr_find_group),
            onClick = onResolve,
            enabled = canSubmit,
            isLoading = isLoading,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CustomDivider() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = CodiceColors.TextLow.copy(alpha = 0.2f),
            thickness = 1.dp
        )
        // Un círculo vacío no decía nada: ahora es la palabra "o".
        Text(
            text = stringResource(R.string.enr_or),
            color = CodiceColors.TextLow,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = CodiceColors.TextLow.copy(alpha = 0.2f),
            thickness = 1.dp
        )
    }
}

@Composable
fun ScanCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(20.dp)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF003320)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = CodiceColors.Lime,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.enr_scan_the_teacher_s_qr),
                style = MaterialTheme.typography.titleSmall,
                color = CodiceColors.TextHigh,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.enr_opens_the_camera),
                style = MaterialTheme.typography.bodySmall,
                color = CodiceColors.TextMid
            )
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = CodiceColors.TextLow,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Diálogo de confirmación que aparece cuando el código ingresado es válido.
 * Muestra los detalles del curso y profesor antes de enviar la solicitud definitiva.
 */
@Composable
fun ConfirmationDialog(
    group: una.codice.core.domain.model.StudentGroup,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CodiceColors.Glass,
        title = { Text(stringResource(R.string.enr_confirm_group), color = CodiceColors.TextHigh) },
        text = {
            Column {
                Text("${group.courseName} (${group.courseCode.ifBlank { group.courseId }})", color = CodiceColors.TextHigh)
                Text(stringResource(R.string.enr_group_2, group.groupNumber, group.period), color = CodiceColors.TextMid)
                Text(stringResource(R.string.enr_teacher, group.professorName), color = CodiceColors.TextMid)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.enr_join), color = CodiceColors.Lime)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.enr_cancel), color = CodiceColors.TextLow)
            }
        }
    )
}
