package una.codice.feature.enrollment.presentation.teacher

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import una.codice.core.designsystem.component.CtaButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.component.glassPanel
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.designsystem.theme.LocalAccent
import una.codice.core.domain.model.TeacherGroupSummary
import una.codice.feature.enrollment.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.horizontalScroll

@Composable
fun TeacherInviteRoute(
    onBack: () -> Unit
) {
    val viewModel: TeacherInviteViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    // Subir brillo al máximo para proyectar
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val layoutParams = activity?.window?.attributes
        val originalBrightness = layoutParams?.screenBrightness ?: -1f
        
        layoutParams?.screenBrightness = 1.0f
        activity?.window?.attributes = layoutParams
        
        onDispose {
            layoutParams?.screenBrightness = originalBrightness
            activity?.window?.attributes = layoutParams
        }
    }

    TeacherInviteScreen(
        uiState = uiState,
        onBack = onBack,
        onGroupSelected = viewModel::onGroupSelected,
        onRegenerateClick = viewModel::onRegenerateClick,
        onConfirmRegenerate = viewModel::onConfirmRegenerate,
        onDismissRegenerate = viewModel::onDismissRegenerate,
        onToggleApproval = viewModel::onToggleApproval
    )
}

@Composable
fun TeacherInviteScreen(
    uiState: TeacherInviteUiState,
    onBack: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onRegenerateClick: () -> Unit,
    onConfirmRegenerate: () -> Unit,
    onDismissRegenerate: () -> Unit,
    onToggleApproval: (Boolean) -> Unit
) {
    val context = LocalContext.current
    // El onClick del botón no es @Composable: el texto a compartir se resuelve antes.
    val shareText = stringResource(R.string.enr_join_my_course_on_codice_use_this_code, uiState.inviteInfo?.inviteCode.orEmpty())

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            InviteTopBar(
                courseId = uiState.selectedGroup?.group?.let { it.courseCode.ifBlank { it.courseId } } ?: "",
                onBack = onBack
            )
        },
        bottomBar = {
            CtaButton(
                text = stringResource(R.string.enr_share_invitation_link),
                onClick = {
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                },
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                // Con el QR, el código y el interruptor no cabe todo en pantallas chicas: se desplaza, y así el
                // interruptor de aprobación no queda tapado por el botón de compartir.
                .verticalScroll(rememberScrollState())
        ) {
            GroupTabs(
                groups = uiState.groups,
                selectedGroupId = uiState.selectedGroupId,
                onGroupSelected = onGroupSelected
            )
            
            Spacer(Modifier.height(24.dp))
            
            QrSection(code = uiState.inviteInfo?.inviteCode ?: "")
            
            Spacer(Modifier.height(16.dp))
            
            CodeSection(
                code = uiState.inviteInfo?.inviteCode ?: "",
                expiration = uiState.inviteInfo?.expiresAt ?: "",
                groupNumber = uiState.selectedGroup?.group?.groupNumber ?: "",
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Codice Invite", uiState.inviteInfo?.inviteCode)
                    clipboard.setPrimaryClip(clip)
                },
                onRegenerate = onRegenerateClick
            )
            
            Spacer(Modifier.height(16.dp))
            
            ApprovalSection(
                checked = uiState.inviteInfo?.requiresApproval ?: true,
                onCheckedChange = onToggleApproval
            )
        }
    }

    if (uiState.showRegenerateConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissRegenerate,
            title = { Text(stringResource(R.string.enr_regenerate_code)) },
            text = { Text(stringResource(R.string.enr_the_previous_code_will_stop_working_im)) },
            confirmButton = {
                TextButton(onClick = onConfirmRegenerate) {
                    Text(stringResource(R.string.enr_regenerate), color = CodiceColors.Orange)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRegenerate) {
                    Text(stringResource(R.string.enr_cancel))
                }
            },
            containerColor = CodiceColors.Deep,
            titleContentColor = CodiceColors.TextHigh,
            textContentColor = CodiceColors.TextMid
        )
    }
}

@Composable
private fun InviteTopBar(courseId: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .clip(CircleShape)
                .background(CodiceColors.GlassHigh)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.enr_back), tint = CodiceColors.TextHigh)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            GradientText(text = stringResource(R.string.enr_invite_to_the_course), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.enr_the_code_is_per_group, courseId),
                style = MaterialTheme.typography.bodySmall,
                color = CodiceColors.TextLow
            )
        }
    }
}

@Composable
private fun GroupTabs(
    groups: List<TeacherGroupSummary>,
    selectedGroupId: String,
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
private fun QrSection(code: String) {
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .glassPanel(24.dp)
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val bitmap = remember(code) { QrGenerator.generateQrCode(code, 800) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.enr_qr_code),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.enr_scan_to_join_the_course),
                color = CodiceColors.TextLow,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun CodeSection(
    code: String,
    expiration: String,
    groupNumber: String,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .glassPanel(24.dp)
            .padding(20.dp)
    ) {
        val accent = LocalAccent.current
        Text(
            text = stringResource(R.string.enr_or_enter_the_group_code, groupNumber),
            color = accent.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = code,
                color = CodiceColors.TextHigh,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            // Botón compacto con el degradado del rol (el CtaButton ocupa todo el ancho).
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.brush())
                    .clickable(onClick = onCopy)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("copiar", color = CodiceColors.Canvas, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Text(text = expiration, color = CodiceColors.TextLow, fontSize = 12.sp)
            Text(text = " • ", color = CodiceColors.TextLow, fontSize = 12.sp)
            Text(
                text = stringResource(R.string.enr_you_can_regenerate_it_whenever_you_wan),
                color = CodiceColors.TextLow,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onRegenerate)
            )
        }
    }
}

@Composable
private fun ApprovalSection(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .glassPanel(24.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CodiceColors.Canvas,
                    checkedTrackColor = LocalAccent.current.primary
                )
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.enr_require_my_approval),
                color = CodiceColors.TextHigh,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.enr_if_you_turn_it_off_anyone_with_the_cod),
            color = CodiceColors.TextLow,
            fontSize = 12.sp
        )
    }
}
