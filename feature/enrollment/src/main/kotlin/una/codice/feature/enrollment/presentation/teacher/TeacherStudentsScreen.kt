package una.codice.feature.enrollment.presentation.teacher

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import una.codice.core.designsystem.component.CtaButton
import una.codice.core.designsystem.component.GlassCard
import una.codice.core.designsystem.component.CodiceLoader
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.theme.LocalAccent
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.domain.model.*
import una.codice.feature.enrollment.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.res.pluralStringResource
import una.codice.core.designsystem.component.EmptyState
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun TeacherStudentsRoute(
    onNavigateToInvite: (String) -> Unit
) {
    val viewModel: TeacherStudentsViewModel = hiltViewModel()
    TeacherStudentsScreen(
        viewModel = viewModel,
        onNavigateToInvite = onNavigateToInvite
    )
}

@Composable
fun TeacherStudentsScreen(
    viewModel: TeacherStudentsViewModel,
    onNavigateToInvite: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // El estudiante que se va a quitar, mientras se espera la confirmación.
    var removing by remember { mutableStateOf<GroupStudent?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TeacherStudentsEvent.Removed ->
                    snackbar.showSnackbar(context.getString(R.string.enr_student_removed, event.studentName))
                is TeacherStudentsEvent.Failed -> snackbar.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            CtaButton(
                text = stringResource(R.string.enr_invite_students),
                onClick = { onNavigateToInvite(uiState.selectedGroupId ?: "") },
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                containerColor = CodiceColors.Purple
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CodiceLoader()
            }
        } else {
            TeacherStudentsContent(
                uiState = uiState,
                onGroupSelected = viewModel::onGroupSelected,
                onApprove = viewModel::onApproveClick,
                onReject = viewModel::onRejectClick,
                onRemove = { removing = it },
                modifier = Modifier.padding(padding)
            )
        }
    }

    removing?.let { student ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = CodiceColors.Glass,
            title = { Text(stringResource(R.string.enr_remove_title, student.user.fullName), color = CodiceColors.TextHigh) },
            text = { Text(stringResource(R.string.enr_remove_message), color = CodiceColors.TextMid) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onRemoveStudent(student)
                    removing = null
                }) { Text(stringResource(R.string.enr_remove), color = CodiceColors.Coral) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.enr_cancel), color = CodiceColors.TextLow) }
            }
        )
    }
}

@Composable
private fun TeacherStudentsContent(
    uiState: TeacherStudentsUiState,
    onGroupSelected: (String) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onRemove: (GroupStudent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            StudentsHeader(
                courseId = uiState.selectedGroup?.group?.let { it.courseCode.ifBlank { it.courseId } } ?: "",
                groupNumber = uiState.selectedGroup?.group?.groupNumber ?: "",
                studentCount = uiState.selectedGroup?.activeStudentCount ?: 0
            )
        }

        item {
            GroupTabs(
                groups = uiState.groups,
                selectedGroupId = uiState.selectedGroupId,
                onGroupSelected = onGroupSelected
            )
        }

        if (uiState.pendingRequests.isNotEmpty()) {
            item {
                PendingRequestsSection(
                    requests = uiState.pendingRequests,
                    onApprove = onApprove,
                    onReject = onReject
                )
            }
        }

        item {
            // Sin "exportar" ni "ordenados por cobertura": lo primero no hacía nada y lo segundo contradecía la promesa de
            // que la cobertura nunca se muestra por estudiante.
            SectionHeader(title = stringResource(R.string.enr_enrolled))
        }

        if (uiState.enrolledStudents.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.enr_no_students_yet),
                    description = stringResource(R.string.enr_no_students_hint),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp)
                )
            }
        }

        items(uiState.enrolledStudents) { student ->
            EnrolledStudentItem(student = student, onRemove = { onRemove(student) })
        }
    }
}

@Composable
private fun StudentsHeader(courseId: String, groupNumber: String, studentCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            GradientText(text = stringResource(R.string.enr_students_2), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                text = pluralStringResource(R.plurals.enr_group_enrolled_count, studentCount, courseId, groupNumber, studentCount),
                style = MaterialTheme.typography.bodySmall,
                color = CodiceColors.TextLow
            )
        }
    }
}

@Composable
private fun GroupTabs(
    groups: List<TeacherGroupSummary>,
    selectedGroupId: String?,
    onGroupSelected: (String) -> Unit
) {
    // Con varios grupos las pestañas ya no caben a lo ancho: se desplazan. Cada una dice el CURSO y el grupo, porque
    // dos cursos pueden tener un "Grupo 01".
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        groups.forEach { summary ->
            val isSelected = summary.group.id == selectedGroupId
            GroupTabItem(
                label = stringResource(
                    R.string.enr_group_chip,
                    summary.group.courseCode.ifBlank { summary.group.courseId },
                    summary.group.groupNumber
                ),
                count = summary.activeStudentCount,
                isSelected = isSelected,
                onClick = { onGroupSelected(summary.group.id) }
            )
        }
    }
}

@Composable
private fun GroupTabItem(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccent.current
    val backgroundColor = if (isSelected) accent.primary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f)
    val borderColor = if (isSelected) accent.primary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f)
    val textColor = if (isSelected) CodiceColors.TextHigh else CodiceColors.TextLow

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CodiceColors.GlassHigh)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = count.toString(), color = CodiceColors.TextLow, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun PendingRequestsSection(
    requests: List<GroupStudent>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .border(1.dp, CodiceColors.Yellow.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .background(CodiceColors.Yellow.copy(alpha = 0.03f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.enr_pending_requests),
                color = CodiceColors.Yellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(CodiceColors.Yellow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = requests.size.toString(), color = CodiceColors.Canvas, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        requests.forEach { student ->
            PendingStudentCard(student = student, onApprove = onApprove, onReject = onReject)
            if (student != requests.last()) Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PendingStudentCard(
    student: GroupStudent,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudentAvatar(name = student.user.fullName, color = CodiceColors.Yellow)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = student.user.fullName, color = CodiceColors.TextHigh, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(text = student.user.email, color = CodiceColors.TextLow, fontSize = 12.sp)
                }
                Text(text = student.requestDate ?: "", color = CodiceColors.TextLow, fontSize = 12.sp)
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onReject(student.enrollmentId) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CodiceColors.TextHigh),
                    border = BorderStroke(1.dp, CodiceColors.Border)
                ) {
                    Text(stringResource(R.string.enr_reject), fontWeight = FontWeight.Bold)
                }
                
                CtaButton(
                    text = stringResource(R.string.enr_approve),
                    onClick = { onApprove(student.enrollmentId) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EnrolledStudentItem(student: GroupStudent, onRemove: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val avatarColor = when (student.user.firstName.firstOrNull()) {
                'S' -> CodiceColors.Lime
                'M' -> CodiceColors.Blue
                'J' -> CodiceColors.Yellow
                'D' -> CodiceColors.Orange
                else -> CodiceColors.Purple
            }
            StudentAvatar(name = student.user.fullName, color = avatarColor)
            Spacer(Modifier.width(12.dp))
            Text(
                text = student.user.fullName,
                color = CodiceColors.TextHigh,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            // La cobertura que ve el profesor es agregada y anónima: sin dato por estudiante no se muestra.
            student.coverage?.let { coverage ->
                Text(
                    text = "${(coverage * 100).toInt()} %",
                    color = if (coverage < 0.3f) CodiceColors.Orange else CodiceColors.Lime,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            // Quitar al estudiante del grupo: pide confirmación y no borra sus apuntes.
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.PersonRemove,
                    contentDescription = stringResource(R.string.enr_remove_student, student.user.fullName),
                    tint = CodiceColors.TextLow
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null, trailingAction: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, color = CodiceColors.TextLow, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            if (subtitle != null) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(2.dp).background(CodiceColors.TextLow, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(text = subtitle, color = CodiceColors.TextLow, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        trailingAction()
    }
}

@Composable
private fun StudentAvatar(name: String, color: Color) {
    val initials = name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase()
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = initials, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TeacherStudentsPreview() {
    val mockGroup = StudentGroup("1", "EIF411", "Diseño", "01", "II Ciclo 2026", "Prof")
    val mockSummary = TeacherGroupSummary(mockGroup, 28, 0.75f, 2)
    val mockStudents = listOf(
        GroupStudent(User("1", "Ana", "Vargas Mora", "ana@est.ac.cr", UserRole.STUDENT), "e1", EnrollmentStatus.PENDING, 0f, "hace 2 h"),
        GroupStudent(User("3", "Sebastian", "Benavides", "seb@est.ac.cr", UserRole.STUDENT), "e3", EnrollmentStatus.ACTIVE, 0.78f)
    )
    
    val uiState = TeacherStudentsUiState(
        isLoading = false,
        groups = listOf(mockSummary, TeacherGroupSummary(mockGroup.copy(id="2", groupNumber="02"), 19, 0.6f, 0)),
        selectedGroupId = "1",
        students = mockStudents
    )
    
    Surface(color = CodiceColors.Canvas) {
        TeacherStudentsContent(
            uiState = uiState,
            onGroupSelected = {},
            onApprove = {},
            onReject = {},
            onRemove = {}
        )
    }
}
