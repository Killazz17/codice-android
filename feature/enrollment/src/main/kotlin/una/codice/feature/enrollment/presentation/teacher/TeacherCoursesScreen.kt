package una.codice.feature.enrollment.presentation.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import una.codice.core.designsystem.component.EmptyState
import una.codice.core.designsystem.component.GlassCard
import una.codice.core.designsystem.component.Meter
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import una.codice.core.designsystem.motion.SkeletonCard
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.motion.staggeredEntrance
import una.codice.core.designsystem.theme.LocalAccent
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.domain.model.TeacherGroupSummary
import androidx.hilt.navigation.compose.hiltViewModel
import una.codice.feature.enrollment.R
import androidx.compose.ui.res.stringResource
import una.codice.core.designsystem.component.describedAs
import androidx.compose.ui.res.pluralStringResource

@Composable
fun TeacherCoursesRoute(
    onNavigateToGroup: (String) -> Unit,
    onNavigateToCreateCourse: () -> Unit,
    onNavigateToRequests: () -> Unit
) {
    val viewModel: TeacherCoursesViewModel = hiltViewModel()
    // Al agregar un grupo se abre su invitación: ahí está el código para repartir.
    LaunchedEffect(viewModel) { viewModel.groupCreated.collect(onNavigateToGroup) }
    TeacherCoursesScreen(
        viewModel = viewModel,
        onNavigateToGroup = onNavigateToGroup,
        onNavigateToCreateCourse = onNavigateToCreateCourse,
        onNavigateToRequests = onNavigateToRequests
    )
}

@Composable
fun TeacherCoursesScreen(
    viewModel: TeacherCoursesViewModel,
    onNavigateToGroup: (String) -> Unit,
    onNavigateToCreateCourse: () -> Unit,
    onNavigateToRequests: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        // Transparente: deja ver el fondo aurora que pone CodiceScaffold detrás de todas las pantallas.
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (!uiState.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToCreateCourse,
                    containerColor = LocalAccent.current.primary,
                    contentColor = CodiceColors.Canvas,
                    shape = RoundedCornerShape(16.dp),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.enr_new_course), fontWeight = FontWeight.Bold) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                // Esqueletos con brillo en lugar de una rueda: muestran la forma de lo que va a llegar.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .describedAs(stringResource(R.string.enr_loading_courses), mergeDescendants = true),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SkeletonCard(lines = 3)
                    SkeletonCard(lines = 3)
                    SkeletonCard(lines = 2)
                }
            } else if (uiState.groups.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.enr_you_don_t_teach_any_courses_yet),
                    description = stringResource(R.string.enr_create_your_first_course_and_generate_),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                TeacherCoursesContent(
                    uiState = uiState,
                    onGroupClick = onNavigateToGroup,
                    onReviewRequestsClick = onNavigateToRequests,
                    onAddGroup = viewModel::onAddGroupClick,
                    onDeleteCourse = viewModel::onDeleteCourseClick
                )
            }
        }
    }

    when (val dialog = uiState.dialog) {
        is CourseDialog.AddGroup -> AddGroupDialog(
            dialog = dialog,
            onGroupNumberChange = viewModel::onGroupNumberChange,
            onPeriodChange = viewModel::onPeriodChange,
            onRequiresApprovalChange = viewModel::onRequiresApprovalChange,
            onConfirm = viewModel::onConfirmAddGroup,
            onDismiss = viewModel::onDismissDialog
        )
        is CourseDialog.ConfirmDelete -> DeleteCourseDialog(
            dialog = dialog,
            onConfirm = viewModel::onConfirmDeleteCourse,
            onDismiss = viewModel::onDismissDialog
        )
        null -> Unit
    }
}

@Composable
private fun TeacherCoursesContent(
    uiState: TeacherCoursesUiState,
    onGroupClick: (String) -> Unit,
    onReviewRequestsClick: () -> Unit,
    onAddGroup: (String) -> Unit,
    onDeleteCourse: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeaderSection(
                teacherName = uiState.teacherName,
                activeCoursesCount = uiState.activeCoursesCount,
                groupsCount = uiState.groups.size
            )
        }

        if (uiState.totalPendingRequests > 0) {
            item {
                PendingRequestsAlert(
                    count = uiState.totalPendingRequests,
                    onClick = onReviewRequestsClick
                )
            }
        }

        itemsIndexed(uiState.groups) { index, summary ->
            Box(modifier = Modifier.staggeredEntrance(index)) {
                GroupCard(
                    summary = summary,
                    onClick = { onGroupClick(summary.group.id) },
                    onAddGroup = { onAddGroup(summary.group.courseId) },
                    onDeleteCourse = { onDeleteCourse(summary.group.courseId) }
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(80.dp)) // Espacio para el FAB extendido
        }
    }
}

@Composable
private fun HeaderSection(teacherName: String, activeCoursesCount: Int, groupsCount: Int) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            GradientText(text = stringResource(R.string.enr_my_courses_2), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = accent.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.enr_teacher_2),
                        color = accent.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        R.string.enr_courses_and_groups,
                        pluralStringResource(R.plurals.enr_courses_count, activeCoursesCount, activeCoursesCount),
                        pluralStringResource(R.plurals.enr_groups_count, groupsCount, groupsCount)
                    ),
                    color = CodiceColors.TextLow,
                    fontSize = 13.sp
                )
            }
        }

        // Avatar placeholder
        val initials = teacherName.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase()
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accent.brush(), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials.ifEmpty { "P" },
                color = CodiceColors.Canvas,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun PendingRequestsAlert(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CodiceColors.Yellow.copy(alpha = 0.05f))
            .border(1.dp, CodiceColors.Yellow.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(CodiceColors.Yellow, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = pluralStringResource(R.plurals.enr_waiting_approval, count, count),
                color = CodiceColors.TextHigh,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.enr_review_action),
                color = CodiceColors.Yellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GroupCard(
    summary: TeacherGroupSummary,
    onClick: () -> Unit,
    onAddGroup: () -> Unit,
    onDeleteCourse: () -> Unit
) {
    val accentColor = when (summary.group.courseId) {
        "EIF411" -> CodiceColors.Lime
        "EIF303" -> CodiceColors.Blue
        else -> LocalAccent.current.primary
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // weight(1f): con un nombre largo ("Estructuras de Datos (demo del equipo)") esta fila se quedaba con todo
                // el ancho y empujaba el ⋮ fuera de la tarjeta.
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(24.dp)
                            .background(accentColor, RoundedCornerShape(100.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = summary.group.courseName,
                            color = CodiceColors.TextHigh,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(
                                R.string.enr_group_line,
                                summary.group.courseCode.ifBlank { summary.group.courseId },
                                summary.group.groupNumber,
                                summary.group.period
                            ),
                            color = CodiceColors.TextLow,
                            fontSize = 12.sp
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (summary.pendingRequestsCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(CodiceColors.Yellow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = summary.pendingRequestsCount.toString(),
                                color = CodiceColors.Canvas,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    CourseMenu(onAddGroup = onAddGroup, onDeleteCourse = onDeleteCourse)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Meter(
                progress = summary.averageCoverage,
                label = stringResource(R.string.enr_group_coverage),
                color = accentColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(text = stringResource(R.string.enr_group_n, summary.group.groupNumber))
                InfoPill(text = pluralStringResource(R.plurals.enr_students_count, summary.activeStudentCount, summary.activeStudentCount))
            }
        }
    }
}

/** El ⋮ de cada tarjeta: las dos cosas que se hacen sobre el curso entero y no sobre un grupo. */
@Composable
private fun CourseMenu(onAddGroup: () -> Unit, onDeleteCourse: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.enr_course_options),
                tint = CodiceColors.TextLow
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(CodiceColors.Glass)
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.enr_add_group_action), color = CodiceColors.TextHigh) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = LocalAccent.current.primary) },
                onClick = {
                    expanded = false
                    onAddGroup()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.enr_delete_course_action), color = CodiceColors.Coral) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = CodiceColors.Coral) },
                onClick = {
                    expanded = false
                    onDeleteCourse()
                }
            )
        }
    }
}

/** Agregar un grupo a un curso que ya existe: el número y el periodo llegan sugeridos, solo hay que confirmar. */
@Composable
private fun AddGroupDialog(
    dialog: CourseDialog.AddGroup,
    onGroupNumberChange: (String) -> Unit,
    onPeriodChange: (String) -> Unit,
    onRequiresApprovalChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CodiceColors.Glass,
        title = {
            Text(stringResource(R.string.enr_add_group_title, dialog.courseCode), color = CodiceColors.TextHigh)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(dialog.courseName, color = CodiceColors.TextMid, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DialogField(
                        label = stringResource(R.string.enr_group_3),
                        value = dialog.groupNumber,
                        onValueChange = onGroupNumberChange,
                        keyboardType = KeyboardType.Number,
                        enabled = !dialog.isLoading,
                        modifier = Modifier.weight(1f)
                    )
                    DialogField(
                        label = stringResource(R.string.enr_term),
                        value = dialog.period,
                        onValueChange = onPeriodChange,
                        enabled = !dialog.isLoading,
                        modifier = Modifier.weight(2f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.enr_approve_each_request),
                        color = CodiceColors.TextHigh,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = dialog.requiresApproval,
                        onCheckedChange = onRequiresApprovalChange,
                        enabled = !dialog.isLoading,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CodiceColors.Canvas,
                            checkedTrackColor = LocalAccent.current.primary
                        )
                    )
                }
                Text(stringResource(R.string.enr_add_group_hint), color = CodiceColors.TextLow, fontSize = 12.sp)
                dialog.error?.let { Text(it, color = CodiceColors.Orange, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !dialog.isLoading) {
                Text(stringResource(R.string.enr_add_group_confirm), color = LocalAccent.current.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.isLoading) {
                Text(stringResource(R.string.enr_cancel), color = CodiceColors.TextLow)
            }
        }
    )
}

/** Eliminar un curso: confirma, o explica por qué no se puede mientras tenga estudiantes matriculados. */
@Composable
private fun DeleteCourseDialog(
    dialog: CourseDialog.ConfirmDelete,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val blocked = dialog.activeStudents > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CodiceColors.Glass,
        title = {
            Text(stringResource(R.string.enr_delete_course_title, dialog.courseCode), color = CodiceColors.TextHigh)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (blocked) {
                        stringResource(R.string.enr_delete_course_blocked, dialog.activeStudents)
                    } else {
                        stringResource(R.string.enr_delete_course_message, dialog.courseName)
                    },
                    color = CodiceColors.TextMid
                )
                dialog.error?.let { Text(it, color = CodiceColors.Orange, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            if (blocked) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.enr_close), color = CodiceColors.TextLow)
                }
            } else {
                TextButton(onClick = onConfirm, enabled = !dialog.isLoading) {
                    Text(stringResource(R.string.enr_delete_course_confirm), color = CodiceColors.Coral)
                }
            }
        },
        dismissButton = if (blocked) {
            null
        } else {
            {
                TextButton(onClick = onDismiss, enabled = !dialog.isLoading) {
                    Text(stringResource(R.string.enr_cancel), color = CodiceColors.TextLow)
                }
            }
        }
    )
}

@Composable
private fun DialogField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val accent = LocalAccent.current
    Column(modifier = modifier) {
        Text(label, color = CodiceColors.TextMid, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CodiceColors.TextHigh,
                unfocusedTextColor = CodiceColors.TextHigh,
                focusedBorderColor = accent.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                cursorColor = accent.primary
            )
        )
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(
        color = CodiceColors.GlassHigh.copy(alpha = 0.5f),
        shape = RoundedCornerShape(100.dp)
    ) {
        Text(
            text = text,
            color = CodiceColors.TextMid,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
